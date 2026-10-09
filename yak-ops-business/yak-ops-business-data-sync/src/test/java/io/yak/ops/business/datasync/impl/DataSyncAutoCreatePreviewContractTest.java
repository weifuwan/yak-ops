package io.yak.ops.business.datasync.impl;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertTrue;

import io.yak.ops.business.datasource.DataSourceService;
import io.yak.ops.business.datasync.schema.target.TargetTablePlanner;
import io.yak.ops.common.bean.dto.datasync.DataSyncColumnMappingDTO;
import io.yak.ops.common.bean.dto.datasync.DataSyncMappingDTO;
import io.yak.ops.common.bean.dto.datasync.DataSyncMappingPreviewDTO;
import io.yak.ops.common.bean.vo.datasource.DataSourceCatalogColumnVO;
import io.yak.ops.common.bean.vo.datasource.DataSourceCatalogTableVO;
import io.yak.ops.common.bean.vo.datasource.DataSourceVO;
import io.yak.ops.common.bean.vo.datasync.DataSyncMappingPreviewVO;
import java.lang.reflect.Field;
import java.lang.reflect.Proxy;
import java.sql.Types;
import java.util.List;
import java.util.Optional;
import org.junit.jupiter.api.Test;

class DataSyncAutoCreatePreviewContractTest {

    @Test
    void shouldPreviewCreateTableWhenTargetMissingAndAutoCreateEnabled() throws Exception {
        DataSyncServiceImpl service = service();

        DataSyncMappingPreviewVO preview = service.previewMapping(request(true));

        assertFalse(preview.isTargetTableExists());
        assertTrue(preview.isAutoCreateTable());
        assertTrue(preview.isCompatible());
        assertNotNull(preview.getCreateTableSql());
        assertTrue(preview.getCreateTableSql().contains("CREATE TABLE"));
        assertEquals(List.of("BIGINT", "VARCHAR(100)"), preview.getMappings().stream()
                .map(mapping -> mapping.getTargetType())
                .toList());
    }

    @Test
    void shouldPreviewMappedTargetNamesAndOrderForAutoCreate() throws Exception {
        DataSyncServiceImpl service = service();
        DataSyncMappingPreviewDTO request = request(true);
        request.setMapping(mapping(
                columnMapping("name", "display_name"),
                columnMapping("id", "user_id")));

        DataSyncMappingPreviewVO preview = service.previewMapping(request);

        assertTrue(preview.isCompatible());
        assertEquals(
                List.of("name", "id"),
                preview.getMappings().stream().map(mapping -> mapping.getSourceName()).toList());
        assertEquals(
                List.of("display_name", "user_id"),
                preview.getMappings().stream().map(mapping -> mapping.getTargetName()).toList());
        assertTrue(preview.getCreateTableSql().contains("\"display_name\""));
        assertTrue(preview.getCreateTableSql().contains("\"user_id\""));
        assertTrue(preview.getCreateTableSql().contains("PRIMARY KEY (\"user_id\")"));
        assertEquals(4, preview.getDdlStatements().size());
        assertEquals(preview.getCreateTableSql(), preview.getDdlStatements().getFirst());
        assertEquals(
                "COMMENT ON TABLE \"target_table\" IS '来源用户表'",
                preview.getDdlStatements().get(1));
        assertEquals(
                "COMMENT ON COLUMN \"target_table\".\"display_name\" IS '用户名'",
                preview.getDdlStatements().get(2));
        assertEquals(
                "COMMENT ON COLUMN \"target_table\".\"user_id\" IS '用户ID'",
                preview.getDdlStatements().get(3));
    }

    @Test
    void shouldReportMissingTargetWhenAutoCreateDisabled() throws Exception {
        DataSyncServiceImpl service = service();

        DataSyncMappingPreviewVO preview = service.previewMapping(request(false));

        assertFalse(preview.isTargetTableExists());
        assertFalse(preview.isAutoCreateTable());
        assertFalse(preview.isCompatible());
        assertEquals(2, preview.getMappings().size());
    }

    private DataSyncServiceImpl service() throws Exception {
        DataSyncServiceImpl service = new DataSyncServiceImpl();
        DataSyncTestTableRouteRepository.inject(service);
        DataSyncTestTableExecutionRepository.inject(service);
        inject(service, "dataSourceService", dataSourceService());
        inject(service, "targetTablePlanner", new TargetTablePlanner());
        return service;
    }

    private DataSyncMappingPreviewDTO request(boolean autoCreateTable) {
        DataSyncMappingPreviewDTO dto = new DataSyncMappingPreviewDTO();
        dto.setSourceDataSourceId("source");
        dto.setSourceTable("source_table");
        dto.setTargetDataSourceId("target");
        dto.setTargetTable("target_table");
        dto.setAutoCreateTable(autoCreateTable);
        return dto;
    }

    private DataSyncMappingDTO mapping(DataSyncColumnMappingDTO... columns) {
        DataSyncMappingDTO mapping = new DataSyncMappingDTO();
        mapping.setColumns(List.of(columns));
        return mapping;
    }

    private DataSyncColumnMappingDTO columnMapping(String source, String target) {
        DataSyncColumnMappingDTO mapping = new DataSyncColumnMappingDTO();
        mapping.setSource(source);
        mapping.setTarget(target);
        return mapping;
    }

    private DataSourceService dataSourceService() {
        DataSourceVO source = dataSource("source", "MYSQL", "source_db");
        DataSourceVO target = dataSource("target", "POSTGRE_SQL", "target_db");
        DataSourceCatalogColumnVO id = column("id", Types.BIGINT, 19, false, 1, true, 1);
        id.setRemarks("用户ID");
        DataSourceCatalogColumnVO name = column("name", Types.VARCHAR, 100, true, 2, false, null);
        name.setRemarks("用户名");
        List<DataSourceCatalogColumnVO> sourceColumns = List.of(id, name);

        return (DataSourceService) Proxy.newProxyInstance(
                DataSourceService.class.getClassLoader(),
                new Class<?>[] {DataSourceService.class},
                (proxy, method, args) -> {
                    if ("queryDataSource".equals(method.getName())) {
                        return "source".equals(args[0]) ? source : target;
                    }
                    if ("queryCatalogColumns".equals(method.getName())) {
                        return "source".equals(args[0]) ? sourceColumns : List.of();
                    }
                    if ("findCatalogTable".equals(method.getName())) {
                        return Optional.empty();
                    }
                    if ("queryCatalogTable".equals(method.getName())) {
                        DataSourceCatalogTableVO table = new DataSourceCatalogTableVO();
                        table.setName("source_table");
                        table.setType("TABLE");
                        table.setRemarks("来源用户表");
                        return table;
                    }
                    throw new UnsupportedOperationException(method.getName());
                });
    }

    private DataSourceVO dataSource(String id, String type, String database) {
        DataSourceVO value = new DataSourceVO();
        value.setId(id);
        value.setName(id);
        value.setDbType(type);
        value.setDatabase(database);
        return value;
    }

    private DataSourceCatalogColumnVO column(
            String name,
            int jdbcType,
            Integer size,
            boolean nullable,
            int ordinal,
            boolean primaryKey,
            Integer primaryKeyPosition) {
        DataSourceCatalogColumnVO column = new DataSourceCatalogColumnVO();
        column.setName(name);
        column.setTypeName(jdbcType == Types.BIGINT ? "BIGINT" : "VARCHAR");
        column.setJdbcType(jdbcType);
        column.setSize(size);
        column.setScale(jdbcType == Types.BIGINT ? 0 : null);
        column.setNullable(nullable);
        column.setOrdinalPosition(ordinal);
        column.setPrimaryKey(primaryKey);
        column.setPrimaryKeyPosition(primaryKeyPosition);
        return column;
    }

    private void inject(Object target, String fieldName, Object value) throws Exception {
        Field field = DataSyncServiceImpl.class.getDeclaredField(fieldName);
        field.setAccessible(true);
        field.set(target, value);
    }
}
