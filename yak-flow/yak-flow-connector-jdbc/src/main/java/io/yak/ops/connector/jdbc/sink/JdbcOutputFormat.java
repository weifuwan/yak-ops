package io.yak.ops.connector.jdbc.sink;

import io.yak.ops.connector.base.sink.writer.BatchOutput;
import io.yak.ops.connector.jdbc.database.connection.JdbcConnectionProvider;
import io.yak.ops.connector.jdbc.database.connection.JdbcConnectionRetry;
import io.yak.ops.connector.jdbc.database.dialect.JdbcDialect;
import io.yak.ops.connector.jdbc.sink.executor.TableBufferedStatementExecutor;
import io.yak.ops.connector.jdbc.sink.executor.TableChangelogStatementExecutor;
import io.yak.ops.core.data.TableRecord;
import java.sql.Connection;
import java.sql.SQLException;
import java.util.List;
import java.util.Objects;
import java.util.concurrent.CancellationException;

/**
 * One transaction and one buffered-record owner for all routes of a JDBC Sink Writer.
 *
 * <p>Statement groups are executed in the source's arrival order within a transaction.
 * Only a successful explicit flush commits; close rolls back without flushing. Failed or
 * ambiguous transactions are never retried inside JDBC.
 */
public final class JdbcOutputFormat implements BatchOutput<TableRecord> {

    private final Connection connection;
    private final TableBufferedStatementExecutor<TableRecord> executor;
    private volatile boolean cancelled;
    private volatile boolean closed;
    private boolean failed;

    public JdbcOutputFormat(JdbcConnectionProvider connections, JdbcDialect dialect, JdbcTableWritePlan plan)
            throws SQLException {
        this(connections, dialect, List.of(plan));
    }

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

    @Override
    public void flush() throws SQLException {
        ensureActive();
        if (executor.bufferedRecords() == 0) {
            return;
        }
        try {
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
