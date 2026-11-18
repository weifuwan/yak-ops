package io.yak.ops.plugin.datasource.mysql.catalog;

import io.yak.ops.plugin.datasource.api.catalog.CatalogNamespace;
import io.yak.ops.plugin.datasource.api.catalog.CatalogObjectQuery;
import io.yak.ops.plugin.datasource.api.catalog.CatalogPath;
import io.yak.ops.plugin.datasource.api.catalog.DataSourceCatalog;
import io.yak.ops.plugin.datasource.api.catalog.NamespaceLevel;
import io.yak.ops.plugin.datasource.api.datasource.ConnectionProperty;

import java.sql.Connection;
import java.sql.PreparedStatement;
import java.sql.ResultSet;
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
    public void testListNamespacesOnlyReturnsConfiguredDatabase() throws Exception {
        Connection connection = org.mockito.Mockito.mock(Connection.class);
        org.mockito.Mockito.when(connection.getCatalog()).thenReturn("yak_e2e_target");

        List<CatalogNamespace> namespaces = new MySQLDataSourceCatalog().listNamespaces(
                connection, new CatalogPath(), NamespaceLevel.DATABASE);

        Assertions.assertEquals(1, namespaces.size());
        Assertions.assertEquals("yak_e2e_target", namespaces.get(0).getName());
        Assertions.assertEquals("yak_e2e_target", namespaces.get(0).getPath().getDatabase());
        org.mockito.Mockito.verify(connection, org.mockito.Mockito.never()).getMetaData();
    }

    @Test
    public void testListObjectsRejectsDatabaseOutsideDatasourceScope() throws Exception {
        Connection connection = org.mockito.Mockito.mock(Connection.class);
        org.mockito.Mockito.when(connection.getCatalog()).thenReturn("yak_e2e_target");
        CatalogPath path = new CatalogPath(null, "other_database", null, null, null);

        Assertions.assertThrows(IllegalArgumentException.class, () ->
                new MySQLDataSourceCatalog().listObjects(
                        connection, new CatalogObjectQuery(path, null, null, 0, 100)));
        org.mockito.Mockito.verify(connection, org.mockito.Mockito.never())
                .prepareStatement(org.mockito.ArgumentMatchers.anyString());
    }

    @Test
    public void testSearchObjectsIsRestrictedToConfiguredDatabase() throws Exception {
        Connection connection = org.mockito.Mockito.mock(Connection.class);
        PreparedStatement statement = org.mockito.Mockito.mock(PreparedStatement.class);
        ResultSet resultSet = org.mockito.Mockito.mock(ResultSet.class);
        org.mockito.Mockito.when(connection.getCatalog()).thenReturn("yak_e2e_target");
        org.mockito.Mockito.when(connection.prepareStatement(org.mockito.ArgumentMatchers.anyString()))
                .thenReturn(statement);
        org.mockito.Mockito.when(statement.executeQuery()).thenReturn(resultSet);
        org.mockito.Mockito.when(resultSet.next()).thenReturn(false);

        new MySQLDataSourceCatalog().searchObjects(connection, "student", 20);

        org.mockito.ArgumentCaptor<String> sql = org.mockito.ArgumentCaptor.forClass(String.class);
        org.mockito.Mockito.verify(connection).prepareStatement(sql.capture());
        Assertions.assertTrue(sql.getValue().contains("TABLE_SCHEMA = ?"));
        org.mockito.Mockito.verify(statement).setString(1, "yak_e2e_target");
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
