package io.yak.ops.connector.jdbc.internal;

import io.yak.ops.connector.base.sink.writer.BatchOutput;
import io.yak.ops.connector.jdbc.database.connection.JdbcConnectionProvider;
import io.yak.ops.connector.jdbc.database.connection.JdbcConnectionRetry;
import io.yak.ops.connector.jdbc.database.dialect.JdbcDialect;
import io.yak.ops.connector.jdbc.internal.executor.TableBufferedStatementExecutor;
import io.yak.ops.connector.jdbc.internal.executor.TableChangelogStatementExecutor;
import io.yak.ops.connector.jdbc.sink.JdbcTableWritePlan;
import io.yak.ops.core.data.TableRecord;
import java.sql.Connection;
import java.sql.SQLException;
import java.util.List;
import java.util.Objects;
import java.util.concurrent.CancellationException;

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

    private final Connection connection;
    private final TableBufferedStatementExecutor<TableRecord> executor;
    private final TableChangelogStatementExecutor statements;
    private volatile boolean cancelled;
    private volatile boolean closed;
    private boolean failed;

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
        Objects.requireNonNull(connections, "connections");
        TableChangelogStatementExecutor statement = new TableChangelogStatementExecutor(dialect, plans);
        TableBufferedStatementExecutor<TableRecord> buffer =
                new TableBufferedStatementExecutor<>(statement, statement::snapshot);
        Connection opened = JdbcConnectionRetry.open(connections, 1);
        try {
            opened.setAutoCommit(false);
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
        executor.addToBatch(record);
    }

    @Override
    public int bufferedRecords() {
        return executor.bufferedRecords();
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
        if (executor.bufferedRecords() == 0) {
            return;
        }
        try {
            statements.requireCompleteUpdate();
            executor.executeBatch();
            if (cancelled) {
                throw new CancellationException("JDBC Sink was cancelled before commit");
            }
            connection.commit();
            executor.acknowledgeCommit();
        } catch (SQLException | RuntimeException failure) {
            failed = true;
            try {
                connection.rollback();
            } catch (SQLException rollbackFailure) {
                failure.addSuppressed(rollbackFailure);
            }
            throw failure;
        }
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
        SQLException failure = null;
        try {
            connection.rollback();
        } catch (SQLException exception) {
            failure = exception;
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

    private void ensureActive() {
        if (cancelled) {
            throw new CancellationException("JDBC Sink writer is cancelled");
        }
        if (closed || failed) {
            throw new IllegalStateException("JDBC Sink writer is closed or previously failed");
        }
    }
}
