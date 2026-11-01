package io.yak.ops.connector.jdbc.source.enumerator;

import io.yak.ops.configuration.Configuration;
import io.yak.ops.connector.jdbc.JdbcConnectionOptions;
import io.yak.ops.connector.jdbc.JdbcSourceOptions;
import io.yak.ops.connector.jdbc.database.catalog.JdbcTableMetadata;
import io.yak.ops.connector.jdbc.database.connection.DefaultJdbcConnectionProvider;
import io.yak.ops.connector.jdbc.database.connection.JdbcConnectionProvider;
import io.yak.ops.connector.jdbc.database.connection.JdbcConnectionRetry;
import io.yak.ops.connector.jdbc.database.dialect.JdbcDialect;
import io.yak.ops.connector.jdbc.database.query.JdbcReadQuery;
import io.yak.ops.connector.jdbc.source.JdbcIncrementalRange;
import io.yak.ops.connector.jdbc.source.JdbcReadOptions;
import io.yak.ops.connector.jdbc.source.split.JdbcSchemaFingerprint;
import io.yak.ops.connector.jdbc.source.split.JdbcSourceSplit;
import io.yak.ops.core.data.TableId;
import io.yak.ops.core.types.Column;
import io.yak.ops.core.types.DecimalType;
import io.yak.ops.core.types.LogicalTypeRoot;
import io.yak.ops.core.types.TableSchema;
import java.math.BigInteger;
import java.sql.Connection;
import java.sql.DatabaseMetaData;
import java.sql.PreparedStatement;
import java.sql.ResultSet;
import java.sql.SQLException;
import java.util.ArrayList;
import java.util.HashSet;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.Set;

/**
 * Plans bounded JDBC table scans as disjoint numeric-primary-key ranges when possible.
 *
 * <p>Planning performs blocking catalog/statistics queries on a discovery thread and produces
 * immutable split definitions. Tables without a supported single numeric primary key get one
 * full-table split; an unordered ResultSet offset must never be checkpointed as a key cursor.
 */
public final class JdbcSplitPlanner {

    private final JdbcConnectionProvider connections;
    private final JdbcDialect dialect;
    private final int targetRowsPerSplit;
    private final int maxSplitsPerTable;
    private final int queryTimeoutSeconds;
    private final int connectionAttempts;
    private final Map<TableId, List<String>> projections;
    private final Map<TableId, JdbcIncrementalRange> ranges;
    private final Map<TableId, JdbcReadOptions> reads;

    public JdbcSplitPlanner(JdbcConnectionOptions connectionOptions, JdbcDialect dialect, Configuration configuration) {
        this(new DefaultJdbcConnectionProvider(connectionOptions), dialect, configuration);
    }

    public JdbcSplitPlanner(JdbcConnectionProvider connections, JdbcDialect dialect, Configuration configuration) {
        this(connections, dialect, configuration, Map.of());
    }

    public JdbcSplitPlanner(
            JdbcConnectionProvider connections,
            JdbcDialect dialect,
            Configuration configuration,
            Map<TableId, List<String>> projections) {
        this(connections, dialect, configuration, projections, Map.of());
    }

    /** Plans frozen per-table incremental ranges alongside existing primary-key splits. */
    public JdbcSplitPlanner(
            JdbcConnectionProvider connections,
            JdbcDialect dialect,
            Configuration configuration,
            Map<TableId, List<String>> projections,
            Map<TableId, JdbcIncrementalRange> ranges) {
        this(connections, dialect, configuration, projections, ranges, Map.of());
    }

    public JdbcSplitPlanner(
            JdbcConnectionProvider connections,
            JdbcDialect dialect,
            Configuration configuration,
            Map<TableId, List<String>> projections,
            Map<TableId, JdbcIncrementalRange> ranges,
            Map<TableId, JdbcReadOptions> reads) {
        this.ranges = Map.copyOf(Objects.requireNonNull(ranges, "ranges"));
        this.reads = Map.copyOf(Objects.requireNonNull(reads, "reads"));
        this.connections = Objects.requireNonNull(connections, "connections");
        this.projections = Map.copyOf(Objects.requireNonNull(projections, "projections"));
        this.dialect = Objects.requireNonNull(dialect, "dialect");
        Objects.requireNonNull(configuration, "configuration");
        targetRowsPerSplit = configuration.get(JdbcSourceOptions.TARGET_ROWS_PER_SPLIT);
        maxSplitsPerTable = configuration.get(JdbcSourceOptions.MAX_SPLITS_PER_TABLE);
        queryTimeoutSeconds = configuration.get(JdbcSourceOptions.QUERY_TIMEOUT_SECONDS);
        connectionAttempts = configuration.get(JdbcSourceOptions.CONNECTION_ATTEMPTS);
        if (targetRowsPerSplit <= 0 || maxSplitsPerTable <= 0 || queryTimeoutSeconds <= 0 || connectionAttempts <= 0) {
            throw new IllegalArgumentException("Invalid JDBC split planning settings");
        }
    }

    /**
     * Resolves an ordered projection and freezes the schema fingerprint before planning splits.
     *
     * <p>The caller runs this operation on a discovery thread, not a task mailbox. Each split
     * contains inclusive bounds when a supported numeric key exists; otherwise it is replayed
     * as a complete table scan after recovery.
     *
     * @param table physical table to scan
     * @param tableIndex stable index in the source's ordered table definition
     * @return non-overlapping table splits with their schema fingerprint
     * @throws SQLException if JDBC metadata, statistics, or schema inspection fails
     */
    public List<JdbcSourceSplit> plan(TableId table, int tableIndex) throws SQLException {
        Objects.requireNonNull(table, "table");
        if (tableIndex < 0) {
            throw new IllegalArgumentException("Table index must be nonnegative");
        }
        try (Connection connection = JdbcConnectionRetry.open(connections, connectionAttempts)) {
            TableSchema schema = JdbcTableMetadata.readTable(connection, dialect, table);
            JdbcIncrementalRange range = ranges.get(table);
            if (range != null) {
                Column chosen = schema.columns().stream()
                        .filter(column -> column.name().equals(range.column()))
                        .findFirst()
                        .orElseThrow(() -> new SQLException("Incremental column is missing"));
                LogicalTypeRoot root = chosen.dataType().getTypeRoot();
                if (chosen.dataType().isNullable()) throw new SQLException("Incremental column must be NOT NULL");
                boolean valid = range.mode() == JdbcIncrementalRange.Mode.TIMESTAMP
                        ? root == io.yak.ops.core.types.LogicalTypeRoot.TIMESTAMP_WITHOUT_TIME_ZONE
                        : root == io.yak.ops.core.types.LogicalTypeRoot.BIGINT
                                || root == io.yak.ops.core.types.LogicalTypeRoot.INTEGER
                                || root == io.yak.ops.core.types.LogicalTypeRoot.SMALLINT
                                || root == io.yak.ops.core.types.LogicalTypeRoot.TINYINT;
                if (!valid) throw new SQLException("Unsupported JDBC incremental column type");
            }
            List<String> availableColumns =
                    schema.columns().stream().map(Column::name).toList();
            List<String> selectedColumns = selectedColumns(availableColumns, projections.get(table));
            JdbcReadOptions read = reads.get(table);
            String splitColumn = read != null && read.splitKey() != null
                    ? explicitSplitKey(connection, table, schema, read.splitKey())
                    : numericPrimaryKey(schema);
            String schemaFingerprint = schemaFingerprint(connection, table, selectedColumns, splitColumn);
            if (splitColumn == null) {
                return List.of(fullScan(tableIndex, table, selectedColumns, null, schemaFingerprint));
            }

            String sql = dialect.splitStatisticsSql(table, splitColumn)
                    + JdbcReadQuery.whereClause(read == null ? null : read.where(), List.of());
            try (PreparedStatement statement = connection.prepareStatement(sql)) {
                statement.setQueryTimeout(read == null ? queryTimeoutSeconds : read.queryTimeoutSeconds());
                try (ResultSet values = statement.executeQuery()) {
                    if (!values.next()) {
                        throw new SQLException("Failed to read JDBC split statistics");
                    }
                    long rows = values.getLong(3);
                    if (rows <= 0) {
                        return List.of(fullScan(tableIndex, table, selectedColumns, splitColumn, schemaFingerprint));
                    }

                    final long minimum;
                    final long maximum;
                    try {
                        minimum = values.getBigDecimal(1).longValueExact();
                        maximum = values.getBigDecimal(2).longValueExact();
                    } catch (ArithmeticException | NullPointerException outOfRange) {
                        if (read != null && read.splitKey() != null) {
                            throw new SQLException(
                                    "Configured JDBC split key exceeds its signed-long cursor range", outOfRange);
                        }
                        // Unsigned BIGINT values outside signed-long range cannot be checkpointed
                        // through the supported numeric cursor. Replay the complete table instead.
                        // The fallback no longer reads a hidden cursor column. Fingerprint
                        // its actual projection and absent cursor, not the rejected key plan.
                        return List.of(fullScan(
                                tableIndex,
                                table,
                                selectedColumns,
                                null,
                                schemaFingerprint(connection, table, selectedColumns, null)));
                    }

                    if (minimum > maximum) {
                        throw new SQLException("Invalid JDBC primary-key range");
                    }
                    BigInteger span = BigInteger.valueOf(maximum)
                            .subtract(BigInteger.valueOf(minimum))
                            .add(BigInteger.ONE);
                    long required = 1 + (rows - 1) / targetRowsPerSplit;
                    int splitCount = BigInteger.valueOf(Math.min(required, maxSplitsPerTable))
                            .min(span)
                            .intValueExact();

                    List<JdbcSourceSplit> result = new ArrayList<>(splitCount);
                    for (int index = 0; index < splitCount; index++) {
                        BigInteger lower = BigInteger.valueOf(minimum)
                                .add(span.multiply(BigInteger.valueOf(index)).divide(BigInteger.valueOf(splitCount)));
                        BigInteger exclusiveUpper = BigInteger.valueOf(minimum)
                                .add(span.multiply(BigInteger.valueOf(index + 1))
                                        .divide(BigInteger.valueOf(splitCount)));
                        long start = lower.longValueExact();
                        long end = exclusiveUpper.subtract(BigInteger.ONE).longValueExact();
                        result.add(new JdbcSourceSplit(
                                splitId(tableIndex, index),
                                table,
                                selectedColumns,
                                splitColumn,
                                start,
                                end,
                                null,
                                schemaFingerprint,
                                range,
                                read));
                    }
                    return List.copyOf(result);
                }
            }
        }
    }

    private List<String> selectedColumns(List<String> available, List<String> requested) throws SQLException {
        if (requested == null) {
            return available;
        }
        if (requested.isEmpty() || new HashSet<>(requested).size() != requested.size()) {
            throw new IllegalArgumentException("JDBC projected columns must be nonempty and unique");
        }
        for (String column : requested) {
            if (column == null || column.isBlank() || !available.contains(column)) {
                throw new SQLException("JDBC projected column does not exist: " + column);
            }
        }
        return List.copyOf(requested);
    }

    private String numericPrimaryKey(TableSchema schema) {
        if (schema.primaryKeys().size() != 1) {
            return null;
        }
        String key = schema.primaryKeys().getFirst();
        Column column = schema.columns().stream()
                .filter(candidate -> key.equals(candidate.name()))
                .findFirst()
                .orElse(null);
        if (column == null) {
            return null;
        }
        return switch (column.dataType().getTypeRoot()) {
            case TINYINT, SMALLINT, INTEGER, BIGINT -> key;
            case DECIMAL -> ((DecimalType) column.dataType()).scale() == 0 ? key : null;
            default -> null;
        };
    }

    private String explicitSplitKey(Connection connection, TableId table, TableSchema schema, String key)
            throws SQLException {
        Column column = schema.columns().stream()
                .filter(candidate -> candidate.name().equals(key))
                .findFirst()
                .orElseThrow(() -> new SQLException("Configured JDBC split key column does not exist"));
        boolean integral =
                switch (column.dataType().getTypeRoot()) {
                    case TINYINT, SMALLINT, INTEGER, BIGINT -> true;
                    case DECIMAL -> ((DecimalType) column.dataType()).scale() == 0;
                    default -> false;
                };
        if (!integral || column.nullable()) {
            throw new SQLException("Configured JDBC split key must be a non-null integral numeric column");
        }
        if (schema.primaryKeys().equals(List.of(key))) return key;
        Map<String, List<String>> indexes = new LinkedHashMap<>();
        Set<String> unsupported = new HashSet<>();
        try (ResultSet metadata = connection
                .getMetaData()
                .getIndexInfo(
                        table.catalog() == null ? connection.getCatalog() : table.catalog(),
                        table.schema() == null ? connection.getSchema() : table.schema(),
                        table.table(),
                        true,
                        false)) {
            while (metadata.next()) {
                if (metadata.getShort("TYPE") == DatabaseMetaData.tableIndexStatistic) continue;
                String name = metadata.getString("INDEX_NAME");
                if (name == null) continue;
                String selected = metadata.getString("COLUMN_NAME");
                String condition = metadata.getString("FILTER_CONDITION");
                if (metadata.getBoolean("NON_UNIQUE")
                        || selected == null
                        || (condition != null && !condition.isBlank())) unsupported.add(name);
                else indexes.computeIfAbsent(name, unused -> new ArrayList<>()).add(selected);
            }
        }
        boolean unique = indexes.entrySet().stream()
                .anyMatch(index -> !unsupported.contains(index.getKey())
                        && index.getValue().equals(List.of(key)));
        if (!unique) throw new SQLException("Configured JDBC split key requires an enforced single-column unique key");
        return key;
    }

    private JdbcSourceSplit fullScan(
            int index, TableId table, List<String> columns, String key, String schemaFingerprint) {
        return new JdbcSourceSplit(
                splitId(index, 0),
                table,
                columns,
                key,
                null,
                null,
                null,
                schemaFingerprint,
                ranges.get(table),
                reads.get(table));
    }

    /**
     * Captures the exact projected JDBC types without fetching any rows.
     *
     * <p>The same vendor converter is used by the running SplitReader, so a restored split
     * detects type, nullability or column-order changes before emitting any new records.
     */
    private String schemaFingerprint(Connection connection, TableId table, List<String> columns, String key)
            throws SQLException {
        JdbcReadOptions read = reads.get(table);
        List<String> readColumns = JdbcSourceSplit.readColumns(columns, key, read == null ? null : read.where());
        String projection =
                readColumns.stream().map(dialect::quoteIdentifier).collect(java.util.stream.Collectors.joining(", "));
        String sql = "SELECT " + projection + " FROM " + dialect.qualifiedTable(table) + " WHERE 1 = 0";
        try (PreparedStatement statement = connection.prepareStatement(sql)) {
            statement.setQueryTimeout(queryTimeoutSeconds);
            try (ResultSet results = statement.executeQuery()) {
                TableSchema schema = dialect.typeConverter().convert(results.getMetaData());
                if (schema.columnCount() != readColumns.size()) {
                    throw new SQLException("JDBC projected column count changed during split planning");
                }
                for (int index = 0; index < readColumns.size(); index++) {
                    if (!readColumns.get(index).equals(schema.column(index).name())) {
                        throw new SQLException("JDBC projected column order changed during split planning");
                    }
                }
                return JdbcSchemaFingerprint.of(table, schema, key);
            }
        }
    }

    private String splitId(int tableIndex, int splitIndex) {
        return "table-" + tableIndex + "-split-" + splitIndex;
    }
}
