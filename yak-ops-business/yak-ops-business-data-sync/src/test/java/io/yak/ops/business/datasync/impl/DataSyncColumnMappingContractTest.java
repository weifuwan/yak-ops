package io.yak.ops.business.datasync.impl;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertThrows;

import io.yak.ops.business.datasource.DataSourceService;
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
import io.yak.ops.common.enums.datasync.DataSyncTaskStatus;
import io.yak.ops.common.enums.datasync.DataSyncType;
import io.yak.ops.common.enums.datasync.DataSyncWriteMode;
import io.yak.ops.common.util.JSONUtils;
import io.yak.ops.dao.entity.datasync.DataSyncInstanceEntity;
import io.yak.ops.dao.entity.datasync.DataSyncTaskEntity;
import io.yak.ops.dao.repository.datasync.DataSyncInstanceRepository;
import io.yak.ops.dao.repository.datasync.DataSyncTaskRepository;
import java.lang.reflect.Field;
import java.lang.reflect.Proxy;
import java.sql.Types;
import java.util.List;
import java.util.Optional;
import java.util.concurrent.atomic.AtomicReference;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.Test;

class DataSyncColumnMappingContractTest {

    @AfterEach
    void clearWorkspace() {
        WorkspaceContext.clear();
    }

    @Test
    void shouldPersistExplicitSameNameMapping() throws Exception {
        AtomicReference<DataSyncTaskEntity> captured = new AtomicReference<>();
        DataSyncServiceImpl service = service(null, captured);

        WorkspaceContext.bind("workspace-1");
        DataSyncTaskVO created = service.createTask(taskDto(mapping(columnMapping("id", "ID"), columnMapping("name", "name"))));

        assertNotNull(captured.get().getMappingConfig());
        assertNotNull(created.getMapping());
        assertEquals(2, created.getMapping().getColumns().size());
        assertEquals("id", created.getMapping().getColumns().get(0).getSource());
        assertEquals("ID", created.getMapping().getColumns().get(0).getTarget());
    }

    @Test
    void shouldIncrementDefinitionVersionWhenExplicitMappingChanges() throws Exception {
        DataSyncTaskEntity task = task(DataSyncTaskStatus.UNPUBLISHED, 3, null);
        AtomicReference<DataSyncTaskEntity> captured = new AtomicReference<>();
        DataSyncServiceImpl service = service(task, captured);

        WorkspaceContext.bind("workspace-1");
        DataSyncTaskVO updated = service.updateTask(
                "task-1", taskDto(mapping(columnMapping("id", "ID"), columnMapping("name", "name"))));

        assertEquals(4, updated.getDefinitionVersion());
        assertEquals(4, captured.get().getDefinitionVersion());
        assertNotNull(captured.get().getMappingConfig());
    }

    @Test
    void shouldRejectDuplicateSourceMapping() throws Exception {
        DataSyncServiceImpl service = service(null, new AtomicReference<>());

        WorkspaceContext.bind("workspace-1");
        DataSyncException exception = assertThrows(
                DataSyncException.class,
                () -> service.createTask(
                        taskDto(mapping(columnMapping("id", "ID"), columnMapping("ID", "name")))));

        assertEquals(DataSyncErrorCode.INVALID_TASK, exception.getErrorCode());
    }

    @Test
    void shouldPersistRenamedAndSubsetMapping() throws Exception {
        AtomicReference<DataSyncTaskEntity> captured = new AtomicReference<>();
        DataSyncServiceImpl service = service(null, captured, renamedDataSourceService());

        WorkspaceContext.bind("workspace-1");
        DataSyncTaskVO created =
                service.createTask(taskDto(mapping(columnMapping("id", "user_id"))));

        assertEquals(1, created.getMapping().getColumns().size());
        assertEquals("id", created.getMapping().getColumns().get(0).getSource());
        assertEquals("user_id", created.getMapping().getColumns().get(0).getTarget());
        assertNotNull(captured.get().getMappingConfig());
    }

    @Test
    void shouldRejectExecutionWithoutRuntimeAndPreserveDefinition() throws Exception {
        DataSyncMappingDTO mapping = mapping(columnMapping("id", "ID"), columnMapping("name", "name"));
        DataSyncTaskEntity task = task(DataSyncTaskStatus.PUBLISHED, 5, JSONUtils.toJson(mapping));
        AtomicReference<DataSyncInstanceEntity> captured = new AtomicReference<>();
        DataSyncServiceImpl service = service(task, new AtomicReference<>());
        DataSyncTestTableExecutionRepository.inject(service);
        inject(service, "instanceRepository", instanceRepository(captured));

        WorkspaceContext.bind("workspace-1");
        DataSyncException exception =
                assertThrows(DataSyncException.class, () -> service.runTask("task-1"));

        assertEquals(DataSyncErrorCode.EXECUTION_FAILED, exception.getErrorCode());
        assertEquals(null, captured.get());
        assertEquals(5, task.getDefinitionVersion());
    }

    private DataSyncServiceImpl service(
            DataSyncTaskEntity existing, AtomicReference<DataSyncTaskEntity> captured) throws Exception {
        return service(existing, captured, dataSourceService());
    }

    private DataSyncServiceImpl service(
            DataSyncTaskEntity existing,
            AtomicReference<DataSyncTaskEntity> captured,
            DataSourceService dataSourceService)
            throws Exception {
        DataSyncServiceImpl service = new DataSyncServiceImpl();
        DataSyncTestTableRouteRepository.inject(service, existing);
        DataSyncTestTableExecutionRepository.inject(service);
        inject(service, "taskRepository", taskRepository(existing, captured));
        inject(service, "dataSourceService", dataSourceService);
        return service;
    }

    private DataSyncTaskRepository taskRepository(
            DataSyncTaskEntity existing, AtomicReference<DataSyncTaskEntity> captured) {
        return (DataSyncTaskRepository) Proxy.newProxyInstance(
                DataSyncTaskRepository.class.getClassLoader(),
                new Class<?>[] {DataSyncTaskRepository.class},
                (proxy, method, args) -> {
                    if ("existsByName".equals(method.getName())) return false;
                    if ("queryById".equals(method.getName())) return Optional.ofNullable(existing);
                    if ("add".equals(method.getName()) || "update".equals(method.getName())) {
                        DataSyncTaskEntity entity =
                                (DataSyncTaskEntity) ("add".equals(method.getName()) ? args[0] : args[1]);
                        captured.set(entity);
                        return entity;
                    }
                    throw new UnsupportedOperationException(method.getName());
                });
    }

    private DataSyncInstanceRepository instanceRepository(AtomicReference<DataSyncInstanceEntity> captured) {
        return (DataSyncInstanceRepository) Proxy.newProxyInstance(
                DataSyncInstanceRepository.class.getClassLoader(),
                new Class<?>[] {DataSyncInstanceRepository.class},
                (proxy, method, args) -> {
                    if ("existsActiveByTask".equals(method.getName())) return false;
                    if ("add".equals(method.getName())) {
                        DataSyncInstanceEntity entity = (DataSyncInstanceEntity) args[0];
                        captured.set(entity);
                        return entity;
                    }
                    throw new UnsupportedOperationException(method.getName());
                });
    }

    private DataSourceService renamedDataSourceService() {
        DataSourceVO source = dataSource("source", "source_db");
        DataSourceVO target = dataSource("target", "target_db");
        DataSourceCatalogTableVO sourceTable = catalogTable("source_db", "source_table");
        DataSourceCatalogTableVO targetTable = catalogTable("target_db", "target_table");
        List<DataSourceCatalogColumnVO> sourceColumns =
                List.of(column("id", 1, true), column("name", 2, false));
        List<DataSourceCatalogColumnVO> targetColumns =
                List.of(column("user_id", 1, true), column("display_name", 2, false));

        return (DataSourceService) Proxy.newProxyInstance(
                DataSourceService.class.getClassLoader(),
                new Class<?>[] {DataSourceService.class},
                (proxy, method, args) -> {
                    if ("queryDataSource".equals(method.getName())) {
                        return "source".equals(args[0]) ? source : target;
                    }
                    if ("findCatalogTable".equals(method.getName())) {
                        return Optional.of("source".equals(args[0]) ? sourceTable : targetTable);
                    }
                    if ("queryCatalogTable".equals(method.getName())) {
                        return "source".equals(args[0]) ? sourceTable : targetTable;
                    }
                    if ("queryCatalogColumns".equals(method.getName())) {
                        return "source".equals(args[0]) ? sourceColumns : targetColumns;
                    }
                    throw new UnsupportedOperationException(method.getName());
                });
    }

    private DataSourceService dataSourceService() {
        DataSourceVO source = dataSource("source", "source_db");
        DataSourceVO target = dataSource("target", "target_db");
        DataSourceCatalogTableVO sourceTable = catalogTable("source_db", "source_table");
        DataSourceCatalogTableVO targetTable = catalogTable("target_db", "target_table");
        List<DataSourceCatalogColumnVO> sourceColumns =
                List.of(column("id", 1, true), column("name", 2, false));
        List<DataSourceCatalogColumnVO> targetColumns =
                List.of(column("ID", 1, true), column("name", 2, false));

        return (DataSourceService) Proxy.newProxyInstance(
                DataSourceService.class.getClassLoader(),
                new Class<?>[] {DataSourceService.class},
                (proxy, method, args) -> {
                    if ("queryDataSource".equals(method.getName())) {
                        return "source".equals(args[0]) ? source : target;
                    }
                    if ("findCatalogTable".equals(method.getName())) {
                        return Optional.of("source".equals(args[0]) ? sourceTable : targetTable);
                    }
                    if ("queryCatalogTable".equals(method.getName())) {
                        return "source".equals(args[0]) ? sourceTable : targetTable;
                    }
                    if ("queryCatalogColumns".equals(method.getName())) {
                        return "source".equals(args[0]) ? sourceColumns : targetColumns;
                    }
                    throw new UnsupportedOperationException(method.getName());
                });
    }

    private DataSyncTaskDTO taskDto(DataSyncMappingDTO mapping) {
        DataSyncTaskDTO dto = new DataSyncTaskDTO();
        dto.setName("task");
        dto.setSyncType(DataSyncType.OFFLINE);
        dto.setWriteMode(DataSyncWriteMode.APPEND);
        dto.setSourceDataSourceId("source");
        dto.setSourceTable("source_table");
        dto.setTargetDataSourceId("target");
        dto.setTargetTable("target_table");
        dto.setMapping(mapping);
        return dto;
    }

    private DataSyncTaskEntity task(DataSyncTaskStatus status, int version, String mappingConfig) {
        DataSyncTaskEntity task = new DataSyncTaskEntity();
        task.setId("task-1");
        task.setWorkspaceId("workspace-1");
        task.setName("task");
        task.setSyncType(DataSyncType.OFFLINE);
        task.setStatus(status);
        task.setWriteMode(DataSyncWriteMode.APPEND);
        task.setSourceDataSourceId("source");
        task.setSourceDatabase("source_db");
        task.setSourceTable("source_table");
        task.setTargetDataSourceId("target");
        task.setTargetDatabase("target_db");
        task.setTargetTable("target_table");
        task.setMappingConfig(mappingConfig);
        task.setRuntimeConfig(
                "{\"fetchSize\":500,\"readBatchSize\":500,\"writeBatchSize\":500,\"splitSize\":null,"
                        + "\"sourceParallelism\":1,\"timeoutSeconds\":30}");
        task.setRetryPolicy("{\"maxAttempts\":1,\"backoffSeconds\":60}");
        task.setDefinitionVersion(version);
        return task;
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

    private DataSourceVO dataSource(String id, String database) {
        DataSourceVO value = new DataSourceVO();
        value.setId(id);
        value.setName(id);
        value.setDbType("MYSQL");
        value.setDatabase(database);
        return value;
    }

    private DataSourceCatalogTableVO catalogTable(String database, String table) {
        DataSourceCatalogTableVO value = new DataSourceCatalogTableVO();
        value.setDatabase(database);
        value.setName(table);
        value.setType("TABLE");
        return value;
    }

    private DataSourceCatalogColumnVO column(String name, int ordinal, boolean primaryKey) {
        DataSourceCatalogColumnVO column = new DataSourceCatalogColumnVO();
        column.setName(name);
        column.setTypeName("VARCHAR");
        column.setJdbcType(Types.VARCHAR);
        column.setSize(128);
        column.setScale(0);
        column.setNullable(!primaryKey);
        column.setOrdinalPosition(ordinal);
        column.setPrimaryKey(primaryKey);
        return column;
    }

    private void inject(Object target, String fieldName, Object value) throws Exception {
        Field field = DataSyncServiceImpl.class.getDeclaredField(fieldName);
        field.setAccessible(true);
        field.set(target, value);
    }
}
