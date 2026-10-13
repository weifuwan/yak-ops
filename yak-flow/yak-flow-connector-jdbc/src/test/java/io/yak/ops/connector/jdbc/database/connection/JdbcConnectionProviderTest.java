package io.yak.ops.connector.jdbc.database.connection;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNotSame;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

import io.yak.ops.configuration.Configuration;
import io.yak.ops.connector.jdbc.JdbcConnectionOptions;
import java.lang.reflect.Proxy;
import java.sql.Connection;
import java.sql.Driver;
import java.sql.DriverManager;
import java.sql.DriverPropertyInfo;
import java.sql.SQLException;
import java.util.Map;
import java.util.Properties;
import java.util.concurrent.atomic.AtomicInteger;
import java.util.logging.Logger;
import org.junit.jupiter.api.Test;

/** Exercises the shared connection implementation without a product datasource service. */
class JdbcConnectionProviderTest {
    @Test
    void opensIndependentCallerOwnedConnections() throws Exception {
        JdbcConnectionProvider provider = new DefaultJdbcConnectionProvider(
                new JdbcConnectionOptions("jdbc:h2:mem:independent_connections;DB_CLOSE_DELAY=-1", "sa", ""));
        try (Connection first = provider.getConnection(); Connection second = provider.getConnection()) {
            assertNotSame(first, second);
            first.setAutoCommit(false);
            assertTrue(second.getAutoCommit());
            first.close();
            assertFalse(second.isClosed());
        }
    }

    @Test
    void doesNotLoadDriversOrConnectUntilRequested() {
        JdbcConnectionOptions options = new JdbcConnectionOptions("jdbc:h2:mem:not_opened", "sa", "", "missing.Driver", Map.of());
        JdbcConnectionProvider provider = new DefaultJdbcConnectionProvider(options);
        assertThrows(SQLException.class, provider::getConnection);
    }

    @Test
    void passesDriverPropertiesAndGivesCredentialsPrecedence() throws Exception {
        var settings = new Configuration().set(JdbcConnectionOptions.URL, "jdbc:yak-connection:probe")
                .set(JdbcConnectionOptions.DRIVER_CLASS, ProbeDriver.class.getName())
                .set(JdbcConnectionOptions.USERNAME, "actual-user")
                .set(JdbcConnectionOptions.PASSWORD, "actual-password")
                .set(JdbcConnectionOptions.PROPERTIES, Map.of("user", "ignored", "password", "ignored", "custom", "kept"))
                .set(JdbcConnectionOptions.TIMEOUT, 7).toReadonlyConfig();
        try (Connection ignored = new DefaultJdbcConnectionProvider(new JdbcConnectionOptions(settings)).getConnection()) {
            assertEquals("actual-user", ProbeDriver.last.getProperty("user"));
            assertEquals("actual-password", ProbeDriver.last.getProperty("password"));
            assertEquals("kept", ProbeDriver.last.getProperty("custom"));
            assertEquals(7, DriverManager.getLoginTimeout());
        }
        assertFalse(settings.toString().contains("actual-password"));
        assertFalse(settings.toString().contains("actual-user"));
        assertFalse(settings.toString().contains("jdbc:yak-connection:probe"));
    }

    @Test
    void releasesTunnelExactlyOnceWhenConnectionCloseFails() throws Exception {
        AtomicInteger closes = new AtomicInteger();
        AtomicInteger tunnelCloses = new AtomicInteger();
        Connection failing = (Connection) Proxy.newProxyInstance(getClass().getClassLoader(), new Class<?>[] {Connection.class},
                (proxy, method, arguments) -> {
                    if (method.getName().equals("close")) { closes.incrementAndGet(); throw new SQLException("close failed"); }
                    throw new UnsupportedOperationException(method.getName());
                });
        Connection wrapped = SshTunneledConnection.wrap(failing, () -> { tunnelCloses.incrementAndGet(); throw new IllegalStateException("tunnel close failed"); });
        SQLException failure = assertThrows(SQLException.class, wrapped::close);
        assertEquals("close failed", failure.getMessage());
        assertEquals(1, failure.getSuppressed().length);
        wrapped.close();
        assertEquals(1, closes.get());
        assertEquals(1, tunnelCloses.get());
    }

    @Test
    void releasesTunnelWhenConnectionClosesNormally() throws Exception {
        AtomicInteger closes = new AtomicInteger();
        AtomicInteger tunnelCloses = new AtomicInteger();
        Connection connection = (Connection) Proxy.newProxyInstance(getClass().getClassLoader(), new Class<?>[] {Connection.class},
                (proxy, method, arguments) -> { if (method.getName().equals("close")) { closes.incrementAndGet(); return null; } throw new UnsupportedOperationException(); });
        Connection wrapped = SshTunneledConnection.wrap(connection, tunnelCloses::incrementAndGet);
        wrapped.close();
        wrapped.close();
        assertEquals(1, closes.get());
        assertEquals(1, tunnelCloses.get());
    }

    public static final class ProbeDriver implements Driver {
        static Properties last;
        static { try { DriverManager.registerDriver(new ProbeDriver()); } catch (SQLException failure) { throw new ExceptionInInitializerError(failure); } }
        @Override
        public Connection connect(String url, Properties properties) {
            if (!acceptsURL(url)) { return null; }
            last = properties;
            return (Connection) Proxy.newProxyInstance(getClass().getClassLoader(), new Class<?>[] {Connection.class},
                    (proxy, method, arguments) -> { if (method.getName().equals("close")) { return null; } throw new UnsupportedOperationException(); });
        }
        @Override
        public boolean acceptsURL(String url) { return url.startsWith("jdbc:yak-connection:"); }
        @Override
        public DriverPropertyInfo[] getPropertyInfo(String url, Properties properties) { return new DriverPropertyInfo[0]; }
        @Override
        public int getMajorVersion() { return 1; }
        @Override
        public int getMinorVersion() { return 0; }
        @Override
        public boolean jdbcCompliant() { return false; }
        @Override
        public Logger getParentLogger() { return Logger.getGlobal(); }
    }
}
