package io.yak.ops.connector.jdbc.xa;

import io.yak.ops.core.api.connector.sink.Committer;
import java.sql.SQLException;
import java.util.Collection;
import java.util.Objects;
import javax.sql.XAConnection;
import javax.sql.XADataSource;
import javax.transaction.xa.XAException;
import javax.transaction.xa.XAResource;

/**
 * Transactional MySQL XA committer. Only XIDs scoped to the configured persistent instance
 * may be committed. No retry of ambiguous XA results is performed by this component.
 *
 * <p>Conservative missing-XID behavior intentionally fails closed: without an independently
 * durable decision, XAER_NOTA cannot prove whether a transaction committed or was rolled back.
 */
public final class MySqlXaCommitter implements Committer<MySqlXaXid> {

    private final XADataSource dataSource;
    private final String instanceId;

    public MySqlXaCommitter(XADataSource dataSource, String instanceId) {
        this.dataSource = Objects.requireNonNull(dataSource, "XA datasource");
        if (instanceId == null || !instanceId.matches("[A-Za-z0-9_-]{1,40}")) {
            throw new IllegalArgumentException("Invalid persistent XA instance identity");
        }
        this.instanceId = instanceId;
    }

    @Override
    public void commit(Collection<MySqlXaXid> committables, boolean recovered) throws SQLException, XAException {
        Objects.requireNonNull(committables, "committables");
        for (MySqlXaXid xid : committables) {
            if (xid == null || !xid.belongsTo(instanceId)) {
                throw new IllegalArgumentException("XA committable does not belong to this task instance");
            }
        }
        if (committables.isEmpty()) return;
        XAConnection connection = dataSource.getXAConnection();
        Throwable failure = null;
        try {
            XAResource resource = connection.getXAResource();
            for (MySqlXaXid xid : committables) {
                // Prepared transactions may outlive the writer connection on supported MySQL.
                // XAER_NOTA cannot be assumed equivalent to a successful commit.
                resource.commit(xid, false);
            }
        } catch (SQLException | XAException | RuntimeException error) {
            failure = error;
            throw error;
        } finally {
            try {
                connection.close();
            } catch (SQLException closeError) {
                if (failure != null) failure.addSuppressed(closeError);
                else throw closeError;
            }
        }
    }

    @Override
    public void close() {
        // Each commit acquires and closes an independent XAConnection.
    }
}
