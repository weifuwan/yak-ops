package io.yak.ops.business.datasource.plugin;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

import io.yak.ops.core.data.TableId;
import io.yak.ops.plugin.datasource.api.plugin.DataSourceConnection;
import io.yak.ops.plugin.datasource.api.plugin.DataSourcePlugin;
import io.yak.ops.plugin.datasource.api.plugin.DataSourcePluginDescriptor;
import java.lang.reflect.Field;
import java.sql.Connection;
import java.sql.DriverManager;
import java.sql.Statement;
import java.util.Map;
import java.util.Set;
import java.util.concurrent.atomic.AtomicInteger;
import org.junit.jupiter.api.Test;

/** Verifies that Datasource delegates its JDBC metadata queries to one Connector Catalog. */
class DataSourcePluginRegistryCatalogTest {

    @Test
    void injectedProviderOwnsEveryPhysicalAndLogicalMetadataConnection() throws Exception {
        String url = "jdbc:h2:mem:datasource_unified_catalog;DB_CLOSE_DELAY=-1";
        try (Connection connection = DriverManager.getConnection(url, "sa", "");
                Statement ddl = connection.createStatement()) {
            ddl.execute("CREATE SCHEMA APP");
            ddl.execute("CREATE TABLE APP.PRODUCTS (TENANT INT, ID BIGINT, NAME VARCHAR(64),"
                    + " PRIMARY KEY(ID, TENANT))");
        }

        AtomicInteger connectionOpens = new AtomicInteger();
        DataSourceConnection settings = new DataSourceConnection() {
            @Override
            public String type() {
                return "TEST_H2";
            }

            @Override
            public String jdbcUrl() {
                return url;
            }

            @Override
            public String driverClassName() {
                return "org.h2.Driver";
            }

            @Override
            public String username() {
                return "sa";
            }

            @Override
            public String password() {
                return "";
            }

            @Override
            public String database() {
                return null;
            }

            @Override
            public String schema() {
                return "APP";
            }

            @Override
            public Map<String, String> properties() {
                return Map.of();
            }

            @Override
            public String normalizedJson() {
                return "{}";
            }
        };
        DataSourcePlugin plugin = new DataSourcePlugin() {
            @Override
            public String type() {
                return "TEST_H2";
            }

            @Override
            public DataSourcePluginDescriptor descriptor() {
                return new DataSourcePluginDescriptor(
                        "TEST_H2", Set.of(), DataSourcePluginDescriptor.CURRENT_API_VERSION, Set.of(), Set.of());
            }

            @Override
            public DataSourceConnection parseConnection(String connectionJson) {
                return settings;
            }

            @Override
            public void testConnection(DataSourceConnection value, int timeoutSeconds) {
                throw new UnsupportedOperationException();
            }

            @Override
            public Connection openConnection(DataSourceConnection value, int timeoutSeconds) throws Exception {
                assertEquals(settings, value);
                connectionOpens.incrementAndGet();
                return DriverManager.getConnection(url, "sa", "");
            }
        };
        DataSourcePluginRegistry registry = new DataSourcePluginRegistry();
        Field plugins = DataSourcePluginRegistry.class.getDeclaredField("plugins");
        plugins.setAccessible(true);
        plugins.set(registry, Map.of("TEST_H2", plugin));

        TableId table = new TableId(null, "APP", "PRODUCTS");
        assertEquals(1, registry.catalogTables("TEST_H2", "{}", 10, null, "APP", "prod", 10).size());
        assertTrue(registry.catalogTable("TEST_H2", "{}", 10, table).isPresent());
        var columns = registry.catalogColumns("TEST_H2", "{}", 10, table);
        assertEquals(3, columns.size());
        assertEquals(2, columns.getFirst().primaryKeyPosition());
        assertEquals(1, columns.get(1).primaryKeyPosition());
        assertFalse(columns.get(2).primaryKey());
        var schema = registry.catalogTableSchema("TEST_H2", "{}", 10, table);
        assertEquals(3, schema.columnCount());
        assertEquals(java.util.List.of("ID", "TENANT"), schema.primaryKeys());
        assertTrue(connectionOpens.get() >= 4, "Datasource Plugin must open connections for Connector metadata");
    }
}
