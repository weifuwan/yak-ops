package io.yak.ops.connector.jdbc.database;

import static org.junit.jupiter.api.Assertions.assertInstanceOf;
import static org.junit.jupiter.api.Assertions.assertNotSame;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

import io.yak.ops.connector.jdbc.JdbcConnectionOptions;
import io.yak.ops.connector.jdbc.database.catalog.JdbcCatalog;
import io.yak.ops.connector.jdbc.database.connection.DriverManagerJdbcConnectionProvider;
import io.yak.ops.connector.jdbc.database.dialect.JdbcDialect;
import io.yak.ops.connector.jdbc.database.internal.MySqlJdbcFactory;
import io.yak.ops.connector.jdbc.database.internal.dialect.MySqlJdbcDialect;
import io.yak.ops.connector.jdbc.database.internal.dialect.OracleJdbcDialect;
import io.yak.ops.connector.jdbc.database.internal.dialect.PostgresJdbcDialect;
import java.sql.Connection;
import java.util.List;
import org.junit.jupiter.api.Test;

/** Verifies actual SPI discovery, unambiguous database selection and per-call connection ownership. */
class JdbcFactoryLoaderTest {

    @Test
    void resolvesBuiltInDatabaseFactoriesThroughServiceLoader() {
        assertInstanceOf(MySqlJdbcDialect.class, JdbcFactoryLoader.loadDialect("jdbc:mysql://localhost/store"));
        assertInstanceOf(PostgresJdbcDialect.class, JdbcFactoryLoader.loadDialect("jdbc:postgresql://localhost/store"));
        assertInstanceOf(OracleJdbcDialect.class, JdbcFactoryLoader.loadDialect("jdbc:oracle:thin:@localhost:1521/X"));
        assertInstanceOf(
                io.yak.ops.connector.jdbc.database.internal.dialect.AnsiJdbcDialect.class,
                JdbcFactoryLoader.loadDialect("jdbc:h2:mem:test"));
    }

    @Test
    void rejectsUnknownAndAmbiguousFactoriesWithoutLeakingCredentials() {
        IllegalStateException notFound =
                assertThrows(IllegalStateException.class, () -> JdbcFactoryLoader.loadDialect("jdbc:unknown://secret"));
        assertTrue(!notFound.getMessage().contains("secret"));
        assertThrows(
                IllegalStateException.class,
                () -> JdbcFactoryLoader.resolve(
                        "jdbc:mysql://localhost/store", List.of(new MySqlJdbcFactory(), new MySqlJdbcFactory())));
        assertThrows(
                IllegalArgumentException.class, () -> JdbcFactoryLoader.resolve("invalid", List.of()));
    }

    @Test
    void aConnectionProviderReturnsIndependentCallerOwnedConnections() throws Exception {
        var provider =
                new DriverManagerJdbcConnectionProvider(new JdbcConnectionOptions("jdbc:h2:mem:factory", "sa", ""));
        try (Connection first = provider.getConnection(); Connection second = provider.getConnection()) {
            assertNotSame(first, second);
        }
    }

    @Test
    void factoriesAlsoBuildVendorCatalogsWithoutOpeningConnections() throws Exception {
        var options = new JdbcConnectionOptions("jdbc:h2:mem:catalog-factory", "sa", "");
        var provider = new DriverManagerJdbcConnectionProvider(options);
        try (JdbcCatalog mysql = JdbcFactoryLoader.loadCatalog("jdbc:mysql://localhost/db", provider);
                JdbcCatalog postgres = JdbcFactoryLoader.loadCatalog("jdbc:postgresql://localhost/db", provider);
                JdbcCatalog oracle = JdbcFactoryLoader.loadCatalog("jdbc:oracle:thin:@localhost/X", provider)) {
            assertInstanceOf(io.yak.ops.connector.jdbc.database.internal.catalog.MySqlCatalog.class, mysql);
            assertInstanceOf(io.yak.ops.connector.jdbc.database.internal.catalog.PostgresCatalog.class, postgres);
            assertInstanceOf(io.yak.ops.connector.jdbc.database.internal.catalog.OracleCatalog.class, oracle);
        }
    }

    @Test
    void externallySuppliedClassLoaderCanDiscoverRegisteredProviders() {
        ClassLoader loader = getClass().getClassLoader();
        JdbcDialect dialect = JdbcFactoryLoader.loadDialect("jdbc:mysql://localhost", loader);
        assertInstanceOf(MySqlJdbcDialect.class, dialect);
    }
}
