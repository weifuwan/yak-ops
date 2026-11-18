package io.yak.ops.plugin.datasource.postgresql.catalog;

import io.yak.ops.plugin.datasource.api.catalog.DataSourceCatalog;
import io.yak.ops.plugin.datasource.api.datasource.ConnectionProperty;

import java.util.List;

import org.junit.jupiter.api.Assertions;
import org.junit.jupiter.api.Test;

public class PostgreSQLDataSourceCatalogTest {

    @Test
    public void testCatalogCapabilitiesExposeDatabaseAndSchema() {
        DataSourceCatalog catalog = new PostgreSQLDataSourceCatalog();
        Assertions.assertEquals(java.util.Arrays.asList(
                io.yak.ops.plugin.datasource.api.catalog.NamespaceLevel.DATABASE,
                io.yak.ops.plugin.datasource.api.catalog.NamespaceLevel.SCHEMA),
                catalog.getCapabilities().getNamespaceLevels());
        Assertions.assertTrue(catalog.getCapabilities().supportsObject(
                io.yak.ops.plugin.datasource.api.catalog.CatalogObjectType.MATERIALIZED_VIEW));
        Assertions.assertTrue(catalog.getCapabilities().supportsFeature(
                io.yak.ops.plugin.datasource.api.catalog.CatalogFeature.COLUMNS));
    }

    @Test
    public void testDriverConnectionProperties() {
        DataSourceCatalog catalog = new PostgreSQLDataSourceCatalog();
        List<ConnectionProperty> properties = catalog.getConnectionProperties();

        Assertions.assertFalse(properties.isEmpty());
        Assertions.assertTrue(properties.stream().anyMatch(p -> "connectTimeout".equals(p.getName())));
        Assertions.assertFalse(properties.stream().anyMatch(p -> "password".equalsIgnoreCase(p.getName())));
        Assertions.assertFalse(properties.stream().anyMatch(p -> "user".equalsIgnoreCase(p.getName())));
    }
}
