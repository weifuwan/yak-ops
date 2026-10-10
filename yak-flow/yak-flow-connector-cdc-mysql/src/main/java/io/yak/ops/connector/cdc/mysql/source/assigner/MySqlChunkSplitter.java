package io.yak.ops.connector.cdc.mysql.source.assigner;

import io.yak.ops.connector.cdc.mysql.source.MySqlCdcSourceConfig;
import io.yak.ops.connector.cdc.mysql.source.split.MySqlSnapshotSplit;
import io.yak.ops.connector.jdbc.database.connection.JdbcConnectionProvider;
import io.yak.ops.connector.jdbc.database.dialect.JdbcDialect;
import io.yak.ops.connector.jdbc.database.dialect.JdbcDialects;
import io.yak.ops.core.data.TableId;
import io.yak.ops.core.types.LogicalTypeRoot;
import io.yak.ops.core.types.TableSchema;
import java.sql.Connection;
import java.sql.PreparedStatement;
import java.sql.ResultSet;
import java.sql.SQLException;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.Objects;

/**
 * Plans disjoint, half-open BIGINT-primary-key snapshot chunks using JDBC keyset boundaries.
 *
 * <p>Unlike numeric-span division, keyset boundaries accommodate sparse keys and avoid
 * scanning huge empty ranges. The total range is always open on both ends, so inserts
 * outside the initially observed key span remain covered by the post-snapshot Binlog replay.
 * Planning runs on the coordinator's background discovery thread.
 */
public final class MySqlChunkSplitter {

    private static final int MAX_CHUNKS_PER_TABLE = 10_000;

    private final JdbcConnectionProvider connections;
    private final JdbcDialect dialect;
    private final Map<TableId, TableSchema> schemas;
    private final int chunkSize;

    public MySqlChunkSplitter(MySqlCdcSourceConfig config, int chunkSize) {
        this(config::openSnapshotConnection, JdbcDialects.forUrl("jdbc:mysql:"), config.schemas(), chunkSize);
    }

    public MySqlChunkSplitter(
            JdbcConnectionProvider connections, JdbcDialect dialect, Map<TableId, TableSchema> schemas, int chunkSize) {
        this.connections = Objects.requireNonNull(connections, "connections");
        this.dialect = Objects.requireNonNull(dialect, "dialect");
        this.schemas = Map.copyOf(Objects.requireNonNull(schemas, "schemas"));
        if (chunkSize < 1 || chunkSize > 100_000) {
            throw new IllegalArgumentException("MySQL snapshot chunk size must be between 1 and 100000");
        }
        this.chunkSize = chunkSize;
    }

    /**
     * Divides the entire table keyspace into disjoint ranges without buffering table rows.
     *
     * <p>Supports exactly one non-null BIGINT primary key in the first hybrid implementation.
     * The cursor belongs to the Reader; the planner never makes a Checkpoint decision.
     *
     * @param table table already present in the frozen source schema
     * @param fingerprint immutable hybrid definition fingerprint
     * @param tableIndex index used to make stable split identifiers
     * @return ranges in ascending key order, including an empty-table range if necessary
     * @throws SQLException if JDBC key lookup fails
     */
    public List<MySqlSnapshotSplit> plan(TableId table, String fingerprint, int tableIndex) throws SQLException {
        TableSchema schema = Objects.requireNonNull(schemas.get(table), "table schema");
        String key = splitKey(schema);
        if (tableIndex < 0) {
            throw new IllegalArgumentException("Table index must be nonnegative");
        }
        String quoted = dialect.quoteIdentifier(key);
        String qualified = dialect.qualifiedTable(table);
        String base = "SELECT " + quoted + " FROM " + qualified;
        List<MySqlSnapshotSplit> ranges = new ArrayList<>();
        try (Connection connection = connections.getConnection()) {
            Long lower = null;
            do {
                if (ranges.size() >= MAX_CHUNKS_PER_TABLE) {
                    throw new IllegalStateException("MySQL snapshot exceeds the maximum chunk count");
                }
                String query = base + (lower == null ? "" : " WHERE " + quoted + " >= ?") + " ORDER BY " + quoted
                        + " LIMIT 1 OFFSET ?";
                Long upper;
                try (PreparedStatement statement = connection.prepareStatement(query)) {
                    int parameter = 1;
                    if (lower != null) {
                        statement.setLong(parameter++, lower);
                    }
                    statement.setInt(parameter, chunkSize);
                    try (ResultSet records = statement.executeQuery()) {
                        upper = records.next() ? records.getLong(1) : null;
                        if (upper != null && records.wasNull()) {
                            throw new SQLException("MySQL chunk key contains null");
                        }
                    }
                }
                if (upper != null && lower != null && upper <= lower) {
                    throw new SQLException("MySQL snapshot chunk boundary is not increasing");
                }
                ranges.add(new MySqlSnapshotSplit(
                        "mysql-snapshot-" + tableIndex + "-" + ranges.size(), fingerprint, table, lower, upper, null));
                lower = upper;
            } while (lower != null);
        }
        return List.copyOf(ranges);
    }

    public static String splitKey(TableSchema schema) {
        Objects.requireNonNull(schema, "schema");
        if (schema.primaryKeys().size() != 1) {
            throw new IllegalArgumentException("Initial MySQL hybrid snapshot requires one primary key");
        }
        String key = schema.primaryKeys().getFirst();
        var column = schema.columns().stream()
                .filter(candidate -> candidate.name().equals(key))
                .findFirst()
                .orElseThrow();
        if (column.dataType().getTypeRoot() != LogicalTypeRoot.BIGINT || column.nullable()) {
            throw new IllegalArgumentException("Initial MySQL hybrid snapshot requires a non-null BIGINT key");
        }
        return key;
    }
}
