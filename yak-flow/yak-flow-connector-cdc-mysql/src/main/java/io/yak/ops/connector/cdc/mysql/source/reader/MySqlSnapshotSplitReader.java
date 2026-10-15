package io.yak.ops.connector.cdc.mysql.source.reader;

import io.yak.ops.connector.base.source.reader.RecordsBySplits;
import io.yak.ops.connector.base.source.reader.RecordsWithSplitIds;
import io.yak.ops.connector.cdc.mysql.source.MySqlCdcSourceConfig;
import io.yak.ops.connector.cdc.mysql.source.assigner.MySqlChunkSplitter;
import io.yak.ops.connector.cdc.mysql.source.split.MySqlSnapshotSplit;
import io.yak.ops.connector.jdbc.database.connection.JdbcConnectionProvider;
import io.yak.ops.connector.jdbc.database.dialect.JdbcDialect;
import io.yak.ops.connector.jdbc.database.dialect.JdbcDialectConverter;
import io.yak.ops.connector.jdbc.database.dialect.JdbcDialects;
import io.yak.ops.core.data.RowKind;
import io.yak.ops.core.data.TableId;
import io.yak.ops.core.data.TableRecord;
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
import java.util.stream.Collectors;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.slf4j.MDC;

/**
 * Performs bounded JDBC snapshot scanning on the owning Connector Base fetcher thread.
 *
 * <p>Physical JDBC statements and ResultSets never cross the mailbox handover. Rows are
 * converted into detached Core TableRecords and their primary-key positions are advanced
 * only by the mailbox RecordEmitter, not by this prefetcher.
 */
public final class MySqlSnapshotSplitReader implements AutoCloseable {

    private static final Logger LOG = LoggerFactory.getLogger(MySqlSnapshotSplitReader.class);

    private static final int FETCH_BATCH = 256;

    private final JdbcConnectionProvider connections;
    private final JdbcDialect dialect;
    private final Map<TableId, TableSchema> schemas;

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

    public MySqlSnapshotSplitReader(MySqlCdcSourceConfig config) {
        this(config::openSnapshotConnection, JdbcDialects.forUrl("jdbc:mysql:"), config.schemas());
    }

    public MySqlSnapshotSplitReader(
            JdbcConnectionProvider connections, JdbcDialect dialect, Map<TableId, TableSchema> schemas) {
        this.connections = Objects.requireNonNull(connections, "connections");
        this.dialect = Objects.requireNonNull(dialect, "dialect");
        this.schemas = Map.copyOf(Objects.requireNonNull(schemas, "schemas"));
    }

    /** Starts a split from its last mailbox-emitted key using ordered, exclusive cursor seek. */
    public void open(MySqlSnapshotSplit split) throws SQLException {
        checkCancelled();
        if (active != null) {
            throw new IllegalStateException("Snapshot reader already has a running split");
        }
        splitStartedNanos = System.nanoTime();
        fetchedRows = 0;
        batchId = 0;
        TableSchema schema = Objects.requireNonNull(schemas.get(split.tableId()), "snapshot table schema");
        String key = MySqlChunkSplitter.splitKey(schema);
        String quoted = dialect.quoteIdentifier(key);
        String columns = schema.columns().stream()
                .map(column -> dialect.quoteIdentifier(column.name()))
                .collect(Collectors.joining(", "));
        List<Long> bounds = new ArrayList<>();
        List<String> predicates = new ArrayList<>();
        if (split.lowerInclusive() != null) {
            predicates.add(quoted + " >= ?");
            bounds.add(split.lowerInclusive());
        }
        if (split.upperExclusive() != null) {
            predicates.add(quoted + " < ?");
            bounds.add(split.upperExclusive());
        }
        if (split.lastEmittedKey() != null) {
            predicates.add(quoted + " > ?");
            bounds.add(split.lastEmittedKey());
        }
        String sql = "SELECT " + columns + " FROM " + dialect.qualifiedTable(split.tableId())
                + (predicates.isEmpty() ? "" : " WHERE " + String.join(" AND ", predicates))
                + " ORDER BY " + quoted;
        try {
            connection = connections.getConnection();
            connection.setReadOnly(true);
            statement = connection.prepareStatement(sql, ResultSet.TYPE_FORWARD_ONLY, ResultSet.CONCUR_READ_ONLY);
            checkCancelled();
            statement.setQueryTimeout(60);
            statement.setFetchSize(FETCH_BATCH);
            for (int index = 0; index < bounds.size(); index++) {
                statement.setLong(index + 1, bounds.get(index));
            }
            checkCancelled();
            long executeStartedNanos = System.nanoTime();
            results = statement.executeQuery();
            if ("true".equals(MDC.get("taskLogDebug"))) {
                LOG.debug(
                        "CDC snapshot query executed, split={}, sql={}, executeQueryMs={}",
                        split.splitId(),
                        sql,
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
     * Returns no more than FETCH_BATCH detached rows plus a terminal split marker if exhausted.
     *
     * @return bounded records and at most one finished split ID
     */
    public RecordsWithSplitIds<MySqlHybridFetchedRecord> fetch() throws SQLException {
        checkCancelled();
        if (active == null) {
            throw new IllegalStateException("No active MySQL snapshot split");
        }
        MySqlSnapshotSplit split = active;
        long fetchStartedNanos = System.nanoTime();
        String key = MySqlChunkSplitter.splitKey(schemas.get(split.tableId()));
        List<MySqlHybridFetchedRecord> batch = new ArrayList<>();
        for (int remaining = FETCH_BATCH; hasRow && remaining > 0; remaining--) {
            checkCancelled();
            var row = converter.toInternal(results);
            long keyValue = results.getLong(key);
            if (results.wasNull()) {
                throw new SQLException("MySQL snapshot primary key must not be null");
            }
            batch.add(new MySqlHybridFetchedRecord(
                    List.of(new TableRecord(split.tableId(), RowKind.INSERT, row)), keyValue, null, false));
            hasRow = results.next();
        }
        boolean completed = !hasRow;
        fetchedRows += batch.size();
        if ("true".equals(MDC.get("taskLogDebug"))) {
            LOG.debug(
                    "CDC snapshot fetch completed, split={}, batch={}, batchRows={}, fetchMs={}",
                    split.splitId(),
                    ++batchId,
                    batch.size(),
                    (System.nanoTime() - fetchStartedNanos) / 1_000_000);
        }
        if (completed) {
            if ("true".equals(MDC.get("taskLogDebug"))) {
                LOG.debug(
                        "CDC snapshot split finished, split={}, fetchedRows={}, durationMs={}",
                        split.splitId(),
                        fetchedRows,
                        (System.nanoTime() - splitStartedNanos) / 1_000_000);
            }
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
