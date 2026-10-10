package io.yak.ops.connector.jdbc.sink;

import io.yak.ops.connector.base.sink.writer.BatchOutput;
import io.yak.ops.connector.jdbc.database.connection.JdbcConnectionProvider;
import io.yak.ops.connector.jdbc.database.connection.JdbcConnectionRetry;
import io.yak.ops.connector.jdbc.database.dialect.JdbcDialect;
import io.yak.ops.connector.jdbc.sink.executor.TableBufferedStatementExecutor;
import io.yak.ops.connector.jdbc.sink.executor.TableSimpleStatementExecutor;
import io.yak.ops.core.data.RowKind;
import io.yak.ops.core.data.TableRecord;
import java.sql.Connection;
import java.sql.SQLException;
import java.util.Objects;
import java.util.concurrent.CancellationException;

/**
 * Connection, transaction and statement lifecycle for one single-table JDBC Writer.
 *
 * <p>The enclosed buffered executor is the only owner of pending records. This output
 * never schedules threads for flushing, never retries ambiguous commits, and never
 * commits on close. Only a successful explicit flush commits and clears pending records.
 * Delivery is at-least-once when Runtime restores from a completed checkpoint.
 */
public final class JdbcOutputFormat implements BatchOutput<TableRecord> {

    private final Connection connection;
    private final JdbcTableWritePlan plan;
    private final TableBufferedStatementExecutor executor;
    private volatile boolean cancelled;
    private volatile boolean closed;
    private boolean failed;

    public JdbcOutputFormat(JdbcConnectionProvider connections, JdbcDialect dialect, JdbcTableWritePlan plan)
            throws SQLException {
        Objects.requireNonNull(connections, "connections");
        Objects.requireNonNull(dialect, "dialect");
        this.plan = Objects.requireNonNull(plan, "plan");
        String sql = plan.sql(dialect);
        TableBufferedStatementExecutor statement = new TableBufferedStatementExecutor(
                new TableSimpleStatementExecutor(sql, dialect.createRowConverter(plan.schema())));
        Connection opened = JdbcConnectionRetry.open(connections, 1);
        try {
            opened.setAutoCommit(false);
            statement.prepareStatements(opened);
        } catch (SQLException | RuntimeException failure) {
            try {
                opened.close();
            } catch (SQLException closeFailure) {
                failure.addSuppressed(closeFailure);
            }
            throw failure;
        }
        this.connection = opened;
        this.executor = statement;
    }

    @Override
    public void add(TableRecord record) {
        ensureActive();
        Objects.requireNonNull(record, "record");
        if (!plan.sourceTable().equals(record.tableId())) {
            throw new IllegalArgumentException("JDBC Sink received a record from an unexpected source table");
        }
        if (record.rowKind() != RowKind.INSERT) {
            throw new UnsupportedOperationException("JDBC Sink changelog events require the next integration phase");
        }
        if (record.row().getArity() != plan.schema().columnCount()) {
            throw new IllegalArgumentException("JDBC Sink row arity does not match target schema");
        }
        executor.addToBatch(record.row());
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
        // JDBC Statement.cancel() can block inside the driver. Do not block the cancelling
        // thread or mutate connection ownership; task cleanup still performs close().
        Thread.ofVirtual().name("yak-jdbc-sink-cancel").start(() -> {
            try {
                executor.cancel();
            } catch (SQLException ignored) {
                // A failed or closed statement is disposed by task-owned close().
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
