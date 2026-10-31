package io.yak.ops.connector.jdbc.internal;

import io.yak.ops.connector.base.metrics.TableMetrics;
import io.yak.ops.connector.base.sink.writer.BatchOutput;
import io.yak.ops.connector.jdbc.database.connection.JdbcConnectionProvider;
import io.yak.ops.connector.jdbc.database.connection.JdbcConnectionRetry;
import io.yak.ops.connector.jdbc.database.dialect.JdbcDialect;
import io.yak.ops.connector.jdbc.database.internal.dialect.MySqlJdbcDialect;
import io.yak.ops.connector.jdbc.database.internal.dialect.OracleJdbcDialect;
import io.yak.ops.connector.jdbc.database.internal.dialect.PostgresJdbcDialect;
import io.yak.ops.connector.jdbc.internal.executor.TableBufferedStatementExecutor;
import io.yak.ops.connector.jdbc.internal.executor.TableChangelogStatementExecutor;
import io.yak.ops.connector.jdbc.sink.JdbcBatchOrder;
import io.yak.ops.connector.jdbc.sink.JdbcSinkOptions;
import io.yak.ops.connector.jdbc.sink.JdbcTableWritePlan;
import io.yak.ops.connector.jdbc.sink.JdbcWriteMode;
import io.yak.ops.connector.jdbc.sink.MySqlExactRowTargetValidator;
import io.yak.ops.connector.jdbc.sink.OracleExactRowTargetValidator;
import io.yak.ops.connector.jdbc.sink.PostgresExactRowTargetValidator;
import io.yak.ops.core.data.TableId;
import io.yak.ops.core.data.TableRecord;
import io.yak.ops.core.event.SchemaChangeRequest;
import java.sql.Connection;
import java.sql.SQLException;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.concurrent.CancellationException;
import java.util.concurrent.TimeUnit;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.slf4j.MDC;

/**
 * Owns one task-local JDBC transaction and the single buffered-record output for Sink routes.
 *
 * <p>Detached records remain in the buffered executor until an explicit successful flush
 * executes ordered statement groups and commits the transaction. Incomplete UPDATE pairs
 * defer automatic batch triggers and cause explicit checkpoint flush to fail closed.
 * Failed or ambiguous commits are not retried in the Connector.
 *
 * <p>Cancellation only signals running JDBC statements. Closing rolls back outstanding
 * work and releases resources; neither operation flushes or commits data.
 */
public final class JdbcOutputFormat implements BatchOutput<TableRecord> {

    private static final Logger LOG = LoggerFactory.getLogger(JdbcOutputFormat.class);
    private static final long REPORT_INTERVAL_NANOS = TimeUnit.SECONDS.toNanos(30);

    private final Connection connection;
    private final JdbcDialect dialect;
    private List<JdbcTableWritePlan> plans;
    private final TableMetrics metrics;
    private final long metricsGeneration;
    private final Map<TableId, Long> pendingTableRows = new LinkedHashMap<>();
    private final Map<TableId, Integer> batchLimits;
    private final TableBufferedStatementExecutor<TableRecord> executor;
    private final TableChangelogStatementExecutor statements;
    private volatile boolean cancelled;
    private volatile boolean closed;
    private boolean failed;
    // Only executed but uncommitted statements require a physical rollback. Buffered rows have
    // not reached the database; rolling back an idle connection can fail on a stale socket.
    private boolean rollbackRequired;
    private long committedRecords;
    private long reportedRecords;
    private final long startedNanos = System.nanoTime();
    private long lastReportNanos = startedNanos;

    public JdbcOutputFormat(JdbcConnectionProvider connections, JdbcDialect dialect, JdbcTableWritePlan plan)
            throws SQLException {
        this(connections, dialect, List.of(plan));
    }

    /**
     * Opens a fresh transaction and prepares statements for the requested table routes.
     *
     * <p>Partially prepared statements and the connection are closed on initialization
     * failure. A new output must be created for every execution attempt.
     *
     * @param connections provider for an independent, task-owned JDBC connection
     * @param dialect target database SQL and value conversion rules
     * @param plans one or more prepared source-to-target write routes
     * @throws SQLException if opening the connection or preparing statements fails
     */
    public JdbcOutputFormat(JdbcConnectionProvider connections, JdbcDialect dialect, List<JdbcTableWritePlan> plans)
            throws SQLException {
        this(connections, dialect, plans, new TableMetrics());
    }

    /** Opens the transaction shared by all routes and reports acknowledged commits by source table. */
    public JdbcOutputFormat(
            JdbcConnectionProvider connections,
            JdbcDialect dialect,
            List<JdbcTableWritePlan> plans,
            TableMetrics metrics)
            throws SQLException {
        this(
                connections,
                dialect,
                plans,
                metrics,
                JdbcBatchOrder.INPUT_ORDER,
                JdbcSinkOptions.STATEMENT_CACHE_SIZE.defaultValue());
    }

    /** Creates one transaction with an explicit physical batch order and statement resource bound. */
    public JdbcOutputFormat(
            JdbcConnectionProvider connections,
            JdbcDialect dialect,
            List<JdbcTableWritePlan> plans,
            TableMetrics metrics,
            JdbcBatchOrder batchOrder,
            int statementCacheSize)
            throws SQLException {
        this(connections, dialect, plans, metrics, batchOrder, statementCacheSize, Map.of());
    }

    public JdbcOutputFormat(
            JdbcConnectionProvider connections,
            JdbcDialect dialect,
            List<JdbcTableWritePlan> plans,
            TableMetrics metrics,
            JdbcBatchOrder batchOrder,
            int statementCacheSize,
            Map<TableId, Integer> batchLimits)
            throws SQLException {
        this.batchLimits = Map.copyOf(batchLimits);
        for (Map.Entry<TableId, Integer> limit : this.batchLimits.entrySet()) {
            if (limit.getValue() < 1
                    || limit.getValue() > 10_000
                    || plans.stream().noneMatch(plan -> plan.sourceTable().equals(limit.getKey()))) {
                throw new IllegalArgumentException("Invalid JDBC route batch limit");
            }
        }
        Objects.requireNonNull(batchOrder, "batchOrder");
        this.dialect = Objects.requireNonNull(dialect, "dialect");
        this.plans = List.copyOf(Objects.requireNonNull(plans, "plans"));
        this.metrics = Objects.requireNonNull(metrics, "metrics");
        metricsGeneration = metrics.generation();
        Objects.requireNonNull(connections, "connections");
        TableChangelogStatementExecutor statement =
                new TableChangelogStatementExecutor(dialect, plans, statementCacheSize);
        TableBufferedStatementExecutor<TableRecord> buffer = new TableBufferedStatementExecutor<>(
                statement,
                statement::snapshot,
                batchOrder == JdbcBatchOrder.INDEPENDENT_TABLES ? TableRecord::tableId : null);
        Connection opened = JdbcConnectionRetry.open(connections, 1);
        try {
            opened.setAutoCommit(false);
            for (JdbcTableWritePlan plan : plans) {
                if (plan.writeMode() == JdbcWriteMode.RETRACT) {
                    if (dialect instanceof MySqlJdbcDialect) {
                        MySqlExactRowTargetValidator.validate(opened, plan);
                    } else if (dialect instanceof PostgresJdbcDialect) {
                        PostgresExactRowTargetValidator.validate(opened, plan);
                    } else if (dialect instanceof OracleJdbcDialect) {
                        OracleExactRowTargetValidator.validate(opened, plan);
                    } else {
                        throw new UnsupportedOperationException("No validated exact-row RETRACT target dialect");
                    }
                }
            }
            buffer.prepareStatements(opened);
        } catch (SQLException | RuntimeException failure) {
            try {
                buffer.closeStatements();
            } catch (SQLException closeFailure) {
                failure.addSuppressed(closeFailure);
            }
            try {
                opened.close();
            } catch (SQLException closeFailure) {
                failure.addSuppressed(closeFailure);
            }
            throw failure;
        }
        connection = opened;
        executor = buffer;
        statements = statement;
    }

    @Override
    public void add(TableRecord record) {
        ensureActive();
        try {
            executor.addToBatch(record);
            pendingTableRows.merge(record.tableId(), 1L, Long::sum);
        } catch (RuntimeException failure) {
            metrics.writeFailed(metricsGeneration, record.tableId());
            throw failure;
        }
    }

    @Override
    public int bufferedRecords() {
        return executor.bufferedRecords();
    }

    @Override
    public boolean automaticFlushRequired(int maximumBatchSize) {
        if (bufferedRecords() >= maximumBatchSize) return true;
        for (Map.Entry<TableId, Long> pending : pendingTableRows.entrySet()) {
            Integer limit = batchLimits.get(pending.getKey());
            if (limit != null && pending.getValue() >= limit) return true;
        }
        return false;
    }

    /**
     * Defers size/timer-triggered flushes while a split UPDATE_BEFORE awaits its after-image.
     *
     * <p>Explicit checkpoint flushes always run and reject incomplete updates instead of
     * acknowledging a partial logical mutation.
     */
    @Override
    public boolean canAutomaticallyFlush() {
        return statements.canAutomaticallyFlush();
    }

    /**
     * Executes buffered statements in input order and commits them as one JDBC transaction.
     *
     * <p>Pending records are acknowledged only after commit succeeds. Any statement or
     * ambiguous commit failure makes this output terminal and attempts rollback without retry.
     *
     * @throws SQLException if a statement batch or commit fails
     */
    @Override
    public void flush() throws SQLException {
        ensureActive();
        int batchRows = executor.bufferedRecords();
        if (batchRows == 0) {
            return;
        }
        long flushStartedNanos = System.nanoTime();
        try {
            statements.requireCompleteUpdate();
            long statementStartedNanos = System.nanoTime();
            rollbackRequired = true;
            executor.executeBatch();
            long statementMs = (System.nanoTime() - statementStartedNanos) / 1_000_000;
            if (cancelled) {
                throw new CancellationException("JDBC Sink was cancelled before commit");
            }
            long commitStartedNanos = System.nanoTime();
            connection.commit();
            rollbackRequired = false;
            long commitMs = (System.nanoTime() - commitStartedNanos) / 1_000_000;
            executor.acknowledgeCommit();
            pendingTableRows.forEach((table, count) -> metrics.committed(metricsGeneration, table, count));
            pendingTableRows.clear();
            committedRecords += batchRows;
            if ("true".equals(MDC.get("taskLogDebug"))) {
                LOG.debug(
                        "JDBC flush completed, batchRows={}, statementMs={}, commitMs={}, totalMs={}",
                        batchRows,
                        statementMs,
                        commitMs,
                        (System.nanoTime() - flushStartedNanos) / 1_000_000);
            }
            reportProgress(false);
        } catch (SQLException | RuntimeException failure) {
            failed = true;
            if (!cancelled) pendingTableRows.keySet().forEach(table -> metrics.writeFailed(metricsGeneration, table));
            if (rollbackRequired) {
                try {
                    connection.rollback();
                    rollbackRequired = false;
                } catch (SQLException rollbackFailure) {
                    failure.addSuppressed(rollbackFailure);
                }
            }
            throw failure;
        }
    }

    /** Validates a replacement route before the runtime applies any target DDL. */
    public void validateSchemaChange(SchemaChangeRequest request) {
        ensureActive();
        updatedPlans(request);
    }

    /**
     * Rebuilds statement SQL and converters on the existing Writer connection after DDL succeeds.
     * The coordinator guarantees that every Writer was flushed before this method is called.
     */
    public void updateSchema(SchemaChangeRequest request) throws SQLException {
        ensureActive();
        if (executor.bufferedRecords() != 0) {
            throw new IllegalStateException("Cannot update JDBC schema with buffered records");
        }
        List<JdbcTableWritePlan> updated = updatedPlans(request);
        statements.updatePlans(dialect, updated);
        plans = updated;
    }

    private List<JdbcTableWritePlan> updatedPlans(SchemaChangeRequest request) {
        Objects.requireNonNull(request, "request");
        List<JdbcTableWritePlan> updated = new java.util.ArrayList<>(plans.size());
        int matched = 0;
        for (JdbcTableWritePlan plan : plans) {
            if (plan.sourceTable().equals(request.sourceTable())
                    && plan.targetTable().equals(request.targetTable())) {
                if (!plan.sourceSchema().equals(request.sourceSchemaBefore().toTableSchema())
                        || !plan.schema().equals(request.targetSchemaBefore().toTableSchema())) {
                    throw new IllegalStateException("Schema request is stale relative to the JDBC write plan");
                }
                updated.add(plan.withSchemas(
                        request.sourceSchemaAfter().toTableSchema(),
                        request.targetSchemaAfter().toTableSchema()));
                matched++;
            } else {
                updated.add(plan);
            }
        }
        if (matched != 1) {
            throw new IllegalArgumentException("Schema request does not identify exactly one JDBC write route");
        }
        return List.copyOf(updated);
    }

    @Override
    public void cancel() {
        if (cancelled || closed) {
            return;
        }
        cancelled = true;
        // Some JDBC drivers block in Statement.cancel(). The requesting thread must not
        // wait for driver I/O; the task mailbox still owns transaction and resource cleanup.
        Thread.ofVirtual().name("yak-jdbc-sink-cancel").start(() -> {
            try {
                executor.cancel();
            } catch (SQLException ignored) {
                // The owning task closes the connection even when a driver cannot cancel.
            }
        });
    }

    @Override
    public void close() throws SQLException {
        if (closed) {
            return;
        }
        closed = true;
        reportProgress(true);
        SQLException failure = null;
        if (rollbackRequired) {
            try {
                connection.rollback();
                rollbackRequired = false;
            } catch (SQLException exception) {
                // Preserve transaction uncertainty and still release statements and the connection.
                failure = exception;
            }
        }
        try {
            executor.closeStatements();
        } catch (SQLException exception) {
            if (failure == null) {
                failure = exception;
            } else {
                failure.addSuppressed(exception);
            }
        }
        try {
            connection.close();
        } catch (SQLException exception) {
            if (failure == null) {
                failure = exception;
            } else {
                failure.addSuppressed(exception);
            }
        }
        if (failure != null) {
            throw failure;
        }
    }

    /** Publishes only successfully acknowledged transaction records; rate is scoped to this Writer. */
    private void reportProgress(boolean terminal) {
        long now = System.nanoTime();
        long elapsed = now - lastReportNanos;
        if (!terminal && elapsed < REPORT_INTERVAL_NANOS) {
            return;
        }
        long committed = committedRecords - reportedRecords;
        long rate = elapsed > 0 ? Math.round(committed * 1_000_000_000.0 / elapsed) : 0;
        if (terminal) {
            LOG.info(
                    "JDBC sink summary, committedRecords={}, durationMs={}, failed={}, cancelled={}",
                    committedRecords,
                    (now - startedNanos) / 1_000_000,
                    failed,
                    cancelled);
        } else {
            LOG.info("JDBC sink progress, committedRecords={}, writeRate={}", committedRecords, rate);
        }
        reportedRecords = committedRecords;
        lastReportNanos = now;
    }

    private void ensureActive() {
        if (cancelled) {
            throw new CancellationException("JDBC Sink writer is cancelled");
        }
        if (closed || failed) {
            throw new IllegalStateException("JDBC Sink writer is closed or previously failed");
        }
    }
}
