package io.yak.ops.connector.jdbc.xa;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;

import java.lang.reflect.Proxy;
import java.sql.SQLException;
import java.util.List;
import java.util.concurrent.atomic.AtomicInteger;
import javax.sql.XAConnection;
import javax.sql.XADataSource;
import javax.transaction.xa.XAException;
import javax.transaction.xa.XAResource;
import org.junit.jupiter.api.Test;

/** Guard against foreign transaction commits and ambiguous outcomes. */
class MySqlXaCommitterTest {

    @Test
    void commitsOnlyPreparedTransactionsForTheConfiguredInstance() throws Exception {
        AtomicInteger commits = new AtomicInteger();
        XAResource resource = resource(commits, false);
        XADataSource dataSource = dataSource(resource);
        var committer = new MySqlXaCommitter(dataSource, "instance-1");
        MySqlXaXid own = MySqlXaXid.next("instance-1", 0, 1);
        committer.commit(List.of(own), false);
        assertEquals(1, commits.get());

        MySqlXaXid foreign = MySqlXaXid.next("instance-2", 0, 1);
        assertThrows(IllegalArgumentException.class, () -> committer.commit(List.of(foreign), true));
        assertEquals(1, commits.get());
    }

    @Test
    void ambiguousCommitIsNotSilentlyAcknowledged() {
        var committer = new MySqlXaCommitter(dataSource(resource(new AtomicInteger(), true)), "instance-1");
        assertThrows(XAException.class, () -> committer.commit(
                List.of(MySqlXaXid.next("instance-1", 0, 8)), true));
    }

    private static XAResource resource(AtomicInteger commits, boolean fail) {
        return (XAResource) Proxy.newProxyInstance(
                XAResource.class.getClassLoader(),
                new Class<?>[] {XAResource.class},
                (proxy, method, args) -> {
                    if (method.getName().equals("commit")) {
                        if (fail) {
                            XAException error = new XAException(XAException.XAER_RMFAIL);
                            throw error;
                        }
                        commits.incrementAndGet();
                    }
                    if (method.getReturnType() == boolean.class) return false;
                    if (method.getReturnType() == int.class) return 0;
                    if (method.getReturnType().isArray()) return new javax.transaction.xa.Xid[0];
                    return null;
                });
    }

    private static XADataSource dataSource(XAResource resource) {
        XAConnection connection = (XAConnection) Proxy.newProxyInstance(
                XAConnection.class.getClassLoader(),
                new Class<?>[] {XAConnection.class},
                (proxy, method, args) -> {
                    if (method.getName().equals("getXAResource")) return resource;
                    return null;
                });
        return (XADataSource) Proxy.newProxyInstance(
                XADataSource.class.getClassLoader(),
                new Class<?>[] {XADataSource.class},
                (proxy, method, args) -> {
                    if (method.getName().equals("getXAConnection")) return connection;
                    if (method.getReturnType() == int.class) return 0;
                    return null;
                });
    }
}
