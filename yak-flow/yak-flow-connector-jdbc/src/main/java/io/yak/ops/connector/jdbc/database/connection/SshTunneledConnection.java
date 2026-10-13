package io.yak.ops.connector.jdbc.database.connection;

import java.lang.reflect.InvocationTargetException;
import java.lang.reflect.Method;
import java.lang.reflect.Proxy;
import java.sql.Connection;
import java.util.concurrent.atomic.AtomicBoolean;

/**
 * Releases an SSH tunnel when its associated JDBC Connection closes.
 *
 * <p>Uses a proxy so that the first Connection.close() releases the tunnel exactly once and avoids leaking the SSH session.
 */
final class SshTunneledConnection {

    private SshTunneledConnection() {}

    static Connection wrap(Connection delegate, AutoCloseable tunnel) {
        AtomicBoolean closed = new AtomicBoolean(false);
        return (Connection) Proxy.newProxyInstance(
                SshTunneledConnection.class.getClassLoader(),
                new Class<?>[] {Connection.class},
                (proxy, method, args) -> invoke(delegate, tunnel, closed, method, args));
    }

    private static Object invoke(
            Connection delegate, AutoCloseable tunnel, AtomicBoolean closed, Method method, Object[] args)
            throws Throwable {
        if ("close".equals(method.getName()) && method.getParameterCount() == 0) {
            if (closed.compareAndSet(false, true)) {
                try (AutoCloseable ownedTunnel = tunnel) {
                    delegate.close();
                }
            }
            return null;
        }

        try {
            return method.invoke(delegate, args);
        } catch (InvocationTargetException exception) {
            throw exception.getCause();
        }
    }
}
