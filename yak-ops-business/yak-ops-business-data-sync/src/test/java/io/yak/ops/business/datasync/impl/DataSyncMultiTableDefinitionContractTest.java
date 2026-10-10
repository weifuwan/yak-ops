package io.yak.ops.business.datasync.impl;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;

import io.yak.ops.business.datasync.exception.DataSyncErrorCode;
import io.yak.ops.business.datasync.exception.DataSyncException;
import io.yak.ops.common.context.WorkspaceContext;
import io.yak.ops.common.enums.datasync.DataSyncTaskStatus;
import io.yak.ops.common.enums.datasync.DataSyncType;
import io.yak.ops.common.enums.datasync.DataSyncWriteMode;
import io.yak.ops.dao.entity.datasync.DataSyncTableRouteEntity;
import io.yak.ops.dao.entity.datasync.SyncDefinitionEntity;
import io.yak.ops.dao.repository.datasync.SyncDefinitionRepository;
import java.lang.reflect.Field;
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
    void shouldRejectHistoricalMultiRouteRun() throws Exception {
        DataSyncInstanceServiceImpl service = new DataSyncInstanceServiceImpl();
        SyncDefinitionEntity task = offlineTask();
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
                "同步任务参数不合法：历史多表任务暂不支持编辑或运行",
                exception.getUserMessage());
    }

    private SyncDefinitionEntity offlineTask() {
        SyncDefinitionEntity task = new SyncDefinitionEntity();
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

    private SyncDefinitionRepository taskRepository(SyncDefinitionEntity task) {
        return (SyncDefinitionRepository) Proxy.newProxyInstance(
                SyncDefinitionRepository.class.getClassLoader(),
                new Class<?>[] {SyncDefinitionRepository.class},
                (proxy, method, args) -> {
                    if ("queryById".equals(method.getName()) && args != null && args.length == 2) {
                        return Optional.of(task);
                    }
                    throw new UnsupportedOperationException(method.getName());
                });
    }

    private void inject(Object target, String fieldName, Object value) throws Exception {
        DataSyncTestServices.inject(target, fieldName, value);
    }
}
