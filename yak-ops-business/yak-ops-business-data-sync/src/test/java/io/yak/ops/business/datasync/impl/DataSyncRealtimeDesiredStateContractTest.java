package io.yak.ops.business.datasync.impl;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;

import io.yak.ops.business.datasync.exception.DataSyncErrorCode;
import io.yak.ops.business.datasync.exception.DataSyncException;
import io.yak.ops.business.datasource.DataSourceService;
import io.yak.ops.business.datasync.schema.catalog.SourceTableIntrospector;
import io.yak.ops.common.bean.vo.datasource.DataSourceCatalogColumnVO;
import io.yak.ops.common.bean.vo.datasource.DataSourceCatalogTableVO;
import io.yak.ops.common.bean.vo.datasource.DataSourceVO;
import io.yak.ops.common.bean.vo.datasync.DataSyncInstanceVO;
import io.yak.ops.common.context.WorkspaceContext;
import io.yak.ops.common.enums.datasync.DataSyncDesiredState;
import io.yak.ops.common.enums.datasync.DataSyncInstanceStatus;
import io.yak.ops.common.enums.datasync.DataSyncTaskStatus;
import io.yak.ops.common.enums.datasync.DataSyncType;
import io.yak.ops.dao.entity.datasync.DataSyncInstanceEntity;
import io.yak.ops.dao.entity.datasync.DataSyncTaskEntity;
import io.yak.ops.dao.repository.datasync.DataSyncAttemptRepository;
import io.yak.ops.dao.repository.datasync.DataSyncInstanceRepository;
import io.yak.ops.dao.repository.datasync.DataSyncScheduleRepository;
import io.yak.ops.dao.repository.datasync.DataSyncTaskRepository;
import java.lang.reflect.Field;
import java.lang.reflect.Proxy;
import java.sql.Types;
import java.util.List;
import java.util.Optional;
import java.util.concurrent.atomic.AtomicReference;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.Test;

class DataSyncRealtimeDesiredStateContractTest {

    @AfterEach
    void clearWorkspace() {
        WorkspaceContext.clear();
    }

    @Test
    void shouldRejectManualRealtimeExecutionWhenRuntimeUnavailable() throws Exception {
        DataSyncServiceImpl service = new DataSyncServiceImpl();
        DataSyncTaskEntity task = realtimeTask(DataSyncDesiredState.STOPPED);
        DataSyncTestTableRouteRepository.inject(service, task);
        DataSyncTestTableExecutionRepository.inject(service);
        AtomicReference<DataSyncTaskEntity> updatedTask = new AtomicReference<>();
        AtomicReference<DataSyncInstanceEntity> createdExecution = new AtomicReference<>();

        inject(service, "taskRepository", taskRepository(task, updatedTask, false));
        inject(service, "instanceRepository", instanceRepository(false, createdExecution));
        injectDataSourceService(service, dataSourceService());

        WorkspaceContext.bind("workspace-1");
        DataSyncException exception =
                assertThrows(DataSyncException.class, () -> service.runTask("task-1"));
        assertEquals(DataSyncErrorCode.EXECUTION_FAILED, exception.getErrorCode());
        assertEquals(null, createdExecution.get());
    }

    @Test
    void shouldNotCreateAutoRecoveryExecutionWhenRuntimeUnavailable() throws Exception {
        DataSyncServiceImpl service = new DataSyncServiceImpl();
        DataSyncTaskEntity task = realtimeTask(DataSyncDesiredState.RUNNING);
        DataSyncTestTableRouteRepository.inject(service, task);
        DataSyncTestTableExecutionRepository.inject(service);
        task.setDefinitionVersion(3);
        AtomicReference<DataSyncInstanceEntity> createdExecution = new AtomicReference<>();

        inject(service, "taskRepository", taskRepository(task, new AtomicReference<>(), true));
        inject(service, "instanceRepository", instanceRepository(false, createdExecution));
        injectDataSourceService(service, dataSourceService());

        service.restoreRealtimeDesiredState();

        assertEquals(null, createdExecution.get());
    }

    @Test
    void shouldSkipAutoRecoveryWhenActiveExecutionAlreadyExists() throws Exception {
        DataSyncServiceImpl service = new DataSyncServiceImpl();
        DataSyncTaskEntity task = realtimeTask(DataSyncDesiredState.RUNNING);
        DataSyncTestTableRouteRepository.inject(service, task);
        DataSyncTestTableExecutionRepository.inject(service);
        AtomicReference<DataSyncInstanceEntity> createdExecution = new AtomicReference<>();

        inject(service, "taskRepository", taskRepository(task, new AtomicReference<>(), true));
        inject(service, "instanceRepository", instanceRepository(true, createdExecution));

        service.restoreRealtimeDesiredState();

        assertEquals(null, createdExecution.get());
    }

    @Test
    void shouldStopDesiredStateWhenRetryWaitingExecutionIsCanceled() throws Exception {
        DataSyncServiceImpl service = new DataSyncServiceImpl();
        DataSyncTaskEntity task = realtimeTask(DataSyncDesiredState.RUNNING);
        DataSyncTestTableRouteRepository.inject(service, task);
        DataSyncTestTableExecutionRepository.inject(service);
        AtomicReference<DataSyncTaskEntity> updatedTask = new AtomicReference<>();
        DataSyncInstanceEntity execution = new DataSyncInstanceEntity();
        execution.setId("execution-1");
        execution.setWorkspaceId("workspace-1");
        execution.setTaskId("task-1");
        execution.setSyncType(DataSyncType.REALTIME);
        execution.setStatus(DataSyncInstanceStatus.RETRY_WAITING);

        inject(service, "taskRepository", taskRepository(task, updatedTask, false));
        inject(service, "instanceRepository", cancelableInstanceRepository(execution));
        inject(service, "attemptRepository", (DataSyncAttemptRepository) Proxy.newProxyInstance(
                DataSyncAttemptRepository.class.getClassLoader(),
                new Class<?>[] {DataSyncAttemptRepository.class},
                (proxy, method, args) -> {
                    if ("cancelActiveByExecution".equals(method.getName())) return 0;
                    throw new UnsupportedOperationException(method.getName());
                }));

        WorkspaceContext.bind("workspace-1");
        DataSyncInstanceVO canceled = service.cancelInstance("execution-1");

        assertEquals(DataSyncDesiredState.STOPPED, updatedTask.get().getDesiredState());
        assertEquals(DataSyncInstanceStatus.CANCELED.name(), canceled.getStatus());
    }

    @Test
    void shouldStopDesiredStateWhenRealtimeTaskIsUnpublished() throws Exception {
        DataSyncServiceImpl service = new DataSyncServiceImpl();
        DataSyncTaskEntity task = realtimeTask(DataSyncDesiredState.RUNNING);
        DataSyncTestTableRouteRepository.inject(service, task);
        DataSyncTestTableExecutionRepository.inject(service);
        AtomicReference<DataSyncTaskEntity> updatedTask = new AtomicReference<>();

        inject(service, "taskRepository", taskRepository(task, updatedTask, false));
        inject(service, "instanceRepository", instanceRepository(false, new AtomicReference<>()));
        inject(service, "scheduleRepository", emptyScheduleRepository());

        WorkspaceContext.bind("workspace-1");
        service.unpublishTask("task-1");

        assertEquals(DataSyncTaskStatus.UNPUBLISHED, updatedTask.get().getStatus());
        assertEquals(DataSyncDesiredState.STOPPED, updatedTask.get().getDesiredState());
    }

    private DataSyncTaskEntity realtimeTask(DataSyncDesiredState desiredState) {
        DataSyncTaskEntity task = new DataSyncTaskEntity();
        task.setId("task-1");
        task.setWorkspaceId("workspace-1");
        task.setName("realtime-task");
        task.setSyncType(DataSyncType.REALTIME);
        task.setStatus(DataSyncTaskStatus.PUBLISHED);
        task.setDesiredState(desiredState);
        task.setSourceDataSourceId("source");
        task.setSourceDatabase("source_db");
        task.setSourceTable("source_table");
        task.setTargetDataSourceId("target");
        task.setTargetDatabase("target_db");
        task.setTargetTable("target_table");
        task.setRuntimeConfig(
                "{\"checkpointIntervalSeconds\":10,\"queueCapacity\":64,\"pollBatchSize\":500,"
                        + "\"writeBatchSize\":500,\"timeoutSeconds\":30}");
        task.setRetryPolicy("{\"maxAttempts\":1,\"backoffSeconds\":60}");
        task.setDefinitionVersion(1);
        return task;
    }

    private DataSyncTaskRepository taskRepository(
            DataSyncTaskEntity task,
            AtomicReference<DataSyncTaskEntity> updated,
            boolean includeDesiredRunningQuery) {
        return (DataSyncTaskRepository) Proxy.newProxyInstance(
                DataSyncTaskRepository.class.getClassLoader(),
                new Class<?>[] {DataSyncTaskRepository.class},
                (proxy, method, args) -> {
                    if ("queryById".equals(method.getName())) return Optional.of(task);
                    if ("queryRealtimeDesiredRunning".equals(method.getName())) {
                        return includeDesiredRunningQuery ? List.of(task) : List.of();
                    }
                    if ("update".equals(method.getName())) {
                        DataSyncTaskEntity entity = (DataSyncTaskEntity) args[1];
                        updated.set(entity);
                        return entity;
                    }
                    throw new UnsupportedOperationException(method.getName());
                });
    }

    private DataSyncInstanceRepository instanceRepository(
            boolean active, AtomicReference<DataSyncInstanceEntity> created) {
        return (DataSyncInstanceRepository) Proxy.newProxyInstance(
                DataSyncInstanceRepository.class.getClassLoader(),
                new Class<?>[] {DataSyncInstanceRepository.class},
                (proxy, method, args) -> {
                    if ("existsActiveByTask".equals(method.getName())) return active;
                    if ("add".equals(method.getName())) {
                        DataSyncInstanceEntity entity = (DataSyncInstanceEntity) args[0];
                        created.set(entity);
                        return entity;
                    }
                    throw new UnsupportedOperationException(method.getName());
                });
    }

    private DataSyncInstanceRepository cancelableInstanceRepository(DataSyncInstanceEntity execution) {
        return (DataSyncInstanceRepository) Proxy.newProxyInstance(
                DataSyncInstanceRepository.class.getClassLoader(),
                new Class<?>[] {DataSyncInstanceRepository.class},
                (proxy, method, args) -> {
                    if ("queryById".equals(method.getName())) return Optional.of(execution);
                    if ("cancelExecution".equals(method.getName())) {
                        execution.setStatus(DataSyncInstanceStatus.CANCELED);
                        return true;
                    }
                    throw new UnsupportedOperationException(method.getName());
                });
    }

    private DataSyncScheduleRepository emptyScheduleRepository() {
        return (DataSyncScheduleRepository) Proxy.newProxyInstance(
                DataSyncScheduleRepository.class.getClassLoader(),
                new Class<?>[] {DataSyncScheduleRepository.class},
                (proxy, method, args) -> {
                    if ("queryByTask".equals(method.getName())) return Optional.empty();
                    throw new UnsupportedOperationException(method.getName());
                });
    }

    private DataSourceService dataSourceService() {
        DataSourceVO source = dataSource("source", "source_db");
        DataSourceVO target = dataSource("target", "target_db");
        DataSourceCatalogTableVO sourceTable = catalogTable("source_db", "source_table");
        DataSourceCatalogTableVO targetTable = catalogTable("target_db", "target_table");
        List<DataSourceCatalogColumnVO> columns = List.of(primaryKeyColumn("id"), column("name"));

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
                    if ("queryCatalogColumns".equals(method.getName())) return columns;
                    throw new UnsupportedOperationException(method.getName());
                });
    }

    private DataSourceCatalogTableVO catalogTable(String database, String table) {
        DataSourceCatalogTableVO value = new DataSourceCatalogTableVO();
        value.setDatabase(database);
        value.setName(table);
        value.setType("TABLE");
        return value;
    }

    private DataSourceVO dataSource(String id, String database) {
        DataSourceVO value = new DataSourceVO();
        value.setId(id);
        value.setName(id);
        value.setDbType("MYSQL");
        value.setDatabase(database);
        return value;
    }

    private DataSourceCatalogColumnVO primaryKeyColumn(String name) {
        DataSourceCatalogColumnVO column = column(name);
        column.setPrimaryKey(true);
        column.setNullable(false);
        return column;
    }

    private DataSourceCatalogColumnVO column(String name) {
        DataSourceCatalogColumnVO column = new DataSourceCatalogColumnVO();
        column.setName(name);
        column.setTypeName("BIGINT");
        column.setJdbcType(Types.BIGINT);
        column.setSize(19);
        column.setScale(0);
        column.setNullable(true);
        column.setOrdinalPosition("id".equals(name) ? 1 : 2);
        column.setPrimaryKey(false);
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
