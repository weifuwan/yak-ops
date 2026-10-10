package io.yak.ops.business.datasync.impl;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertThrows;

import io.yak.ops.business.datasource.DataSourceService;
import io.yak.ops.business.datasync.exception.DataSyncErrorCode;
import io.yak.ops.business.datasync.exception.DataSyncException;
import io.yak.ops.common.bean.dto.datasync.DataSyncRetryPolicyDTO;
import io.yak.ops.common.bean.dto.datasync.DataSyncRuntimeConfigDTO;
import io.yak.ops.common.bean.dto.datasync.DataSyncTaskDTO;
import io.yak.ops.common.bean.vo.datasource.DataSourceCatalogColumnVO;
import io.yak.ops.common.bean.vo.datasource.DataSourceCatalogTableVO;
import io.yak.ops.common.bean.vo.datasource.DataSourceVO;
import io.yak.ops.common.bean.vo.datasync.DataSyncTaskVO;
import io.yak.ops.common.context.WorkspaceContext;
import io.yak.ops.common.enums.datasync.DataSyncRetryPolicyMode;
import io.yak.ops.common.enums.datasync.DataSyncRuntimePolicy;
import io.yak.ops.common.enums.datasync.DataSyncTaskStatus;
import io.yak.ops.common.enums.datasync.DataSyncType;
import io.yak.ops.common.enums.datasync.DataSyncWriteMode;
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

class DataSyncTaskLifecycleContractTest {

    @AfterEach
    void clearWorkspace() {
        WorkspaceContext.clear();
    }

    @Test
    void shouldCreateTaskAsUnpublishedVersionOne() throws Exception {
        DataSyncServiceImpl service = new DataSyncServiceImpl();
        DataSyncTestTableRouteRepository.inject(service);
        DataSyncTestTableExecutionRepository.inject(service);
        AtomicReference<DataSyncTaskEntity> captured = new AtomicReference<>();
        inject(service, "taskRepository", taskRepository(null, captured));
        inject(service, "dataSourceService", dataSourceService());

        WorkspaceContext.bind("workspace-1");
        DataSyncTaskVO created = service.createTask(taskDto());

        assertEquals(DataSyncTaskStatus.UNPUBLISHED.name(), created.getStatus());
        assertEquals(1, created.getDefinitionVersion());
        assertEquals(DataSyncTaskStatus.UNPUBLISHED, captured.get().getStatus());
        assertNotNull(created.getTableRoutes());
        assertEquals(1, created.getTableRoutes().size());
        assertNotNull(created.getTableRoutes().get(0).getId());
        assertEquals("source_table", created.getTableRoutes().get(0).getSourceTable());
        assertEquals("target_table", created.getTableRoutes().get(0).getTargetTable());
        assertEquals(500, created.getRuntimeConfig().getFetchSize());
        assertEquals(500, created.getRuntimeConfig().getReadBatchSize());
        assertEquals(500, created.getRuntimeConfig().getWriteBatchSize());
        assertEquals(DataSyncRuntimePolicy.AUTO, created.getRuntimeConfig().getPolicy());
        assertEquals(1, created.getRuntimeConfig().getSourceParallelism());
        assertEquals(30, created.getRuntimeConfig().getTimeoutSeconds());
        assertEquals(DataSyncRetryPolicyMode.SMART, created.getRetryPolicy().getMode());
        assertEquals(3, created.getRetryPolicy().getMaxAttempts());
        assertEquals(15, created.getRetryPolicy().getBackoffSeconds());
    }

    @Test
    void shouldTreatExplicitOfflineRuntimeConfigWithoutPolicyAsFixed() throws Exception {
        DataSyncServiceImpl service = new DataSyncServiceImpl();
        DataSyncTestTableRouteRepository.inject(service);
        DataSyncTestTableExecutionRepository.inject(service);
        AtomicReference<DataSyncTaskEntity> captured = new AtomicReference<>();
        inject(service, "taskRepository", taskRepository(null, captured));
        inject(service, "dataSourceService", dataSourceService());

        DataSyncTaskDTO dto = taskDto();
        DataSyncRuntimeConfigDTO runtimeConfig = new DataSyncRuntimeConfigDTO();
        runtimeConfig.setSourceParallelism(4);
        dto.setRuntimeConfig(runtimeConfig);

        WorkspaceContext.bind("workspace-1");
        DataSyncTaskVO created = service.createTask(dto);

        assertEquals(DataSyncRuntimePolicy.FIXED, created.getRuntimeConfig().getPolicy());
        assertEquals(4, created.getRuntimeConfig().getSourceParallelism());
    }

    @Test
    void shouldCreateRealtimeTaskWithSystemRuntimeAndRetryDefaultsWhenOmitted() throws Exception {
        DataSyncServiceImpl service = new DataSyncServiceImpl();
        DataSyncTestTableRouteRepository.inject(service);
        DataSyncTestTableExecutionRepository.inject(service);
        AtomicReference<DataSyncTaskEntity> captured = new AtomicReference<>();
        DataSourceService dataSourceService = dataSourceService();
        inject(service, "taskRepository", taskRepository(null, captured));
        inject(service, "dataSourceService", dataSourceService);

        DataSyncTaskDTO dto = taskDto();
        dto.setSyncType(DataSyncType.REALTIME);

        WorkspaceContext.bind("workspace-1");
        DataSyncTaskVO created = service.createTask(dto);

        assertEquals(10, created.getRealtimeConfig().getCheckpointIntervalSeconds());
        assertEquals(64, created.getRealtimeConfig().getQueueCapacity());
        assertEquals(500, created.getRealtimeConfig().getPollBatchSize());
        assertEquals(500, created.getRealtimeConfig().getWriteBatchSize());
        assertEquals(30, created.getRealtimeConfig().getTimeoutSeconds());
        assertEquals(DataSyncRetryPolicyMode.SMART, created.getRetryPolicy().getMode());
        assertEquals(3, created.getRetryPolicy().getMaxAttempts());
        assertEquals(15, created.getRetryPolicy().getBackoffSeconds());
    }

    @Test
    void shouldRejectRunForUnpublishedTask() throws Exception {
        DataSyncServiceImpl service = new DataSyncServiceImpl();
        DataSyncTestTableRouteRepository.inject(service);
        DataSyncTestTableExecutionRepository.inject(service);
        inject(service, "taskRepository", taskRepository(task(DataSyncTaskStatus.UNPUBLISHED, 1), new AtomicReference<>()));

        WorkspaceContext.bind("workspace-1");
        DataSyncException exception =
                assertThrows(DataSyncException.class, () -> service.runTask("task-1"));

        assertEquals(DataSyncErrorCode.INVALID_TASK_STATUS, exception.getErrorCode());
    }

    @Test
    void shouldPublishWithoutChangingDefinitionVersion() throws Exception {
        DataSyncTaskEntity task = task(DataSyncTaskStatus.UNPUBLISHED, 3);
        AtomicReference<DataSyncTaskEntity> captured = new AtomicReference<>();
        DataSyncServiceImpl service = new DataSyncServiceImpl();
        DataSyncTestTableRouteRepository.inject(service, task);
        DataSyncTestTableExecutionRepository.inject(service);
        inject(service, "taskRepository", taskRepository(task, captured));
        inject(service, "dataSourceService", dataSourceService());

        WorkspaceContext.bind("workspace-1");
        DataSyncTaskVO published = service.publishTask("task-1");

        assertEquals(DataSyncTaskStatus.PUBLISHED.name(), published.getStatus());
        assertEquals(3, published.getDefinitionVersion());
        assertEquals(DataSyncTaskStatus.PUBLISHED, captured.get().getStatus());
    }

    @Test
    void shouldRejectUnpublishWhileInstanceIsActive() throws Exception {
        DataSyncServiceImpl service = new DataSyncServiceImpl();
        DataSyncTestTableRouteRepository.inject(service);
        DataSyncTestTableExecutionRepository.inject(service);
        inject(service, "taskRepository", taskRepository(task(DataSyncTaskStatus.PUBLISHED, 2), new AtomicReference<>()));
        inject(service, "instanceRepository", instanceRepository(true));

        WorkspaceContext.bind("workspace-1");
        DataSyncException exception =
                assertThrows(DataSyncException.class, () -> service.unpublishTask("task-1"));

        assertEquals(DataSyncErrorCode.ACTIVE_INSTANCE_EXISTS, exception.getErrorCode());
    }

    @Test
    void shouldKeepVersionForMetadataOnlyUpdate() throws Exception {
        DataSyncTaskEntity task = task(DataSyncTaskStatus.UNPUBLISHED, 3);
        AtomicReference<DataSyncTaskEntity> captured = new AtomicReference<>();
        DataSyncServiceImpl service = editableService(task, captured);
        DataSyncTaskDTO dto = taskDto();
        dto.setName("renamed-task");
        dto.setRemark("metadata-only");

        WorkspaceContext.bind("workspace-1");
        DataSyncTaskVO updated = service.updateTask("task-1", dto);

        assertEquals(3, updated.getDefinitionVersion());
        assertEquals("renamed-task", captured.get().getName());
        assertEquals("metadata-only", captured.get().getRemark());
        assertEquals(1, updated.getTableRoutes().size());
        assertEquals("task-1", updated.getTableRoutes().get(0).getId());
    }

    @Test
    void shouldPreserveExistingRuntimeAndRetryPolicyWhenUpdateOmitsThem() throws Exception {
        DataSyncTaskEntity task = task(DataSyncTaskStatus.UNPUBLISHED, 3);
        task.setRuntimeConfig(
                "{\"fetchSize\":1200,\"readBatchSize\":700,\"writeBatchSize\":300,\"splitSize\":250000,"
                        + "\"sourceParallelism\":4,\"timeoutSeconds\":45}");
        task.setRetryPolicy("{\"maxAttempts\":3,\"backoffSeconds\":90}");
        AtomicReference<DataSyncTaskEntity> captured = new AtomicReference<>();
        DataSyncServiceImpl service = editableService(task, captured);

        WorkspaceContext.bind("workspace-1");
        DataSyncTaskVO updated = service.updateTask("task-1", taskDto());

        assertEquals(3, updated.getDefinitionVersion());
        assertEquals(DataSyncRuntimePolicy.FIXED, updated.getRuntimeConfig().getPolicy());
        assertEquals(1200, updated.getRuntimeConfig().getFetchSize());
        assertEquals(700, updated.getRuntimeConfig().getReadBatchSize());
        assertEquals(300, updated.getRuntimeConfig().getWriteBatchSize());
        assertEquals(250000L, updated.getRuntimeConfig().getSplitSize());
        assertEquals(4, updated.getRuntimeConfig().getSourceParallelism());
        assertEquals(45, updated.getRuntimeConfig().getTimeoutSeconds());
        assertEquals(DataSyncRetryPolicyMode.FIXED, updated.getRetryPolicy().getMode());
        assertEquals(3, updated.getRetryPolicy().getMaxAttempts());
        assertEquals(90, updated.getRetryPolicy().getBackoffSeconds());
    }

    @Test
    void shouldIncrementVersionWhenExecutableDefinitionChanges() throws Exception {
        DataSyncTaskEntity task = task(DataSyncTaskStatus.UNPUBLISHED, 3);
        AtomicReference<DataSyncTaskEntity> captured = new AtomicReference<>();
        DataSyncServiceImpl service = editableService(task, captured);
        DataSyncTaskDTO dto = taskDto();
        DataSyncRuntimeConfigDTO runtimeConfig = new DataSyncRuntimeConfigDTO();
        runtimeConfig.setFetchSize(1000);
        dto.setRuntimeConfig(runtimeConfig);

        WorkspaceContext.bind("workspace-1");
        DataSyncTaskVO updated = service.updateTask("task-1", dto);

        assertEquals(4, updated.getDefinitionVersion());
        assertEquals(4, captured.get().getDefinitionVersion());
    }

    @Test
    void shouldTreatExplicitRetryPolicyWithoutModeAsFixed() throws Exception {
        DataSyncServiceImpl service = new DataSyncServiceImpl();
        DataSyncTestTableRouteRepository.inject(service);
        DataSyncTestTableExecutionRepository.inject(service);
        AtomicReference<DataSyncTaskEntity> captured = new AtomicReference<>();
        inject(service, "taskRepository", taskRepository(null, captured));
        inject(service, "dataSourceService", dataSourceService());

        DataSyncTaskDTO dto = taskDto();
        DataSyncRetryPolicyDTO retryPolicy = new DataSyncRetryPolicyDTO();
        retryPolicy.setMaxAttempts(4);
        retryPolicy.setBackoffSeconds(20);
        dto.setRetryPolicy(retryPolicy);

        WorkspaceContext.bind("workspace-1");
        DataSyncTaskVO created = service.createTask(dto);

        assertEquals(DataSyncRetryPolicyMode.FIXED, created.getRetryPolicy().getMode());
        assertEquals(4, created.getRetryPolicy().getMaxAttempts());
        assertEquals(20, created.getRetryPolicy().getBackoffSeconds());
    }

    @Test
    void shouldIncrementVersionWhenRetryPolicyChanges() throws Exception {
        DataSyncTaskEntity task = task(DataSyncTaskStatus.UNPUBLISHED, 3);
        task.setRetryPolicy("{\"maxAttempts\":1,\"backoffSeconds\":60}");
        AtomicReference<DataSyncTaskEntity> captured = new AtomicReference<>();
        DataSyncServiceImpl service = editableService(task, captured);
        DataSyncTaskDTO dto = taskDto();
        DataSyncRetryPolicyDTO retryPolicy = new DataSyncRetryPolicyDTO();
        retryPolicy.setMaxAttempts(3);
        dto.setRetryPolicy(retryPolicy);

        WorkspaceContext.bind("workspace-1");
        DataSyncTaskVO updated = service.updateTask("task-1", dto);

        assertEquals(4, updated.getDefinitionVersion());
        assertEquals(4, captured.get().getDefinitionVersion());
    }

    @Test
    void shouldIncrementVersionWhenRemovingLegacyAutoCreatePolicy() throws Exception {
        DataSyncTaskEntity task = task(DataSyncTaskStatus.UNPUBLISHED, 3);
        task.setAutoCreateTable(true);
        AtomicReference<DataSyncTaskEntity> captured = new AtomicReference<>();
        DataSyncServiceImpl service = editableService(task, captured);

        WorkspaceContext.bind("workspace-1");
        DataSyncTaskVO updated = service.updateTask("task-1", taskDto());

        assertEquals(4, updated.getDefinitionVersion());
        assertEquals(false, captured.get().getAutoCreateTable());
    }

    @Test
    void shouldRejectUpdateForPublishedTask() throws Exception {
        DataSyncServiceImpl service = new DataSyncServiceImpl();
        DataSyncTestTableRouteRepository.inject(service);
        DataSyncTestTableExecutionRepository.inject(service);
        inject(service, "taskRepository", taskRepository(task(DataSyncTaskStatus.PUBLISHED, 1), new AtomicReference<>()));

        WorkspaceContext.bind("workspace-1");
        DataSyncException exception =
                assertThrows(DataSyncException.class, () -> service.updateTask("task-1", taskDto()));

        assertEquals(DataSyncErrorCode.INVALID_TASK_STATUS, exception.getErrorCode());
    }

    @Test
    void shouldRejectSyncTypeMutation() throws Exception {
        DataSyncServiceImpl service = new DataSyncServiceImpl();
        DataSyncTestTableRouteRepository.inject(service);
        DataSyncTestTableExecutionRepository.inject(service);
        inject(service, "taskRepository", taskRepository(task(DataSyncTaskStatus.UNPUBLISHED, 1), new AtomicReference<>()));
        DataSyncTaskDTO dto = taskDto();
        dto.setSyncType(DataSyncType.REALTIME);

        WorkspaceContext.bind("workspace-1");
        DataSyncException exception =
                assertThrows(DataSyncException.class, () -> service.updateTask("task-1", dto));

        assertEquals(DataSyncErrorCode.INVALID_TASK, exception.getErrorCode());
    }

    @Test
    void shouldRejectDeleteForPublishedTask() throws Exception {
        DataSyncServiceImpl service = new DataSyncServiceImpl();
        DataSyncTestTableRouteRepository.inject(service);
        DataSyncTestTableExecutionRepository.inject(service);
        inject(service, "taskRepository", taskRepository(task(DataSyncTaskStatus.PUBLISHED, 1), new AtomicReference<>()));

        WorkspaceContext.bind("workspace-1");
        DataSyncException exception =
                assertThrows(DataSyncException.class, () -> service.deleteTask("task-1"));

        assertEquals(DataSyncErrorCode.INVALID_TASK_STATUS, exception.getErrorCode());
    }

    private DataSyncServiceImpl editableService(
            DataSyncTaskEntity task, AtomicReference<DataSyncTaskEntity> captured) throws Exception {
        DataSyncServiceImpl service = new DataSyncServiceImpl();
        DataSyncTestTableRouteRepository.inject(service, task);
        DataSyncTestTableExecutionRepository.inject(service);
        inject(service, "taskRepository", taskRepository(task, captured));
        inject(service, "dataSourceService", dataSourceService());
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

    private DataSyncInstanceRepository instanceRepository(boolean active) {
        return (DataSyncInstanceRepository) Proxy.newProxyInstance(
                DataSyncInstanceRepository.class.getClassLoader(),
                new Class<?>[] {DataSyncInstanceRepository.class},
                (proxy, method, args) -> {
                    if ("existsActiveByTask".equals(method.getName())) return active;
                    throw new UnsupportedOperationException(method.getName());
                });
    }

    private DataSourceService dataSourceService() {
        DataSourceVO source = dataSource("source", "source_db");
        DataSourceVO target = dataSource("target", "target_db");
        List<DataSourceCatalogColumnVO> columns = List.of(column("id"), column("name"));

        return (DataSourceService) Proxy.newProxyInstance(
                DataSourceService.class.getClassLoader(),
                new Class<?>[] {DataSourceService.class},
                (proxy, method, args) -> {
                    if ("queryDataSource".equals(method.getName())) {
                        return "source".equals(args[0]) ? source : target;
                    }
                    if ("queryCatalogTable".equals(method.getName())) {
                        DataSourceCatalogTableVO table = new DataSourceCatalogTableVO();
                        table.setName("source".equals(args[0]) ? "source_table" : "target_table");
                        table.setType("TABLE");
                        return table;
                    }
                    if ("findCatalogTable".equals(method.getName())) {
                        DataSourceCatalogTableVO table = new DataSourceCatalogTableVO();
                        table.setName("target_table");
                        table.setType("TABLE");
                        return Optional.of(table);
                    }
                    if ("queryCatalogColumns".equals(method.getName())) return columns;
                    throw new UnsupportedOperationException(method.getName());
                });
    }

    private DataSyncTaskEntity task(DataSyncTaskStatus status, int version) {
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
        task.setRuntimeConfig(
                "{\"fetchSize\":500,\"readBatchSize\":500,\"writeBatchSize\":500,\"splitSize\":null,"
                        + "\"sourceParallelism\":1,\"timeoutSeconds\":30}");
        task.setDefinitionVersion(version);
        return task;
    }

    private DataSyncTaskDTO taskDto() {
        DataSyncTaskDTO dto = new DataSyncTaskDTO();
        dto.setName("task");
        dto.setSyncType(DataSyncType.OFFLINE);
        dto.setWriteMode(DataSyncWriteMode.APPEND);
        dto.setSourceDataSourceId("source");
        dto.setSourceTable("source_table");
        dto.setTargetDataSourceId("target");
        dto.setTargetTable("target_table");
        return dto;
    }

    private DataSourceVO dataSource(String id, String database) {
        DataSourceVO value = new DataSourceVO();
        value.setId(id);
        value.setName(id);
        value.setDbType("MYSQL");
        value.setDatabase(database);
        return value;
    }

    private DataSourceCatalogColumnVO column(String name) {
        DataSourceCatalogColumnVO column = new DataSourceCatalogColumnVO();
        column.setName(name);
        column.setTypeName("VARCHAR");
        column.setJdbcType(Types.VARCHAR);
        column.setSize(128);
        column.setScale(0);
        column.setNullable(true);
        column.setOrdinalPosition("id".equals(name) ? 1 : 2);
        column.setPrimaryKey("id".equals(name));
        return column;
    }

    private void inject(Object target, String fieldName, Object value) throws Exception {
        Field field = target.getClass().getDeclaredField(fieldName);
        field.setAccessible(true);
        field.set(target, value);
    }
}
