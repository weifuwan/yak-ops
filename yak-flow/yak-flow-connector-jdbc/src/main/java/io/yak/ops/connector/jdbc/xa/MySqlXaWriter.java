package io.yak.ops.connector.jdbc.xa;

import io.yak.ops.connector.jdbc.JdbcConnectionOptions;
import io.yak.ops.connector.jdbc.database.dialect.JdbcDialect;
import io.yak.ops.connector.jdbc.internal.executor.TableChangelogStatementExecutor;
import io.yak.ops.connector.jdbc.sink.JdbcKeylessTargetValidator;
import io.yak.ops.connector.jdbc.sink.JdbcTableWritePlan;
import io.yak.ops.core.api.connector.sink.CommittingSinkWriter;
import io.yak.ops.core.api.connector.sink.SinkWriter;
import io.yak.ops.core.data.TableRecord;
import java.sql.Connection;
import java.sql.ResultSet;
import java.sql.SQLException;
import java.sql.Statement;
import java.util.Collection;
import java.util.List;
import java.util.Objects;
import javax.sql.XAConnection;
import javax.transaction.xa.XAException;
import javax.transaction.xa.XAResource;

/**
 * Task-mailbox-owned XA writer. Batch flushes execute statements but never commit transactions.
 * Only prepareCommit exposes an external transaction to checkpoint-coordinated Committer.
 */
public final class MySqlXaWriter implements CommittingSinkWriter<TableRecord, MySqlXaXid> {

    private final JdbcConnectionOptions options;
    private final JdbcDialect dialect;
    private final JdbcTableWritePlan plan;
    private final String instanceId;
    private final int subtask;
    private XAConnection xaConnection;
    private Connection jdbc;
    private XAResource resource;
    private TableChangelogStatementExecutor statements;
    private MySqlXaXid xid;
    private long transactionSequence;
    private int pendingRows;
    private boolean stopped;

    public MySqlXaWriter(
            JdbcConnectionOptions options,
            JdbcDialect dialect,
            JdbcTableWritePlan plan,
            String instanceId,
            int subtask) {
        this.options = Objects.requireNonNull(options, "options");
        this.dialect = Objects.requireNonNull(dialect, "dialect");
        this.plan = Objects.requireNonNull(plan, "plan");
        this.instanceId = Objects.requireNonNull(instanceId, "instanceId");
        if (subtask < 0) throw new IllegalArgumentException("Invalid XA subtask index");
        this.subtask = subtask;
    }

    @Override
    public void write(TableRecord element, SinkWriter.Context context) throws Exception {
        requireRunning();
        if (resource == null) begin();
        var detached = statements.snapshot(Objects.requireNonNull(element, "element"));
        statements.addToBatch(detached);
        if (++pendingRows >= 500) flush(false);
    }

    @Override
    public void flush(boolean endOfInput) throws Exception {
        requireRunning();
        if (endOfInput) {
            throw new UnsupportedOperationException("MySQL XA Sink requires a coordinated final checkpoint");
        }
        if (resource != null) {
            statements.requireCompleteUpdate();
            statements.executeBatch();
            pendingRows = 0;
        }
    }

    @Override
    public Collection<MySqlXaXid> prepareCommit(long checkpointId) throws Exception {
        requireRunning();
        if (checkpointId <= 0) throw new IllegalArgumentException("Invalid XA checkpoint ID");
        if (resource == null) return List.of();
        flush(false);
        MySqlXaXid prepared = xid;
        try {
            resource.end(prepared, XAResource.TMSUCCESS);
            int vote = resource.prepare(prepared);
            // MySQL 8.0.29+ detached XA transactions survive closing this connection.
            closeSession();
            if (vote == XAResource.XA_RDONLY) return List.of();
            if (vote != XAResource.XA_OK) {
                throw new XAException("Unexpected XA prepare result: " + vote);
            }
            return List.of(prepared);
        } catch (SQLException | XAException | RuntimeException failure) {
            stopped = true;
            throw failure;
        }
    }

    private void begin() throws Exception {
        xaConnection = MySqlXaSink.dataSource(options).getXAConnection();
        try {
            jdbc = xaConnection.getConnection();
            requireDetachedXa(jdbc);
            JdbcKeylessTargetValidator.validate(jdbc, plan, false);
            statements = new TableChangelogStatementExecutor(dialect, List.of(plan), 64, false);
            statements.prepareStatements(jdbc);
            resource = xaConnection.getXAResource();
            xid = MySqlXaXid.next(instanceId, subtask, ++transactionSequence);
            resource.start(xid, XAResource.TMNOFLAGS);
        } catch (Exception | Error failure) {
            stopped = true;
            try {
                closeSession();
            } catch (Exception closeFailure) {
                failure.addSuppressed(closeFailure);
            }
            throw failure;
        }
    }

    /**
     * Prepared transactions must survive the loss of the original physical XA connection.
     * Earlier MySQL versions without detached XA are not admitted.
     */
    private static void requireDetachedXa(Connection connection) throws SQLException {
        try (Statement statement = connection.createStatement();
                ResultSet result = statement.executeQuery("SHOW VARIABLES LIKE 'xa_detach_on_prepare'")) {
            if (!result.next() || !"ON".equalsIgnoreCase(result.getString(2))) {
                throw new SQLException("MySQL XA requires xa_detach_on_prepare=ON");
            }
        }
    }

    private void requireRunning() {
        if (stopped) throw new IllegalStateException("MySQL XA writer has already failed or closed");
    }

    private void closeSession() throws SQLException {
        SQLException failure = null;
        if (statements != null) {
            try {
                statements.closeStatements();
            } catch (SQLException error) {
                failure = error;
            }
            statements = null;
        }
        if (jdbc != null) {
            try {
                jdbc.close();
            } catch (SQLException error) {
                if (failure == null) failure = error;
                else failure.addSuppressed(error);
            }
            jdbc = null;
        }
        if (xaConnection != null) {
            try {
                xaConnection.close();
            } catch (SQLException error) {
                if (failure == null) failure = error;
                else failure.addSuppressed(error);
            }
            xaConnection = null;
        }
        resource = null;
        xid = null;
        if (failure != null) throw failure;
    }

    @Override
    public void close() throws Exception {
        if (stopped && resource == null) {
            closeSession();
            return;
        }
        stopped = true;
        Exception failure = null;
        if (resource != null && xid != null) {
            try {
                resource.end(xid, XAResource.TMFAIL);
                resource.rollback(xid);
            } catch (XAException error) {
                failure = error;
            }
        }
        try {
            closeSession();
        } catch (SQLException error) {
            if (failure == null) failure = error;
            else failure.addSuppressed(error);
        }
        if (failure != null) throw failure;
    }
}
