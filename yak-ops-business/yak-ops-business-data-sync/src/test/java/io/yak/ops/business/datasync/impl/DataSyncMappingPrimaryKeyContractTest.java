package io.yak.ops.business.datasync.impl;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertThrows;

import io.yak.ops.business.datasource.DataSourceService;
import io.yak.ops.business.datasync.schema.catalog.SourceTableIntrospector;
import io.yak.ops.business.datasync.exception.DataSyncErrorCode;
import io.yak.ops.business.datasync.exception.DataSyncException;
import io.yak.ops.common.bean.dto.datasync.DataSyncColumnMappingDTO;
import io.yak.ops.common.bean.dto.datasync.DataSyncMappingDTO;
import io.yak.ops.common.bean.dto.datasync.DataSyncTaskDTO;
import io.yak.ops.common.bean.vo.datasource.DataSourceCatalogColumnVO;
import io.yak.ops.common.bean.vo.datasource.DataSourceCatalogTableVO;
import io.yak.ops.common.bean.vo.datasource.DataSourceVO;
import io.yak.ops.common.bean.vo.datasync.DataSyncTaskVO;
import io.yak.ops.common.context.WorkspaceContext;
import io.yak.ops.common.enums.datasync.DataSyncType;
import io.yak.ops.common.enums.datasync.DataSyncWriteMode;
import io.yak.ops.dao.entity.datasync.DataSyncTaskEntity;
import io.yak.ops.dao.repository.datasync.DataSyncTaskRepository;
import java.lang.reflect.Field;
import java.lang.reflect.Proxy;
import java.sql.Types;
import java.util.List;
import java.util.Optional;
import java.util.concurrent.atomic.AtomicReference;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.Test;

class DataSyncMappingPrimaryKeyContractTest {

    @AfterEach
    void clearWorkspace() {
        WorkspaceContext.clear();
    }

    @Test
    void shouldAllowRealtimePrimaryKeyRename() throws Exception {
        AtomicReference<DataSyncTaskEntity> captured = new AtomicReference<>();
        DataSyncServiceImpl service = service(true, captured);
        DataSyncTaskDTO dto = task(DataSyncType.REALTIME, DataSyncWriteMode.APPEND);
        dto.setMapping(mapping(
                columnMapping("name", "display_name"),
                columnMapping("id", "user_id")));

        WorkspaceContext.bind("workspace-1");
        DataSyncTaskVO created = service.createTask(dto);

        assertNotNull(created.getMapping());
        assertEquals("user_id", created.getMapping().getColumns().get(1).getTarget());
        assertNotNull(captured.get());
    }

    @Test
    void shouldRejectRealtimeMappingThatDropsSourcePrimaryKey() throws Exception {
        DataSyncServiceImpl service = service(true, new AtomicReference<>());
        DataSyncTaskDTO dto = task(DataSyncType.REALTIME, DataSyncWriteMode.APPEND);
        dto.setMapping(mapping(columnMapping("name", "display_name")));

        WorkspaceContext.bind("workspace-1");
        DataSyncException exception =
                assertThrows(DataSyncException.class, () -> service.createTask(dto));

        assertEquals(DataSyncErrorCode.INVALID_TASK, exception.getErrorCode());
    }

    @Test
    void shouldAllowOfflineUpsertWithRenamedTargetPrimaryKey() throws Exception {
        AtomicReference<DataSyncTaskEntity> captured = new AtomicReference<>();
        DataSyncServiceImpl service = service(true, captured);
        DataSyncTaskDTO dto = task(DataSyncType.OFFLINE, DataSyncWriteMode.UPSERT);
        dto.setMapping(mapping(
                columnMapping("name", "display_name"),
                columnMapping("id", "user_id")));

        WorkspaceContext.bind("workspace-1");
        DataSyncTaskVO created = service.createTask(dto);

        assertEquals(DataSyncWriteMode.UPSERT.name(), created.getWriteMode());
        assertNotNull(captured.get());
    }

    @Test
    void shouldRejectUpsertAutoCreateWhenMappingDropsSourcePrimaryKey() throws Exception {
        DataSyncServiceImpl service = service(false, new AtomicReference<>());
        DataSyncTaskDTO dto = task(DataSyncType.OFFLINE, DataSyncWriteMode.UPSERT);
        dto.setAutoCreateTable(true);
        dto.setMapping(mapping(columnMapping("name", "display_name")));

        WorkspaceContext.bind("workspace-1");
        DataSyncException exception =
                assertThrows(DataSyncException.class, () -> service.createTask(dto));

        assertEquals(DataSyncErrorCode.INVALID_TASK, exception.getErrorCode());
    }

    private DataSyncServiceImpl service(boolean targetExists, AtomicReference<DataSyncTaskEntity> captured)
            throws Exception {
        DataSyncServiceImpl service = new DataSyncServiceImpl();
        DataSyncTestTableRouteRepository.inject(service);
        DataSyncTestTableExecutionRepository.inject(service);
        inject(service, "taskRepository", taskRepository(captured));
        injectDataSourceService(service, dataSourceService(targetExists));
        return service;
    }

    private DataSyncTaskRepository taskRepository(AtomicReference<DataSyncTaskEntity> captured) {
        return (DataSyncTaskRepository) Proxy.newProxyInstance(
                DataSyncTaskRepository.class.getClassLoader(),
                new Class<?>[] {DataSyncTaskRepository.class},
                (proxy, method, args) -> {
                    if ("existsByName".equals(method.getName())) return false;
                    if ("add".equals(method.getName())) {
                        DataSyncTaskEntity entity = (DataSyncTaskEntity) args[0];
                        captured.set(entity);
                        return entity;
                    }
                    throw new UnsupportedOperationException(method.getName());
                });
    }

    private DataSourceService dataSourceService(boolean targetExists) {
        DataSourceVO source = dataSource("source", "MYSQL", "source_db");
        DataSourceVO target = dataSource("target", "POSTGRE_SQL", "target_db");
        DataSourceCatalogTableVO sourceTable = table("source_table");
        DataSourceCatalogTableVO targetTable = table("target_table");
        List<DataSourceCatalogColumnVO> sourceColumns = List.of(
                column("id", Types.BIGINT, 19, false, 1, true, 1),
                column("name", Types.VARCHAR, 100, true, 2, false, null));
        List<DataSourceCatalogColumnVO> targetColumns = List.of(
                column("user_id", Types.BIGINT, 19, false, 1, true, 1),
                column("display_name", Types.VARCHAR, 100, true, 2, false, null));

        return (DataSourceService) Proxy.newProxyInstance(
                DataSourceService.class.getClassLoader(),
                new Class<?>[] {DataSourceService.class},
                (proxy, method, args) -> {
                    String id = args != null && args.length > 0 ? String.valueOf(args[0]) : null;
                    if ("queryDataSource".equals(method.getName())) {
                        return "source".equals(id) ? source : target;
                    }
                    if ("queryCatalogTable".equals(method.getName())) {
                        return "source".equals(id) ? sourceTable : targetTable;
                    }
                    if ("queryCatalogColumns".equals(method.getName())) {
                        return "source".equals(id) ? sourceColumns : targetColumns;
                    }
                    if ("findCatalogTable".equals(method.getName())) {
                        return targetExists ? Optional.of(targetTable) : Optional.empty();
                    }
                    throw new UnsupportedOperationException(method.getName());
                });
    }

    private DataSyncTaskDTO task(DataSyncType syncType, DataSyncWriteMode writeMode) {
        DataSyncTaskDTO dto = new DataSyncTaskDTO();
        dto.setName("mapping-key-task");
        dto.setSyncType(syncType);
        dto.setWriteMode(writeMode);
        dto.setSourceDataSourceId("source");
        dto.setSourceTable("source_table");
        dto.setTargetDataSourceId("target");
        dto.setTargetTable("target_table");
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

    private DataSourceVO dataSource(String id, String type, String database) {
        DataSourceVO value = new DataSourceVO();
        value.setId(id);
        value.setName(id);
        value.setDbType(type);
        value.setDatabase(database);
        return value;
    }

    private DataSourceCatalogTableVO table(String name) {
        DataSourceCatalogTableVO value = new DataSourceCatalogTableVO();
        value.setName(name);
        value.setType("TABLE");
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

    private void injectDataSourceService(DataSyncServiceImpl service, DataSourceService dataSourceService)
            throws Exception {
        inject(service, "dataSourceService", dataSourceService);
        SourceTableIntrospector sourceTableIntrospector = new SourceTableIntrospector();
        Field field = SourceTableIntrospector.class.getDeclaredField("dataSourceService");
        field.setAccessible(true);
        field.set(sourceTableIntrospector, dataSourceService);
        inject(service, "sourceTableIntrospector", sourceTableIntrospector);
    }

    private void inject(Object target, String fieldName, Object value) throws Exception {
        Field field = DataSyncServiceImpl.class.getDeclaredField(fieldName);
        field.setAccessible(true);
        field.set(target, value);
    }
}
