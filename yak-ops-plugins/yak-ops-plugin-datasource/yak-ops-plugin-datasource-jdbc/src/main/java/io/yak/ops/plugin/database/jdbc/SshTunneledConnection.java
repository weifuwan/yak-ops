package io.yak.ops.plugin.database.jdbc;

import java.lang.reflect.InvocationTargetException;
import java.lang.reflect.Method;
import java.lang.reflect.Proxy;
import java.sql.Connection;
import java.util.concurrent.atomic.AtomicBoolean;

/** 将 SSH Session 生命周期绑定到 JDBC Connection.close()。 */
final class SshTunneledConnection {

    private SshTunneledConnection() {}

    static Connection wrap(Connection delegate, SshTunnel tunnel) {
        AtomicBoolean closed = new AtomicBoolean(false);
        return (Connection) Proxy.newProxyInstance(
                SshTunneledConnection.class.getClassLoader(),
                new Class<?>[] {Connection.class},
                (proxy, method, args) -> invoke(delegate, tunnel, closed, method, args));
    }

    private static Object invoke(
            Connection delegate, SshTunnel tunnel, AtomicBoolean closed, Method method, Object[] args)
            throws Throwable {
        if ("close".equals(method.getName()) && method.getParameterCount() == 0) {
            if (closed.compareAndSet(false, true)) {
                try {
                    delegate.close();
                } finally {
                    tunnel.close();
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
