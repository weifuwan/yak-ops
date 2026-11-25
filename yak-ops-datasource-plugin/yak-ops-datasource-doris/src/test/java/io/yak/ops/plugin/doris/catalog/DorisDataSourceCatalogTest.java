package io.yak.ops.plugin.doris.catalog;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.ArgumentMatchers.isNull;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;

import io.yak.ops.plugin.datasource.api.catalog.CatalogObject;
import io.yak.ops.plugin.datasource.api.catalog.CatalogPath;
import io.yak.ops.plugin.datasource.api.catalog.CatalogObjectType;
import io.yak.ops.plugin.datasource.api.catalog.DataSourceCatalogColumn;
import io.yak.ops.plugin.doris.param.DorisDataSourceProcessor;
import java.sql.Connection;
import java.sql.DatabaseMetaData;
import java.sql.ResultSet;
import java.sql.ResultSetMetaData;
import java.sql.Statement;
import java.sql.Types;
import java.util.List;
import org.junit.jupiter.api.Test;

class DorisDataSourceCatalogTest {

    @Test
    void shouldExposeCatalogFromDatasourceProcessor() {
        assertNotNull(new DorisDataSourceProcessor().getCatalog());
    }

    @Test
    void shouldFindOlapTableThroughShowTablesAndReadColumns() throws Exception {
        Connection connection = mock(Connection.class);
        DatabaseMetaData metadata = mock(DatabaseMetaData.class);
        Statement statement = mock(Statement.class);
        ResultSet tables = mock(ResultSet.class);
        ResultSet columns = mock(ResultSet.class);
        when(connection.getCatalog()).thenReturn("warehouse");
        when(connection.getMetaData()).thenReturn(metadata);
        when(connection.createStatement()).thenReturn(statement);
        when(statement.executeQuery("SHOW TABLES FROM `warehouse`")).thenReturn(tables);
        when(tables.next()).thenReturn(true, false);
        when(tables.getString(1)).thenReturn("dwd_order_detail");
        when(statement.executeQuery(
                "SHOW FULL COLUMNS FROM `warehouse`.`dwd_order_detail`")).thenReturn(columns);
        when(columns.next()).thenReturn(true, false);
        when(columns.getString("Field")).thenReturn("customer_id");
        when(columns.getString("Type")).thenReturn("BIGINT");
        when(columns.getString("Null")).thenReturn("NO");
        when(columns.getString("Key")).thenReturn("true");
        when(columns.getString("Comment")).thenReturn("客户编号");

        DorisDataSourceCatalog catalog = new DorisDataSourceCatalog();
        CatalogObject table = catalog.getObject(
                connection, new CatalogPath(null, "warehouse", null, "dwd_order_detail", CatalogObjectType.TABLE));
        List<DataSourceCatalogColumn> result = catalog.listColumns(
                connection, "warehouse", "dwd_order_detail");

        assertNotNull(table);
        assertEquals("dwd_order_detail", table.getName());
        assertEquals(1, result.size());
        assertEquals("customer_id", result.get(0).getName());
        assertEquals("BIGINT", result.get(0).getTypeName());
        assertEquals(Boolean.FALSE, result.get(0).getNullable());
        assertEquals(true, result.get(0).isPrimaryKey());
    }

    @Test
    void shouldReturnNullWhenShowTablesDoesNotContainRequestedName() throws Exception {
        Connection connection = mock(Connection.class);
        Statement statement = mock(Statement.class);
        ResultSet tables = mock(ResultSet.class);
        when(connection.getCatalog()).thenReturn("warehouse");
        when(connection.createStatement()).thenReturn(statement);
        when(statement.executeQuery("SHOW TABLES FROM `warehouse`")).thenReturn(tables);
        when(tables.next()).thenReturn(true, false);
        when(tables.getString(1)).thenReturn("another_table");

        CatalogObject table = new DorisDataSourceCatalog().getObject(
                connection, new CatalogPath(null, "warehouse", null, "dwd_order_detail", CatalogObjectType.TABLE));

        org.junit.jupiter.api.Assertions.assertNull(table);
    }
}
