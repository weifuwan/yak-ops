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
import java.sql.Types;
import java.util.List;
import org.junit.jupiter.api.Test;

class DorisDataSourceCatalogTest {

    @Test
    void shouldExposeCatalogFromDatasourceProcessor() {
        assertNotNull(new DorisDataSourceProcessor().getCatalog());
    }

    @Test
    void shouldExposeDorisTableAndColumnMetadata() throws Exception {
        Connection connection = mock(Connection.class);
        DatabaseMetaData metadata = mock(DatabaseMetaData.class);
        ResultSet tables = mock(ResultSet.class);
        ResultSet columnKeys = mock(ResultSet.class);
        ResultSet columns = mock(ResultSet.class);
        when(connection.getCatalog()).thenReturn("warehouse");
        when(connection.getMetaData()).thenReturn(metadata);
        when(metadata.getTables(eq("warehouse"), isNull(), eq("dwd_order_detail"), any(String[].class)))
                .thenReturn(tables);
        when(tables.next()).thenReturn(true, false);
        when(tables.getString("TABLE_NAME")).thenReturn("dwd_order_detail");
        when(tables.getString("REMARKS")).thenReturn("订单明细");
        when(metadata.getPrimaryKeys("warehouse", null, "dwd_order_detail"))
                .thenReturn(columnKeys);
        when(columnKeys.next()).thenReturn(true, false);
        when(columnKeys.getString("COLUMN_NAME")).thenReturn("customer_id");
        when(metadata.getColumns("warehouse", null, "dwd_order_detail", null)).thenReturn(columns);
        when(columns.next()).thenReturn(true, false);
        when(columns.getString("TABLE_NAME")).thenReturn("dwd_order_detail");
        when(columns.getString("COLUMN_NAME")).thenReturn("customer_id");
        when(columns.getString("TYPE_NAME")).thenReturn("BIGINT");
        when(columns.getInt("DATA_TYPE")).thenReturn(Types.BIGINT);
        when(columns.getInt("COLUMN_SIZE")).thenReturn(19);
        when(columns.getInt("DECIMAL_DIGITS")).thenReturn(0);
        when(columns.getInt("NULLABLE")).thenReturn(DatabaseMetaData.columnNoNulls);
        when(columns.getInt("ORDINAL_POSITION")).thenReturn(1);
        when(columns.getString("REMARKS")).thenReturn("客户编号");

        DorisDataSourceCatalog catalog = new DorisDataSourceCatalog();
        CatalogObject table = catalog.getObject(
                connection, new CatalogPath(null, "warehouse", null, "dwd_order_detail", CatalogObjectType.TABLE));
        List<DataSourceCatalogColumn> result = catalog.listColumns(
                connection, "warehouse", "dwd_order_detail");

        assertNotNull(table);
        assertEquals("订单明细", table.getComment());
        assertEquals(1, result.size());
        assertEquals("customer_id", result.get(0).getName());
        assertEquals("BIGINT", result.get(0).getTypeName());
        assertEquals(Boolean.FALSE, result.get(0).getNullable());
        assertEquals(true, result.get(0).isPrimaryKey());
    }
}
