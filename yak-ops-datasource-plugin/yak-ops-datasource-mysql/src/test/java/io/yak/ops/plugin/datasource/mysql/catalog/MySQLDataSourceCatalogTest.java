package io.yak.ops.plugin.datasource.mysql.catalog;

import io.yak.ops.plugin.datasource.api.catalog.DataSourceCatalog;
import io.yak.ops.plugin.datasource.api.datasource.ConnectionProperty;

import java.util.List;

import org.junit.jupiter.api.Assertions;
import org.junit.jupiter.api.Test;

public class MySQLDataSourceCatalogTest {

    @Test
    public void testCatalogCapabilitiesExposeDatabaseWithoutSchema() {
        DataSourceCatalog catalog = new MySQLDataSourceCatalog();
        Assertions.assertEquals(java.util.Arrays.asList(
                io.yak.ops.plugin.datasource.api.catalog.NamespaceLevel.DATABASE),
                catalog.getCapabilities().getNamespaceLevels());
        Assertions.assertTrue(catalog.getCapabilities().supportsObject(
                io.yak.ops.plugin.datasource.api.catalog.CatalogObjectType.TABLE));
        Assertions.assertTrue(catalog.getCapabilities().supportsFeature(
                io.yak.ops.plugin.datasource.api.catalog.CatalogFeature.INDEXES));
    }

    @Test
    public void testDriverConnectionProperties() {
        DataSourceCatalog catalog = new MySQLDataSourceCatalog();
        List<ConnectionProperty> properties = catalog.getConnectionProperties();

        Assertions.assertFalse(properties.isEmpty());
        Assertions.assertTrue(properties.stream().anyMatch(p -> "connectTimeout".equals(p.getName())));
        Assertions.assertFalse(properties.stream().anyMatch(p -> "password".equalsIgnoreCase(p.getName())));
        Assertions.assertFalse(properties.stream().anyMatch(p -> "user".equalsIgnoreCase(p.getName())));
        Assertions.assertFalse(properties.stream().anyMatch(p -> "allowLoadLocalInfile".equalsIgnoreCase(p.getName())));
    }
}
