package io.yak.ops.connector.cdc.mysql.debezium.reader;

import io.yak.ops.connector.base.source.reader.RecordsBySplits;
import io.yak.ops.connector.base.source.reader.RecordsWithSplitIds;
import io.yak.ops.connector.cdc.mysql.source.assigners.ChunkRange;
import io.yak.ops.connector.cdc.mysql.source.config.MySqlSourceConfig;
import io.yak.ops.connector.cdc.mysql.source.split.MySqlSnapshotSplit;
import io.yak.ops.connector.cdc.mysql.source.utils.StatementUtils;
import io.yak.ops.connector.jdbc.database.connection.JdbcConnectionProvider;
import io.yak.ops.connector.jdbc.database.dialect.JdbcDialect;
import io.yak.ops.connector.jdbc.database.dialect.JdbcDialectConverter;
import io.yak.ops.connector.jdbc.database.dialect.JdbcDialects;
import io.yak.ops.core.data.RowKind;
import io.yak.ops.core.data.TableId;
import io.yak.ops.core.data.TableRecord;
import io.yak.ops.core.types.Column;
import io.yak.ops.core.types.TableSchema;
import java.sql.Connection;
import java.sql.PreparedStatement;
import java.sql.ResultSet;
import java.sql.SQLException;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.Set;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.slf4j.MDC;

/**
 * Performs bounded JDBC snapshot scanning on the owning Connector Base fetcher thread.
 *
 * <p>Physical JDBC statements and ResultSets never cross the mailbox handover. Rows are
 * converted into detached Core TableRecords. Legacy BIGINT scan progress is advanced
 * only by the mailbox emitter. A typed scan has no per-row checkpoint cursor and must
 * be fully reconciled and frozen before any row reaches the downstream.
 */
public final class SnapshotSplitReader implements AutoCloseable {

    private static final Logger LOG = LoggerFactory.getLogger(SnapshotSplitReader.class);

    private static final int DEFAULT_FETCH_SIZE = 256;

    private final JdbcConnectionProvider connections;
    private final JdbcDialect dialect;
    private java.util.function.Function<TableId, TableSchema> schemas;
    private final int fetchSize;
    private java.util.function.Function<TableId, String> snapshotPredicates = table -> null;

    private Connection connection;
    private volatile PreparedStatement statement;
    private volatile boolean cancelled;
    private ResultSet results;
    private JdbcDialectConverter converter;
    private MySqlSnapshotSplit active;
    private boolean hasRow;
    private long splitStartedNanos;
    private long fetchedRows;
    private long batchId;

    public SnapshotSplitReader(MySqlSourceConfig config) {
        this(
                config::openSnapshotConnection,
                JdbcDialects.forUrl("jdbc:mysql:"),
                config.schemas(),
                config.getFetchSize());
        snapshotPredicates = table -> config.tableOptions(table).snapshotWhere();
        schemas = table -> config.schemas().get(table);
    }

    public SnapshotSplitReader(
            JdbcConnectionProvider connections, JdbcDialect dialect, Map<TableId, TableSchema> schemas) {
        this(connections, dialect, schemas, DEFAULT_FETCH_SIZE);
    }

    public SnapshotSplitReader(
            JdbcConnectionProvider connections, JdbcDialect dialect, Map<TableId, TableSchema> schemas, int fetchSize) {
        this.connections = Objects.requireNonNull(connections, "connections");
        this.dialect = Objects.requireNonNull(dialect, "dialect");
        Map<TableId, TableSchema> frozen = Map.copyOf(Objects.requireNonNull(schemas, "schemas"));
        this.schemas = frozen::get;
        if (fetchSize <= 0) throw new IllegalArgumentException("Snapshot fetch size must be positive");
        this.fetchSize = fetchSize;
    }

    /** Opens a half-open range; typed splits are only consumed by the backfill/freeze stage. */
    public void open(MySqlSnapshotSplit split) throws SQLException {
        open(split, null);
    }

    /**
     * Uses a caller-owned, already established consistent MySQL read view for typed chunks.
     * This Reader takes over ownership and closes the connection on completion or failure.
     */
    public void open(MySqlSnapshotSplit split, Connection consistentView) throws SQLException {
        checkCancelled();
        if (split.chunkRange() == null) {
            throw new IllegalArgumentException("Snapshot Reader requires a typed ChunkRange");
        }
        if (split.chunkRange() != null && split.prepared()) {
            throw new IllegalArgumentException("Frozen typed snapshot output must be replayed, not rescanned");
        }
        if (active != null) {
            throw new IllegalStateException("Snapshot reader already has a running split");
        }
        splitStartedNanos = System.nanoTime();
        fetchedRows = 0;
        batchId = 0;
        TableSchema schema = Objects.requireNonNull(schemas.apply(split.tableId()), "snapshot table schema");
        ChunkRange range = split.chunkRange();
        String key = range.column();
        if (range != null) {
            Column selected = schema.columns().stream()
                    .filter(column -> column.name().equals(range.column()))
                    .findFirst()
                    .orElseThrow(() -> new IllegalArgumentException("Typed snapshot chunk column is missing"));
            if (!split.tableId().equals(range.tableId())
                    || selected.nullable()
                    || selected.dataType().getTypeRoot() != range.type()) {
                throw new IllegalArgumentException("Typed snapshot range conflicts with its source schema");
            }
        }
        Object lower = range.start();
        Object upper = range.end();
        String sql = StatementUtils.buildSplitScanQuery(
                dialect,
                split.tableId(),
                schema,
                key,
                lower == null,
                upper == null,
                snapshotPredicates.apply(split.tableId()));
        try {
            connection = consistentView == null ? connections.getConnection() : consistentView;
            if (consistentView == null) connection.setReadOnly(true);
            statement = connection.prepareStatement(sql, ResultSet.TYPE_FORWARD_ONLY, ResultSet.CONCUR_READ_ONLY);
            checkCancelled();
            statement.setQueryTimeout(60);
            statement.setFetchSize(fetchSize);
            int parameter = 1;
            if (lower != null) {
                StatementUtils.setSafeObject(statement, parameter++, lower);
            }
            if (upper != null) {
                StatementUtils.setSafeObject(statement, parameter, upper);
            }
            checkCancelled();
            long executeStartedNanos = System.nanoTime();
            results = statement.executeQuery();
            if ("true".equals(MDC.get("taskLogDebug"))) {
                LOG.debug(
                        "CDC snapshot query executed, split={}, executeQueryMs={}",
                        split.splitId(),
                        (System.nanoTime() - executeStartedNanos) / 1_000_000);
            }
            converter = dialect.createRowConverter(results.getMetaData());
            TableSchema actual = converter.schema();
            if (actual.columnCount() != schema.columnCount()) {
                throw new SQLException("MySQL snapshot projected column count changed");
            }
            for (int index = 0; index < schema.columnCount(); index++) {
                if (!schema.column(index).name().equals(actual.column(index).name())
                        || schema.column(index).dataType().getTypeRoot()
                                != actual.column(index).dataType().getTypeRoot()) {
                    throw new SQLException("MySQL snapshot schema changed during Hybrid execution");
                }
            }
            hasRow = results.next();
            active = split;
        } catch (SQLException | RuntimeException failure) {
            try {
                close();
            } catch (Exception closeFailure) {
                failure.addSuppressed(closeFailure);
            }
            throw failure;
        }
    }

    /**
     * Fetches raw typed rows without assigning a synthetic key or advancing a resume cursor.
     *
     * <p>Only the bounded snapshot/backfill stage may consume these rows. If it fails before
     * freezing its normalized output, the entire half-open chunk must be reread; a partial
     * raw scan cannot be resumed by skipping equal Chunk Key values.
     */
    public RecordsWithSplitIds<TableRecord> fetch() throws SQLException {
        checkCancelled();
        if (active == null) {
            throw new IllegalStateException("Typed fetch requires an active typed snapshot split");
        }
        MySqlSnapshotSplit split = active;
        long startedNanos = System.nanoTime();
        List<TableRecord> batch = new ArrayList<>(fetchSize);
        for (int remaining = fetchSize; hasRow && remaining > 0; remaining--) {
            checkCancelled();
            batch.add(new TableRecord(split.tableId(), RowKind.INSERT, converter.toInternal(results)));
            hasRow = results.next();
        }
        boolean completed = !hasRow;
        fetchedRows += batch.size();
        if ("true".equals(MDC.get("taskLogDebug"))) {
            LOG.debug(
                    "CDC typed snapshot fetch completed, split={}, batch={}, batchRows={}, fetchMs={}",
                    split.splitId(),
                    ++batchId,
                    batch.size(),
                    (System.nanoTime() - startedNanos) / 1_000_000);
        }
        if (completed) {
            close();
        }
        return new RecordsBySplits<>(
                batch.isEmpty() ? Map.of() : Map.of(split.splitId(), batch),
                completed ? Set.of(split.splitId()) : Set.of());
    }

    /** Signals driver cancellation without making the requesting mailbox wait for network I/O. */
    public void cancel() {
        cancelled = true;
        PreparedStatement running = statement;
        if (running != null) {
            Thread.ofVirtual().name("yak-mysql-snapshot-cancel").start(() -> {
                try {
                    running.cancel();
                } catch (SQLException ignored) {
                    // The fetcher still owns close and observes the query outcome.
                }
            });
        }
    }

    private void checkCancelled() throws SQLException {
        if (cancelled) throw new SQLException("MySQL snapshot read was cancelled");
    }

    @Override
    public void close() throws SQLException {
        SQLException problem = null;
        if (results != null) {
            try {
                results.close();
            } catch (SQLException error) {
                problem = error;
            }
            results = null;
        }
        if (statement != null) {
            try {
                statement.close();
            } catch (SQLException error) {
                if (problem == null) {
                    problem = error;
                } else {
                    problem.addSuppressed(error);
                }
            }
            statement = null;
        }
        if (connection != null) {
            try {
                connection.close();
            } catch (SQLException error) {
                if (problem == null) {
                    problem = error;
                } else {
                    problem.addSuppressed(error);
                }
            }
            connection = null;
        }
        active = null;
        hasRow = false;
        converter = null;
        if (problem != null) {
            throw problem;
        }
    }
}
