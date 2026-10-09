package io.yak.ops.connector.jdbc.source.enumerator;

import io.yak.ops.connector.jdbc.JdbcConnectionOptions;
import io.yak.ops.connector.jdbc.JdbcSourceOptions;
import io.yak.ops.connector.jdbc.database.dialect.JdbcDialect;
import io.yak.ops.connector.jdbc.source.split.JdbcSourceSplit;
import io.yak.ops.core.configuration.Configuration;
import io.yak.ops.core.data.TableId;
import java.math.BigInteger;
import java.sql.Connection;
import java.sql.DatabaseMetaData;
import java.sql.PreparedStatement;
import java.sql.ResultSet;
import java.sql.SQLException;
import java.sql.Types;
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Objects;

/**
 * Turns one catalog table into disjoint bounded JDBC splits using a single numeric primary key.
 *
 * <p>Tables without a supported key are read as one replayable split. They must not checkpoint
 * a numeric cursor, because an unordered ResultSet offset would lose records after changes.
 */
public final class JdbcSplitPlanner {

    private final JdbcConnectionOptions connectionOptions;
    private final JdbcDialect dialect;
    private final int targetRowsPerSplit;
    private final int maxSplitsPerTable;
    private final int queryTimeoutSeconds;

    public JdbcSplitPlanner(JdbcConnectionOptions connectionOptions, JdbcDialect dialect, Configuration configuration) {
        this.connectionOptions = Objects.requireNonNull(connectionOptions, "connectionOptions");
        this.dialect = Objects.requireNonNull(dialect, "dialect");
        Objects.requireNonNull(configuration, "configuration");
        targetRowsPerSplit = configuration.get(JdbcSourceOptions.TARGET_ROWS_PER_SPLIT);
        maxSplitsPerTable = configuration.get(JdbcSourceOptions.MAX_SPLITS_PER_TABLE);
        queryTimeoutSeconds = configuration.get(JdbcSourceOptions.QUERY_TIMEOUT_SECONDS);
        if (targetRowsPerSplit <= 0 || maxSplitsPerTable <= 0 || queryTimeoutSeconds <= 0) {
            throw new IllegalArgumentException("Invalid JDBC split planning settings");
        }
    }

    /** Plans a table on a discovery thread; no coordinator or reader state is accessed here. */
    public List<JdbcSourceSplit> plan(TableId table, int tableIndex) throws SQLException {
        Objects.requireNonNull(table, "table");
        if (tableIndex < 0) {
            throw new IllegalArgumentException("Table index must be nonnegative");
        }
        try (Connection connection = connectionOptions.openConnection()) {
            DatabaseMetaData metadata = connection.getMetaData();
            Map<String, Integer> columns = discoverColumns(metadata, table);
            if (columns.isEmpty()) {
                throw new SQLException("The JDBC table has no discoverable columns");
            }
            List<String> selectedColumns = List.copyOf(columns.keySet());
            String splitColumn = numericPrimaryKey(metadata, table, columns);
            if (splitColumn == null) {
                return List.of(fullScan(tableIndex, table, selectedColumns, null));
            }

            String column = dialect.quoteIdentifier(splitColumn);
            String sql =
                    "SELECT MIN(" + column + "), MAX(" + column + "), COUNT(*) FROM " + dialect.qualifiedTable(table);
            try (PreparedStatement statement = connection.prepareStatement(sql)) {
                statement.setQueryTimeout(queryTimeoutSeconds);
                try (ResultSet values = statement.executeQuery()) {
                    if (!values.next()) {
                        throw new SQLException("Failed to read JDBC split statistics");
                    }
                    long rows = values.getLong(3);
                    if (rows <= 0) {
                        return List.of(fullScan(tableIndex, table, selectedColumns, splitColumn));
                    }

                    final long minimum;
                    final long maximum;
                    try {
                        minimum = values.getBigDecimal(1).longValueExact();
                        maximum = values.getBigDecimal(2).longValueExact();
                    } catch (ArithmeticException | NullPointerException outOfRange) {
                        // Unsigned BIGINT values outside signed-long range cannot be checkpointed
                        // through the supported numeric cursor. Replay the complete table instead.
                        return List.of(fullScan(tableIndex, table, selectedColumns, null));
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
                                splitId(tableIndex, index), table, selectedColumns, splitColumn, start, end, null));
                    }
                    return List.copyOf(result);
                }
            }
        }
    }

    private Map<String, Integer> discoverColumns(DatabaseMetaData metadata, TableId table) throws SQLException {
        Map<String, Integer> columns = new LinkedHashMap<>();
        try (ResultSet result = metadata.getColumns(table.catalog(), table.schema(), table.table(), null)) {
            while (result.next()) {
                if (table.table().equals(result.getString("TABLE_NAME"))) {
                    columns.put(result.getString("COLUMN_NAME"), result.getInt("DATA_TYPE"));
                }
            }
        }
        return columns;
    }

    private String numericPrimaryKey(DatabaseMetaData metadata, TableId table, Map<String, Integer> columns)
            throws SQLException {
        String key = null;
        try (ResultSet primaryKeys = metadata.getPrimaryKeys(table.catalog(), table.schema(), table.table())) {
            while (primaryKeys.next()) {
                if (key != null) {
                    return null;
                }
                key = primaryKeys.getString("COLUMN_NAME");
            }
        }
        if (key == null || !columns.containsKey(key)) {
            return null;
        }
        int type = columns.get(key);
        return type == Types.TINYINT || type == Types.SMALLINT || type == Types.INTEGER || type == Types.BIGINT
                ? key
                : null;
    }

    private JdbcSourceSplit fullScan(int index, TableId table, List<String> columns, String key) {
        return new JdbcSourceSplit(splitId(index, 0), table, columns, key, null, null, null);
    }

    private String splitId(int tableIndex, int splitIndex) {
        return "table-" + tableIndex + "-split-" + splitIndex;
    }
}
