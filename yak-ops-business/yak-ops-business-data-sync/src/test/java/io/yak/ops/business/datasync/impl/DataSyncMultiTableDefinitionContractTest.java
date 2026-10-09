package io.yak.ops.business.datasync.impl;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;

import io.yak.ops.business.datasource.DataSourceService;
import io.yak.ops.business.datasync.exception.DataSyncErrorCode;
import io.yak.ops.business.datasync.exception.DataSyncException;
import io.yak.ops.common.bean.vo.datasource.DataSourceVO;
import io.yak.ops.common.bean.vo.datasync.DataSyncDefinitionSnapshotVO;
import io.yak.ops.common.context.WorkspaceContext;
import io.yak.ops.common.enums.datasync.DataSyncTaskStatus;
import io.yak.ops.common.enums.datasync.DataSyncType;
import io.yak.ops.common.enums.datasync.DataSyncWriteMode;
import io.yak.ops.dao.entity.datasync.DataSyncTableRouteEntity;
import io.yak.ops.dao.entity.datasync.DataSyncTaskEntity;
import io.yak.ops.dao.repository.datasync.DataSyncTaskRepository;
import java.lang.reflect.Field;
import java.lang.reflect.Method;
import java.lang.reflect.Proxy;
import java.util.List;
import java.util.Optional;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.Test;

class DataSyncMultiTableDefinitionContractTest {

    @AfterEach
    void clearWorkspace() {
        WorkspaceContext.clear();
    }

    @Test
    void shouldFreezeAllRoutesAndKeepFirstRouteCompatibilityProjection() throws Exception {
        DataSyncServiceImpl service = new DataSyncServiceImpl();
        inject(service, "dataSourceService", dataSourceService());

        DataSyncTaskEntity task = offlineTask();
        List<DataSyncTableRouteEntity> routes = List.of(
                route("route-1", 0, "users", "ods_users"),
                route("route-2", 1, "orders", "ods_orders"));

        Method method =
                DataSyncServiceImpl.class.getDeclaredMethod("definitionSnapshot", DataSyncTaskEntity.class, List.class);
        method.setAccessible(true);
        DataSyncDefinitionSnapshotVO snapshot =
                (DataSyncDefinitionSnapshotVO) method.invoke(service, task, routes);

        assertEquals(2, snapshot.getTableRoutes().size());
        assertEquals("route-1", snapshot.getTableRoutes().get(0).getRouteId());
        assertEquals("users", snapshot.getTableRoutes().get(0).getSource().getTable());
        assertEquals("ods_users", snapshot.getTableRoutes().get(0).getTarget().getTable());
        assertEquals("route-2", snapshot.getTableRoutes().get(1).getRouteId());
        assertEquals("orders", snapshot.getTableRoutes().get(1).getSource().getTable());
        assertEquals("ods_orders", snapshot.getTableRoutes().get(1).getTarget().getTable());

        assertEquals("users", snapshot.getSource().getTable());
        assertEquals("ods_users", snapshot.getTarget().getTable());
        assertEquals(snapshot.getTableRoutes().get(0).getRuntimeConfig(), snapshot.getRuntimeConfig());
    }

    @Test
    void shouldRejectRealtimeMultiRouteRun() throws Exception {
        DataSyncServiceImpl service = new DataSyncServiceImpl();
        DataSyncTaskEntity task = offlineTask();
        task.setSyncType(DataSyncType.REALTIME);
        DataSyncTestTableRouteRepository.inject(
                service,
                List.of(
                        route("route-1", 0, "users", "ods_users"),
                        route("route-2", 1, "orders", "ods_orders")));
        DataSyncTestTableExecutionRepository.inject(service);
        inject(service, "taskRepository", taskRepository(task));

        WorkspaceContext.bind("workspace-1");
        DataSyncException exception =
                assertThrows(DataSyncException.class, () -> service.runTask("task-1"));

        assertEquals(DataSyncErrorCode.INVALID_TASK, exception.getErrorCode());
        assertEquals(
                "同步任务参数不合法：REALTIME 当前只支持单 Route",
                exception.getUserMessage());
    }

    private DataSyncTaskEntity offlineTask() {
        DataSyncTaskEntity task = new DataSyncTaskEntity();
        task.setId("task-1");
        task.setWorkspaceId("workspace-1");
        task.setName("multi-table");
        task.setSyncType(DataSyncType.OFFLINE);
        task.setStatus(DataSyncTaskStatus.PUBLISHED);
        task.setWriteMode(DataSyncWriteMode.APPEND);
        task.setSourceDataSourceId("source");
        task.setTargetDataSourceId("target");
        task.setRuntimeConfig(
                "{\"fetchSize\":500,\"readBatchSize\":500,\"writeBatchSize\":500,\"splitSize\":null,"
                        + "\"sourceParallelism\":1,\"timeoutSeconds\":30}");
        task.setRetryPolicy("{\"maxAttempts\":1,\"backoffSeconds\":60}");
        task.setDefinitionVersion(6);
        return task;
    }

    private DataSyncTableRouteEntity route(
            String id, int order, String sourceTable, String targetTable) {
        DataSyncTableRouteEntity route = new DataSyncTableRouteEntity();
        route.setId(id);
        route.setWorkspaceId("workspace-1");
        route.setTaskId("task-1");
        route.setSourceDatabase("source_db");
        route.setSourceTable(sourceTable);
        route.setTargetDatabase("target_db");
        route.setTargetTable(targetTable);
        route.setAutoCreateTable(false);
        route.setSortOrder(order);
        return route;
    }

    private DataSyncTaskRepository taskRepository(DataSyncTaskEntity task) {
        return (DataSyncTaskRepository) Proxy.newProxyInstance(
                DataSyncTaskRepository.class.getClassLoader(),
                new Class<?>[] {DataSyncTaskRepository.class},
                (proxy, method, args) -> {
                    if ("queryById".equals(method.getName()) && args != null && args.length == 2) {
                        return Optional.of(task);
                    }
                    throw new UnsupportedOperationException(method.getName());
                });
    }

    private DataSourceService dataSourceService() {
        DataSourceVO source = dataSource("source", "source_db");
        DataSourceVO target = dataSource("target", "target_db");
        return (DataSourceService) Proxy.newProxyInstance(
                DataSourceService.class.getClassLoader(),
                new Class<?>[] {DataSourceService.class},
                (proxy, method, args) -> {
                    if ("queryDataSource".equals(method.getName())) {
                        return "source".equals(args[0]) ? source : target;
                    }
                    throw new UnsupportedOperationException(method.getName());
                });
    }

    private DataSourceVO dataSource(String id, String database) {
        DataSourceVO value = new DataSourceVO();
        value.setId(id);
        value.setName(id);
        value.setDbType("MYSQL");
        value.setDatabase(database);
        return value;
    }

    private void inject(Object target, String fieldName, Object value) throws Exception {
        Field field = DataSyncServiceImpl.class.getDeclaredField(fieldName);
        field.setAccessible(true);
        field.set(target, value);
    }
}
