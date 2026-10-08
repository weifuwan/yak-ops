package io.yak.ops.business.datasync.execution.executor;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

import io.yak.ops.business.datasync.execution.lifecycle.DataSyncTableAttemptLifecycle;
import io.yak.ops.common.bean.vo.datasync.DataSyncDefinitionSnapshotVO;
import io.yak.ops.common.bean.vo.datasync.DataSyncEndpointSnapshotVO;
import io.yak.ops.common.bean.vo.datasync.DataSyncRetryPolicyVO;
import io.yak.ops.common.bean.vo.datasync.DataSyncTableRouteSnapshotVO;
import io.yak.ops.common.enums.datasync.DataSyncAttemptStatus;
import io.yak.ops.common.enums.datasync.DataSyncInstanceStatus;
import io.yak.ops.common.enums.datasync.DataSyncRetryPolicyMode;
import io.yak.ops.common.enums.datasync.DataSyncTableExecutionStatus;
import io.yak.ops.dao.entity.datasync.DataSyncInstanceEntity;
import io.yak.ops.dao.entity.datasync.DataSyncTableAttemptEntity;
import io.yak.ops.dao.entity.datasync.DataSyncTableExecutionEntity;
import io.yak.ops.dao.repository.datasync.DataSyncInstanceRepository;
import io.yak.ops.dao.repository.datasync.DataSyncTableExecutionRepository;
import io.yak.ops.flow.runtime.ExecutionStatus;
import java.lang.reflect.Field;
import java.lang.reflect.Proxy;
import java.net.SocketTimeoutException;
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.function.BiConsumer;
import org.junit.jupiter.api.Test;

class MultiTableOfflineExecutorContractTest {

    @Test
    void failedAppendTableDoesNotBlockOtherTablesOrReplaySuccessfulTables() throws Exception {
        Fixture f = new Fixture();
        f.runner = (table, attempt) -> "orders".equals(table)
                ? new RouteExecutionOutcome(
                        ExecutionStatus.FAILED, 5, 2, new SocketTimeoutException("timeout"), true)
                : new RouteExecutionOutcome(
                        ExecutionStatus.SUCCEEDED, "users".equals(table) ? 10 : 3,
                        "users".equals(table) ? 10 : 3, null, true);
        f.execute();

        assertEquals(DataSyncInstanceStatus.FAILED, f.root.getStatus());
        assertEquals(DataSyncTableExecutionStatus.SUCCEEDED, f.tables.get("users").getStatus());
        assertEquals(DataSyncTableExecutionStatus.FAILED, f.tables.get("orders").getStatus());
        assertEquals(DataSyncTableExecutionStatus.SUCCEEDED, f.tables.get("products").getStatus());
        assertEquals(1, f.calls.get("users"));
        assertEquals(1, f.calls.get("orders"));
        assertEquals(1, f.calls.get("products"));
        assertEquals(18L, f.root.getReadRows());
        assertEquals(15L, f.root.getWriteRows());
    }

    @Test
    void smartRetryReplaysOnlyTransientlyFailedTableAndUsesLastAttemptMetrics() throws Exception {
        Fixture f = new Fixture();
        f.runner = (table, attempt) -> {
            if ("orders".equals(table) && attempt == 1) {
                return new RouteExecutionOutcome(
                        ExecutionStatus.FAILED, 0, 0, new SocketTimeoutException("connect timeout"), false);
            }
            long rows = "users".equals(table) ? 10 : "orders".equals(table) ? 7 : 3;
            return new RouteExecutionOutcome(ExecutionStatus.SUCCEEDED, rows, rows, null, true);
        };
        f.execute();

        assertEquals(DataSyncInstanceStatus.SUCCEEDED, f.root.getStatus());
        assertEquals(1, f.calls.get("users"));
        assertEquals(2, f.calls.get("orders"));
        assertEquals(1, f.calls.get("products"));
        assertEquals(20L, f.root.getReadRows());
        assertEquals(20L, f.root.getWriteRows());
        assertEquals(2, f.attempts.get("orders").size());
        assertEquals(DataSyncAttemptStatus.FAILED, f.attempts.get("orders").get(0).getStatus());
        assertEquals(DataSyncAttemptStatus.SUCCEEDED, f.attempts.get("orders").get(1).getStatus());
        assertEquals(2, f.tables.get("orders").getCurrentAttempt());
        assertTrue(f.tables.values().stream().allMatch(
                table -> table.getStatus() == DataSyncTableExecutionStatus.SUCCEEDED));
    }

    @Test
    void canceledRootDoesNotStartAnyRoute() throws Exception {
        Fixture f = new Fixture();
        f.root.setStatus(DataSyncInstanceStatus.CANCELED);
        f.execute();

        assertTrue(f.calls.isEmpty());
        assertTrue(f.tables.values().stream().allMatch(
                table -> table.getStatus() == DataSyncTableExecutionStatus.CANCELED));
        assertEquals(DataSyncInstanceStatus.CANCELED, f.root.getStatus());
    }

    private interface Scenario {
        RouteExecutionOutcome run(String table, int attempt);
    }

    private static final class Fixture {
        private final DataSyncInstanceEntity root = new DataSyncInstanceEntity();
        private final Map<String, DataSyncTableExecutionEntity> tables = new LinkedHashMap<>();
        private final Map<String, List<DataSyncTableAttemptEntity>> attempts = new LinkedHashMap<>();
        private final Map<String, Integer> calls = new LinkedHashMap<>();
        private Scenario runner = (table, attempt) ->
                new RouteExecutionOutcome(ExecutionStatus.SUCCEEDED, 1, 1, null, true);

        Fixture() {
            root.setId("root-1");
            root.setWorkspaceId("ws-1");
            root.setStatus(DataSyncInstanceStatus.PENDING);
            root.setReadRows(0L);
            root.setWriteRows(0L);
            String[] names = {"users", "orders", "products"};
            for (int i = 0; i < names.length; i++) {
                DataSyncTableExecutionEntity table = new DataSyncTableExecutionEntity();
                table.setId("table-" + names[i]);
                table.setWorkspaceId("ws-1");
                table.setExecutionId(root.getId());
                table.setRouteId("route-" + names[i]);
                table.setRouteOrder(i);
                table.setCurrentAttempt(0);
                table.setStatus(DataSyncTableExecutionStatus.PLANNED);
                table.setReadRows(0L);
                table.setWriteRows(0L);
                tables.put(names[i], table);
                attempts.put(names[i], new ArrayList<>());
            }
        }

        void execute() throws Exception {
            MultiTableOfflineExecutor executor = new MultiTableOfflineExecutor() {
                @Override
                protected RouteExecutionOutcome executeAttemptRuntime(
                        RouteAttemptRuntimeContext context, BiConsumer<Long, Long> metrics) {
                    String name = context.snapshot().getSource().getTable();
                    calls.merge(name, 1, Integer::sum);
                    RouteExecutionOutcome result = runner.run(name, context.attemptNo());
                    metrics.accept(result.readRows(), result.writeRows());
                    return result;
                }
            };
            inject(executor, "tableAttemptLifecycle", lifecycle());
            inject(executor, "instanceRepository", rootRepository());
            inject(executor, "tableExecutionRepository", tableRepository());
            executor.executeInline("ws-1", root.getId(), snapshot());
        }

        private DataSyncDefinitionSnapshotVO snapshot() {
            DataSyncDefinitionSnapshotVO snapshot = new DataSyncDefinitionSnapshotVO();
            snapshot.setTaskId("task-1");
            snapshot.setTaskVersion(1);
            snapshot.setSyncType("OFFLINE");
            snapshot.setWriteMode("APPEND");
            DataSyncRetryPolicyVO retry = new DataSyncRetryPolicyVO();
            retry.setMode(DataSyncRetryPolicyMode.SMART);
            retry.setMaxAttempts(2);
            retry.setBackoffSeconds(0);
            snapshot.setRetryPolicy(retry);
            List<DataSyncTableRouteSnapshotVO> routes = new ArrayList<>();
            for (Map.Entry<String, DataSyncTableExecutionEntity> entry : tables.entrySet()) {
                DataSyncTableRouteSnapshotVO route = new DataSyncTableRouteSnapshotVO();
                route.setRouteId(entry.getValue().getRouteId());
                route.setSortOrder(entry.getValue().getRouteOrder());
                DataSyncEndpointSnapshotVO source = new DataSyncEndpointSnapshotVO();
                source.setTable(entry.getKey());
                route.setSource(source);
                DataSyncEndpointSnapshotVO target = new DataSyncEndpointSnapshotVO();
                target.setTable("ods_" + entry.getKey());
                route.setTarget(target);
                routes.add(route);
            }
            snapshot.setTableRoutes(routes);
            return snapshot;
        }

        private DataSyncTableAttemptLifecycle lifecycle() {
            return new DataSyncTableAttemptLifecycle() {
                @Override
                public DataSyncTableAttemptEntity begin(String ws, String tableId, int no) {
                    DataSyncTableExecutionEntity table = byTableId(tableId);
                    DataSyncTableAttemptEntity attempt = new DataSyncTableAttemptEntity();
                    attempt.setId(tableId + "-attempt-" + no);
                    attempt.setTableExecutionId(tableId);
                    attempt.setAttemptNo(no);
                    attempt.setStatus(DataSyncAttemptStatus.RUNNING);
                    attempt.setReadRows(0L);
                    attempt.setWriteRows(0L);
                    attempts.get(name(table)).add(attempt);
                    table.setStatus(DataSyncTableExecutionStatus.RUNNING);
                    table.setCurrentAttempt(no);
                    table.setReadRows(0L);
                    table.setWriteRows(0L);
                    return attempt;
                }

                @Override
                public void updateMetrics(
                        String ws, String tableId, String attemptId, int no, long read, long write) {
                    DataSyncTableExecutionEntity table = byTableId(tableId);
                    table.setReadRows(read);
                    table.setWriteRows(write);
                    DataSyncTableAttemptEntity attempt = attempts.get(name(table)).get(no - 1);
                    attempt.setReadRows(read);
                    attempt.setWriteRows(write);
                }

                @Override
                public void complete(
                        String ws, String tableId, String attemptId, int no, long read, long write,
                        DataSyncTableExecutionStatus target, Integer code, String message) {
                    updateMetrics(ws, tableId, attemptId, no, read, write);
                    DataSyncTableExecutionEntity table = byTableId(tableId);
                    table.setStatus(target);
                    table.setErrorCode(code);
                    table.setErrorMessage(message);
                    attempts.get(name(table)).get(no - 1).setStatus(
                            target == DataSyncTableExecutionStatus.SUCCEEDED
                                    ? DataSyncAttemptStatus.SUCCEEDED : DataSyncAttemptStatus.FAILED);
                }

                @Override
                public void cancelUnfinished(String ws, String rootId, DataSyncTableExecutionStatus target) {
                    for (DataSyncTableExecutionEntity table : tables.values()) {
                        if (!table.getStatus().isTerminal()) table.setStatus(target);
                    }
                }
            };
        }

        private DataSyncTableExecutionEntity byTableId(String id) {
            return tables.values().stream().filter(table -> id.equals(table.getId())).findFirst().orElseThrow();
        }

        private String name(DataSyncTableExecutionEntity table) {
            return table.getId().substring("table-".length());
        }

        private DataSyncTableExecutionRepository tableRepository() {
            return (DataSyncTableExecutionRepository) Proxy.newProxyInstance(
                    DataSyncTableExecutionRepository.class.getClassLoader(),
                    new Class<?>[] {DataSyncTableExecutionRepository.class},
                    (proxy, method, args) -> {
                        if ("queryByExecution".equals(method.getName())) return List.copyOf(tables.values());
                        if ("queryById".equals(method.getName()) && args.length == 2) {
                            return Optional.ofNullable(byTableId((String) args[1]));
                        }
                        throw new UnsupportedOperationException(method.getName());
                    });
        }

        private DataSyncInstanceRepository rootRepository() {
            return (DataSyncInstanceRepository) Proxy.newProxyInstance(
                    DataSyncInstanceRepository.class.getClassLoader(),
                    new Class<?>[] {DataSyncInstanceRepository.class},
                    (proxy, method, args) -> {
                        String methodName = method.getName();
                        if ("queryById".equals(methodName)) return Optional.of(root);
                        if ("transitionStatus".equals(methodName)) {
                            if (root.getStatus() != args[2]) return false;
                            root.setStatus((DataSyncInstanceStatus) args[3]);
                            return true;
                        }
                        if ("updateMetrics".equals(methodName)) {
                            root.setReadRows((Long) args[2]);
                            root.setWriteRows((Long) args[3]);
                            return true;
                        }
                        if ("completeExecution".equals(methodName)) {
                            if (root.getStatus() != args[2]) return false;
                            root.setStatus((DataSyncInstanceStatus) args[3]);
                            root.setReadRows((Long) args[6]);
                            root.setWriteRows((Long) args[7]);
                            return true;
                        }
                        if ("cancelExecution".equals(methodName)) {
                            if (root.getStatus() != args[2]) return false;
                            root.setStatus(DataSyncInstanceStatus.CANCELED);
                            return true;
                        }
                        throw new UnsupportedOperationException(methodName);
                    });
        }
    }

    private static void inject(Object target, String fieldName, Object value) throws Exception {
        Field field = MultiTableOfflineExecutor.class.getDeclaredField(fieldName);
        field.setAccessible(true);
        field.set(target, value);
    }
}
