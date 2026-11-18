package io.yak.ops.plugin.datasource.postgresql.catalog;

import io.yak.ops.plugin.datasource.api.catalog.CatalogNamespace;
import io.yak.ops.plugin.datasource.api.catalog.CatalogObjectQuery;
import io.yak.ops.plugin.datasource.api.catalog.CatalogPath;
import io.yak.ops.plugin.datasource.api.catalog.DataSourceCatalog;
import io.yak.ops.plugin.datasource.api.catalog.NamespaceLevel;
import io.yak.ops.plugin.datasource.api.datasource.ConnectionProperty;

import java.sql.Connection;
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
    public void testListNamespacesOnlyReturnsConfiguredDatabase() throws Exception {
        Connection connection = org.mockito.Mockito.mock(Connection.class);
        org.mockito.Mockito.when(connection.getCatalog()).thenReturn("chat2db_demo");

        List<CatalogNamespace> namespaces = new PostgreSQLDataSourceCatalog().listNamespaces(
                connection, new CatalogPath(), NamespaceLevel.DATABASE);

        Assertions.assertEquals(1, namespaces.size());
        Assertions.assertEquals("chat2db_demo", namespaces.get(0).getName());
        Assertions.assertEquals("chat2db_demo", namespaces.get(0).getPath().getDatabase());
        org.mockito.Mockito.verify(connection, org.mockito.Mockito.never())
                .prepareStatement(org.mockito.ArgumentMatchers.anyString());
    }

    @Test
    public void testListSchemasRejectsDatabaseOutsideDatasourceScope() throws Exception {
        Connection connection = org.mockito.Mockito.mock(Connection.class);
        org.mockito.Mockito.when(connection.getCatalog()).thenReturn("chat2db_demo");
        CatalogPath parentPath = new CatalogPath(null, "other_database", null, null, null);

        Assertions.assertThrows(IllegalArgumentException.class, () ->
                new PostgreSQLDataSourceCatalog().listNamespaces(
                        connection, parentPath, NamespaceLevel.SCHEMA));
        org.mockito.Mockito.verify(connection, org.mockito.Mockito.never())
                .prepareStatement(org.mockito.ArgumentMatchers.anyString());
    }

    @Test
    public void testListObjectsRejectsDatabaseOutsideDatasourceScope() throws Exception {
        Connection connection = org.mockito.Mockito.mock(Connection.class);
        org.mockito.Mockito.when(connection.getCatalog()).thenReturn("chat2db_demo");
        CatalogPath path = new CatalogPath(null, "other_database", "public", null, null);

        Assertions.assertThrows(IllegalArgumentException.class, () ->
                new PostgreSQLDataSourceCatalog().listObjects(
                        connection, new CatalogObjectQuery(path, null, null, 0, 100)));
        org.mockito.Mockito.verify(connection, org.mockito.Mockito.never())
                .prepareStatement(org.mockito.ArgumentMatchers.anyString());
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
