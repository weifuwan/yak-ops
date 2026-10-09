package io.yak.ops.business.datasync.schema.catalog;

import io.yak.ops.business.datasync.schema.LogicalColumn;
import io.yak.ops.business.datasync.schema.LogicalTable;
import static org.junit.jupiter.api.Assertions.assertEquals;

import io.yak.ops.business.datasource.DataSourceService;
import io.yak.ops.common.bean.dto.datasource.DataSourceTablePathDTO;
import io.yak.ops.common.bean.vo.datasource.DataSourceCatalogColumnVO;
import io.yak.ops.common.bean.vo.datasource.DataSourceCatalogTableVO;
import java.lang.reflect.Field;
import java.lang.reflect.Proxy;
import java.sql.Types;
import java.util.List;
import java.util.concurrent.atomic.AtomicReference;
import org.junit.jupiter.api.Test;

class SourceTableIntrospectorTest {

    @Test
    void shouldReadExactTableAndColumnsThroughDatasourceService() throws Exception {
        AtomicReference<DataSourceTablePathDTO> tablePath = new AtomicReference<>();
        AtomicReference<DataSourceTablePathDTO> columnsPath = new AtomicReference<>();

        DataSourceService dataSourceService = (DataSourceService) Proxy.newProxyInstance(
                DataSourceService.class.getClassLoader(),
                new Class<?>[] {DataSourceService.class},
                (proxy, method, args) -> {
                    if ("queryCatalogTable".equals(method.getName())) {
                        tablePath.set((DataSourceTablePathDTO) args[1]);
                        DataSourceCatalogTableVO table = new DataSourceCatalogTableVO();
                        table.setName("orders");
                        table.setRemarks("订单表");
                        return table;
                    }
                    if ("queryCatalogColumns".equals(method.getName())) {
                        columnsPath.set((DataSourceTablePathDTO) args[1]);
                        DataSourceCatalogColumnVO column = new DataSourceCatalogColumnVO();
                        column.setName("id");
                        column.setTypeName("BIGINT");
                        column.setJdbcType(Types.BIGINT);
                        column.setSize(19);
                        column.setScale(0);
                        column.setNullable(false);
                        column.setOrdinalPosition(1);
                        column.setPrimaryKey(true);
                        column.setPrimaryKeyPosition(1);
                        return List.of(column);
                    }
                    throw new UnsupportedOperationException(method.getName());
                });

        SourceTableIntrospector introspector = new SourceTableIntrospector();
        inject(introspector, dataSourceService);

        LogicalTable result = introspector.introspect("source-1", " source_db ", " public ", " orders ");

        assertEquals("orders", result.name());
        assertEquals("订单表", result.comment());
        assertEquals(List.of("id"), result.primaryKeys());

        assertEquals("source_db", tablePath.get().getDatabase());
        assertEquals("public", tablePath.get().getSchema());
        assertEquals("orders", tablePath.get().getTable());
        assertEquals("orders", columnsPath.get().getTable());
    }

    private void inject(SourceTableIntrospector introspector, DataSourceService dataSourceService) throws Exception {
        Field field = SourceTableIntrospector.class.getDeclaredField("dataSourceService");
        field.setAccessible(true);
        field.set(introspector, dataSourceService);
    }
}
