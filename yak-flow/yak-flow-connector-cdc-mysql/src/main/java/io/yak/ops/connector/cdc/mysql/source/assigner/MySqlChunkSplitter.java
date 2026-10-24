package io.yak.ops.connector.cdc.mysql.source.assigner;

import io.yak.ops.connector.cdc.mysql.source.MySqlCdcSourceConfig;
import io.yak.ops.connector.cdc.mysql.source.split.MySqlSnapshotSplit;
import io.yak.ops.connector.cdc.mysql.source.utils.ChunkUtils;
import io.yak.ops.connector.cdc.mysql.source.utils.StatementUtils;
import io.yak.ops.connector.jdbc.database.connection.JdbcConnectionProvider;
import io.yak.ops.connector.jdbc.database.dialect.JdbcDialect;
import io.yak.ops.connector.jdbc.database.dialect.JdbcDialects;
import io.yak.ops.core.data.TableId;
import io.yak.ops.core.types.Column;
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
 * <p>Keyset boundaries accommodate sparse keys and avoid scanning huge empty ranges. The total range is always open on both ends, so inserts
 * outside the initially observed key span remain covered by the post-snapshot Binlog replay.
 * Planning runs on the coordinator's background discovery thread.
 */
public final class MySqlChunkSplitter {

    private static final int MAX_CHUNKS_PER_TABLE = 10_000;

    private final JdbcConnectionProvider connections;
    private final JdbcDialect dialect;
    private final Map<TableId, TableSchema> schemas;
    private final Map<TableId, String> chunkKeyColumns;
    private final int chunkSize;
    private volatile PreparedStatement planningStatement;
    private volatile boolean cancelled;

    public MySqlChunkSplitter(MySqlCdcSourceConfig config, int chunkSize) {
        this(
                config::openSnapshotConnection,
                JdbcDialects.forUrl("jdbc:mysql:"),
                config.schemas(),
                config.chunkKeyColumns(),
                chunkSize);
    }

    public MySqlChunkSplitter(
            JdbcConnectionProvider connections, JdbcDialect dialect, Map<TableId, TableSchema> schemas, int chunkSize) {
        this(connections, dialect, schemas, Map.of(), chunkSize);
    }

    public MySqlChunkSplitter(
            JdbcConnectionProvider connections,
            JdbcDialect dialect,
            Map<TableId, TableSchema> schemas,
            Map<TableId, String> chunkKeyColumns,
            int chunkSize) {
        this.connections = Objects.requireNonNull(connections, "connections");
        this.dialect = Objects.requireNonNull(dialect, "dialect");
        this.schemas = Map.copyOf(Objects.requireNonNull(schemas, "schemas"));
        this.chunkKeyColumns = Map.copyOf(Objects.requireNonNull(chunkKeyColumns, "chunkKeyColumns"));
        if (chunkSize < 1 || chunkSize > 100_000) {
            throw new IllegalArgumentException("MySQL snapshot chunk size must be between 1 and 100000");
        }
        this.chunkSize = chunkSize;
    }

    /**
     * Plans all snapshot ranges using database comparison semantics. This typed planner is
     * available independently of the legacy BIGINT split transport and is not used to
     * dispatch work until typed checkpoint, Backfill and Binlog filtering support arrives.
     */
    public List<ChunkRange> splitChunks(TableId table) throws SQLException {
        List<ChunkRange> ranges = new ArrayList<>();
        Object lower = null;
        while (true) {
            if (ranges.size() >= MAX_CHUNKS_PER_TABLE) {
                throw new IllegalStateException("MySQL snapshot exceeds the maximum chunk count");
            }
            ChunkRange range = nextChunk(table, lower);
            ranges.add(range);
            if (range.last()) return List.copyOf(ranges);
            lower = range.end();
        }
    }

    /**
     * Discovers one typed chunk from an optional database-ordered lower boundary.
     * Equal keys stay together and the next boundary is strictly greater in MySQL,
     * even when Java string comparisons disagree with the source collation.
     */
    public ChunkRange nextChunk(TableId table, Object lower) throws SQLException {
        if (cancelled) throw new SQLException("MySQL snapshot chunk discovery was cancelled");
        TableSchema schema = Objects.requireNonNull(schemas.get(table), "table schema");
        Column column = ChunkUtils.getChunkKeyColumn(schema, chunkKeyColumns.get(table));
        LogicalTypeRoot type = column.dataType().getTypeRoot();
        // Validate scalar family and caller-supplied resume boundary before opening JDBC.
        new ChunkRange(table, column.name(), type, lower, null);
        try (Connection connection = connections.getConnection()) {
            Object maximum =
                    StatementUtils.queryNextChunkMax(connection, dialect, table, column.name(), type, chunkSize, lower);
            Object upper = maximum == null
                    ? null
                    : StatementUtils.queryMin(connection, dialect, table, column.name(), type, maximum);
            return new ChunkRange(table, column.name(), type, lower, upper);
        }
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
        String key = splitKey(schema, chunkKeyColumns.get(table));
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

    /** Discovers exactly one next range; the caller checkpoints the returned upper boundary before continuing. */
    public MySqlSnapshotSplit planNext(TableId table, String fingerprint, int tableIndex, int chunkIndex, Long lower)
            throws SQLException {
        if (cancelled || tableIndex < 0 || chunkIndex < 0 || chunkIndex >= MAX_CHUNKS_PER_TABLE) {
            throw new SQLException("MySQL chunk discovery is cancelled or exceeds its range limit");
        }
        String key = splitKey(Objects.requireNonNull(schemas.get(table), "table schema"), chunkKeyColumns.get(table));
        String quoted = dialect.quoteIdentifier(key);
        String sql = "SELECT " + quoted + " FROM " + dialect.qualifiedTable(table)
                + (lower == null ? "" : " WHERE " + quoted + " >= ?")
                + " ORDER BY " + quoted + " LIMIT 1 OFFSET ?";
        try (Connection connection = connections.getConnection();
                PreparedStatement statement = connection.prepareStatement(sql)) {
            planningStatement = statement;
            statement.setQueryTimeout(60);
            if (cancelled) throw new SQLException("MySQL chunk discovery was cancelled");
            int parameter = 1;
            if (lower != null) statement.setLong(parameter++, lower);
            statement.setInt(parameter, chunkSize);
            Long upper;
            try (ResultSet rows = statement.executeQuery()) {
                upper = rows.next() ? rows.getBigDecimal(1).longValueExact() : null;
            } catch (ArithmeticException | NullPointerException invalidKey) {
                throw new SQLException(
                        "MySQL snapshot chunk key is outside the supported signed BIGINT range", invalidKey);
            }
            if (upper != null && lower != null && upper <= lower)
                throw new SQLException("MySQL chunk boundary did not advance");
            return new MySqlSnapshotSplit(
                    "mysql-snapshot-" + tableIndex + "-" + chunkIndex, fingerprint, table, lower, upper, null);
        } finally {
            planningStatement = null;
        }
    }

    /**
     * Plans one typed snapshot Split from the durable Enumerator discovery cursor.
     *
     * <p>The cursor identifies the next lower boundary, not a row identity or Reader
     * progress position. Non-unique key values stay within one half-open chunk.
     */
    public MySqlSnapshotSplit planNextTyped(
            TableId table, String fingerprint, int tableIndex, int chunkIndex, ChunkRange cursor) throws SQLException {
        if (cancelled || tableIndex < 0 || chunkIndex < 0 || chunkIndex >= MAX_CHUNKS_PER_TABLE) {
            throw new SQLException("MySQL typed chunk discovery is cancelled or exceeds its range limit");
        }
        TableSchema schema = Objects.requireNonNull(schemas.get(table), "table schema");
        Column column = ChunkUtils.getChunkKeyColumn(schema, chunkKeyColumns.get(table));
        if (cursor != null
                && (!cursor.tableId().equals(table)
                        || !cursor.column().equals(column.name())
                        || cursor.type() != column.dataType().getTypeRoot()
                        || cursor.start() == null
                        || cursor.end() != null)) {
            throw new IllegalArgumentException("Restored typed chunk cursor differs from the selected source column");
        }
        if ((chunkIndex == 0) != (cursor == null)) {
            throw new IllegalArgumentException("Typed chunk index and restored discovery cursor differ");
        }
        ChunkRange range = nextChunk(table, cursor == null ? null : cursor.start());
        return MySqlSnapshotSplit.typed("mysql-snapshot-" + tableIndex + "-" + chunkIndex, fingerprint, range);
    }

    /** Signals terminal discovery cancellation without blocking the coordinator. */
    public void cancel() {
        cancelled = true;
        PreparedStatement running = planningStatement;
        if (running != null)
            Thread.ofVirtual().name("yak-mysql-discovery-cancel").start(() -> {
                try {
                    running.cancel();
                } catch (SQLException ignored) {
                    /* Discovery owns statement cleanup and observes cancellation. */
                }
            });
    }

    public static String splitKey(TableSchema schema) {
        return splitKey(schema, null);
    }

    /**
     * The selection contract is independent of primary-key discovery, but the current Long-based
     * snapshot, backfill and checkpoint still require the selected column to be the sole primary key.
     */
    public static String splitKey(TableSchema schema, String configuredChunkKey) {
        var column = ChunkUtils.getChunkKeyColumn(schema, configuredChunkKey);
        if (schema.primaryKeys().size() != 1 || !schema.primaryKeys().getFirst().equals(column.name())) {
            throw new IllegalArgumentException(
                    "MySQL hybrid snapshot currently requires the chunk column to be its sole physical primary key");
        }
        if (column.dataType().getTypeRoot() != LogicalTypeRoot.BIGINT || column.nullable()) {
            throw new IllegalArgumentException("Initial MySQL hybrid snapshot requires a non-null BIGINT key");
        }
        return column.name();
    }
}
