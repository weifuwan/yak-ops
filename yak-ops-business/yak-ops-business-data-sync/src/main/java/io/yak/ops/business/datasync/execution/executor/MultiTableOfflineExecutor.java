package io.yak.ops.business.datasync.execution.executor;

import io.yak.ops.business.datasync.exception.DataSyncErrorCode;
import io.yak.ops.business.datasync.execution.lifecycle.DataSyncRetryAssessment;
import io.yak.ops.business.datasync.execution.lifecycle.DataSyncRetryClassifier;
import io.yak.ops.business.datasync.execution.lifecycle.DataSyncTableAttemptLifecycle;
import io.yak.ops.business.datasync.execution.planning.OfflineSyncExecutionPlan;
import io.yak.ops.business.datasync.execution.planning.OfflineSyncExecutionPlanner;
import io.yak.ops.business.datasync.execution.trace.ExecutionTraceSession;
import io.yak.ops.business.datasync.execution.trace.ExecutionTraceStore;
import io.yak.ops.common.bean.vo.datasync.DataSyncDefinitionSnapshotVO;
import io.yak.ops.common.bean.vo.datasync.DataSyncRetryPolicyVO;
import io.yak.ops.common.bean.vo.datasync.DataSyncTableRouteSnapshotVO;
import io.yak.ops.common.context.WorkspaceContext;
import io.yak.ops.common.enums.datasync.DataSyncInstanceStatus;
import io.yak.ops.common.enums.datasync.DataSyncTableExecutionStatus;
import io.yak.ops.common.util.DateUtils;
import io.yak.ops.dao.entity.datasync.DataSyncInstanceEntity;
import io.yak.ops.dao.entity.datasync.DataSyncTableAttemptEntity;
import io.yak.ops.dao.entity.datasync.DataSyncTableExecutionEntity;
import io.yak.ops.dao.repository.datasync.DataSyncInstanceRepository;
import io.yak.ops.dao.repository.datasync.DataSyncTableExecutionRepository;
import io.yak.ops.flow.runtime.ExecutionMetrics;
import io.yak.ops.flow.runtime.ExecutionStatus;
import io.yak.ops.flow.runtime.LocalExecution;
import io.yak.ops.flow.runtime.LocalExecutionEngine;
import jakarta.annotation.Resource;
import java.time.Duration;
import java.time.LocalDateTime;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.ConcurrentMap;
import java.util.function.BiConsumer;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Component;

/**
 * OFFLINE 多表执行的单节点、有界编排。
 *
 * <p>每个 Root 内顺序执行 Route；失败表独立 Retry，其他表继续运行。已成功的表永不在该
 * Root 内重新执行。Root 只聚合表级事实，不再拥有表级 Attempt。仅用于多 Route Task；
 * 旧单 Route 继续走已有 OfflineSyncExecutor。</p>
 *
 * @author weifuwan
 * @since 2026-10-08
 */
@Component
public class MultiTableOfflineExecutor {

    private static final Logger LOG = LoggerFactory.getLogger(MultiTableOfflineExecutor.class);

    private final ConcurrentMap<String, MultiTableRunControl> controls = new ConcurrentHashMap<>();
    private final DataSyncRetryClassifier retryClassifier = new DataSyncRetryClassifier();

    @Resource
    private DataSyncTableAttemptLifecycle tableAttemptLifecycle;

    @Resource
    private DataSyncInstanceRepository instanceRepository;

    @Resource
    private DataSyncTableExecutionRepository tableExecutionRepository;

    @Resource
    private OfflineSyncExecutionPlanner executionPlanner;

    @Resource
    private ExecutionTraceStore executionTraceStore;

    public void submit(String workspaceId, String rootExecutionId, DataSyncDefinitionSnapshotVO snapshot) {
        if (snapshot == null
                || snapshot.getTableRoutes() == null
                || snapshot.getTableRoutes().size() < 2
                || !"OFFLINE".equals(snapshot.getSyncType())) {
            throw new IllegalArgumentException("multi-table executor requires an OFFLINE multi-route snapshot");
        }
        MultiTableRunControl control = new MultiTableRunControl();
        if (controls.putIfAbsent(rootExecutionId, control) != null) {
            throw new IllegalStateException("multi-table execution already started: " + rootExecutionId);
        }
        Thread worker = Thread.ofVirtual()
                .name("yak-offline-multi-" + rootExecutionId)
                .unstarted(() -> runBound(workspaceId, rootExecutionId, snapshot, control));
        control.setWorker(worker);
        worker.start();
    }

    /** 用户取消整个 Root；正在执行的 Route 会立即收到 Runtime cancel，后续 Route 不再启动。 */
    public boolean cancel(String rootExecutionId) {
        MultiTableRunControl control = controls.get(rootExecutionId);
        if (control == null) return false;
        control.cancel();
        return true;
    }

    /** 用于 Contract Test 的同步执行入口，不创建额外线程。 */
    void executeInline(String workspaceId, String rootExecutionId, DataSyncDefinitionSnapshotVO snapshot) {
        runBound(workspaceId, rootExecutionId, snapshot, new MultiTableRunControl());
    }

    private void runBound(
            String workspaceId,
            String rootExecutionId,
            DataSyncDefinitionSnapshotVO snapshot,
            MultiTableRunControl control) {
        WorkspaceContext.bind(workspaceId);
        try {
            execute(workspaceId, rootExecutionId, snapshot, control);
        } catch (Exception exception) {
            LOG.error(
                    "多表同步异常，workspaceId={}, executionId={}, error={}",
                    workspaceId,
                    rootExecutionId,
                    ExecutionErrorMessages.tableFailure(exception));
            try {
                DataSyncInstanceEntity root = instanceRepository
                        .queryById(workspaceId, rootExecutionId)
                        .orElse(null);
                if (root != null && root.getStatus() == DataSyncInstanceStatus.CANCELED) {
                    tableAttemptLifecycle.cancelUnfinished(
                            workspaceId, rootExecutionId, DataSyncTableExecutionStatus.CANCELED);
                    return;
                }
                tableAttemptLifecycle.cancelUnfinished(workspaceId, rootExecutionId, DataSyncTableExecutionStatus.LOST);
                if (root != null && root.getStatus() == DataSyncInstanceStatus.RUNNING) {
                    long[] totals = totals(workspaceId, rootExecutionId);
                    instanceRepository.completeExecution(
                            workspaceId,
                            rootExecutionId,
                            DataSyncInstanceStatus.RUNNING,
                            DataSyncInstanceStatus.LOST,
                            1,
                            DateUtils.now(),
                            totals[0],
                            totals[1],
                            DataSyncErrorCode.EXECUTION_LOST.getCode(),
                            ExecutionErrorMessages.tableFailure(exception));
                }
            } catch (Exception recoveryException) {
                LOG.error(
                        "多表同步异常收口失败，workspaceId={}, executionId={}, error={}",
                        workspaceId,
                        rootExecutionId,
                        ExecutionErrorMessages.tableFailure(recoveryException));
            }
        } finally {
            controls.remove(rootExecutionId, control);
            WorkspaceContext.clear();
        }
    }

    private void execute(
            String workspaceId,
            String rootExecutionId,
            DataSyncDefinitionSnapshotVO snapshot,
            MultiTableRunControl control) {
        if (!instanceRepository.transitionStatus(
                workspaceId,
                rootExecutionId,
                DataSyncInstanceStatus.PENDING,
                DataSyncInstanceStatus.RUNNING,
                DateUtils.now(),
                null,
                null,
                null)) {
            finishPendingAfterRootChange(workspaceId, rootExecutionId);
            return;
        }
        List<DataSyncTableExecutionEntity> tables =
                tableExecutionRepository.queryByExecution(workspaceId, rootExecutionId);
        if (tables.size() != snapshot.getTableRoutes().size()) {
            throw new IllegalStateException("frozen Route and Table Execution count mismatch");
        }
        Map<String, DataSyncTableExecutionEntity> byRoute = new HashMap<>();
        for (DataSyncTableExecutionEntity table : tables) {
            if (byRoute.putIfAbsent(table.getRouteId(), table) != null) {
                throw new IllegalStateException("duplicate Table Execution routeId");
            }
        }

        MultiTableRootContext root = new MultiTableRootContext(
                workspaceId, rootExecutionId, snapshot, control, new MultiTableRootMetrics(tables));
        for (DataSyncTableRouteSnapshotVO route : snapshot.getTableRoutes()) {
            if (control.isCanceled() || !rootRunning(workspaceId, rootExecutionId)) break;
            DataSyncTableExecutionEntity table = byRoute.get(route.getRouteId());
            if (table == null || !route.getSortOrder().equals(table.getRouteOrder())) {
                throw new IllegalStateException("frozen Route / Table Execution identity mismatch");
            }
            if (table.getStatus() != DataSyncTableExecutionStatus.PLANNED) {
                throw new IllegalStateException("a table was already started in this Root Execution");
            }
            executeTable(root, route, table);
            refreshRootMetrics(root);
        }

        if (control.isCanceled() && rootRunning(workspaceId, rootExecutionId)) {
            instanceRepository.cancelExecution(
                    workspaceId, rootExecutionId, DataSyncInstanceStatus.RUNNING, DateUtils.now());
        }
        if (control.isCanceled() || !rootRunning(workspaceId, rootExecutionId)) {
            finishPendingAfterRootChange(workspaceId, rootExecutionId);
            return;
        }
        finishRoot(workspaceId, rootExecutionId);
    }

    private void executeTable(
            MultiTableRootContext root, DataSyncTableRouteSnapshotVO route, DataSyncTableExecutionEntity table) {
        String workspaceId = root.workspaceId();
        String rootExecutionId = root.executionId();
        DataSyncDefinitionSnapshotVO rootSnapshot = root.snapshot();
        MultiTableRunControl control = root.control();
        MultiTableRootMetrics rootMetrics = root.metrics();
        DataSyncDefinitionSnapshotVO runtimeSnapshot = singleRouteSnapshot(rootSnapshot, route);
        DataSyncRetryPolicyVO policy = rootSnapshot.getRetryPolicy();
        ExecutionRetryPolicy retryPolicy = ExecutionRetryPolicy.from(policy);
        int maxAttempts = retryPolicy.maxAttempts();
        int backoffSeconds = retryPolicy.baseBackoffSeconds();

        for (int attemptNo = 1; attemptNo <= maxAttempts; attemptNo++) {
            if (control.isCanceled() || !rootRunning(workspaceId, rootExecutionId)) return;
            DataSyncTableAttemptEntity attempt = tableAttemptLifecycle.begin(workspaceId, table.getId(), attemptNo);
            rootMetrics.reset(table.getId());
            refreshRootMetrics(root);
            int currentAttempt = attemptNo;
            BiConsumer<Long, Long> metrics = (read, write) -> {
                tableAttemptLifecycle.updateMetrics(
                        workspaceId, table.getId(), attempt.getId(), currentAttempt, read, write);
                rootMetrics.update(table.getId(), read, write);
                refreshRootMetrics(root);
            };
            RouteExecutionOutcome outcome = executeAttemptRuntime(
                    new RouteAttemptRuntimeContext(
                            workspaceId, table.getId(), attempt.getId(), attemptNo, runtimeSnapshot, control),
                    metrics);
            if (outcome.status() == ExecutionStatus.CANCELED) control.cancel();
            if (control.isCanceled() && rootRunning(workspaceId, rootExecutionId)) {
                instanceRepository.cancelExecution(
                        workspaceId, rootExecutionId, DataSyncInstanceStatus.RUNNING, DateUtils.now());
            }
            if (control.isCanceled() || !rootRunning(workspaceId, rootExecutionId)) {
                finishPendingAfterRootChange(workspaceId, rootExecutionId);
                return;
            }
            if (outcome.status() == ExecutionStatus.SUCCEEDED) {
                tableAttemptLifecycle.complete(
                        workspaceId,
                        table.getId(),
                        attempt.getId(),
                        attemptNo,
                        outcome.readRows(),
                        outcome.writeRows(),
                        DataSyncTableExecutionStatus.SUCCEEDED,
                        null,
                        null);
                return;
            }

            String message = ExecutionErrorMessages.tableFailure(outcome.failure());
            DataSyncRetryAssessment assessment = retryDecision(policy, runtimeSnapshot, outcome);
            boolean retry = assessment.retryable() && attemptNo < maxAttempts;
            tableAttemptLifecycle.complete(
                    workspaceId,
                    table.getId(),
                    attempt.getId(),
                    attemptNo,
                    outcome.readRows(),
                    outcome.writeRows(),
                    retry ? DataSyncTableExecutionStatus.RETRY_WAITING : DataSyncTableExecutionStatus.FAILED,
                    DataSyncErrorCode.EXECUTION_FAILED.getCode(),
                    message);
            if (!retry) {
                LOG.warn(
                        "多表同步单表失败，workspaceId={}, executionId={}, tableExecutionId={}, attempt={}, error={}",
                        workspaceId,
                        rootExecutionId,
                        table.getId(),
                        attemptNo,
                        message);
                return;
            }
            if (!waitForRetry(
                    workspaceId, rootExecutionId, control, retryPolicy.delayForAttempt(attemptNo, backoffSeconds))) {
                return;
            }
        }
    }

    private DataSyncRetryAssessment retryDecision(
            DataSyncRetryPolicyVO policy, DataSyncDefinitionSnapshotVO runtimeSnapshot, RouteExecutionOutcome outcome) {
        if (ExecutionRetryPolicy.from(policy).isFixed()) {
            return DataSyncRetryAssessment.retryable("固定重试策略");
        }
        return retryClassifier.classify(
                runtimeSnapshot, outcome.failure(), outcome.runtimeStarted(), outcome.writeRows());
    }

    private boolean waitForRetry(
            String workspaceId, String rootExecutionId, MultiTableRunControl control, int backoffSeconds) {
        LocalDateTime next = DateUtils.now().plusSeconds(Math.max(0, backoffSeconds));
        while (!control.isCanceled() && rootRunning(workspaceId, rootExecutionId)) {
            long remaining = Duration.between(DateUtils.now(), next).toMillis();
            if (remaining <= 0) return true;
            try {
                Thread.sleep(Math.min(remaining, 250L));
            } catch (InterruptedException exception) {
                Thread.currentThread().interrupt();
                return false;
            }
        }
        return false;
    }

    /** 执行一条已冻结 Route；测试可替换此数据平面入口，产品状态仍走真实 Lifecycle。 */
    protected RouteExecutionOutcome executeAttemptRuntime(
            RouteAttemptRuntimeContext context, BiConsumer<Long, Long> metrics) {
        String workspaceId = context.workspaceId();
        String tableExecutionId = context.tableExecutionId();
        String attemptId = context.attemptId();
        int attemptNo = context.attemptNo();
        DataSyncDefinitionSnapshotVO runtimeSnapshot = context.snapshot();
        MultiTableRunControl control = context.control();
        LocalExecution<?> execution = null;
        boolean started = false;
        try (ExecutionTraceSession trace =
                executionTraceStore.openSession(workspaceId, tableExecutionId, attemptId, attemptNo)) {
            OfflineSyncExecutionPlan plan = executionPlanner.plan(runtimeSnapshot, trace.listener());
            execution = control.launch(() -> new LocalExecutionEngine()
                    .start(plan.source(), plan.sink(), plan.sourceSchema(), plan.sourceParallelism()));
            if (execution == null) {
                return new RouteExecutionOutcome(ExecutionStatus.CANCELED, 0L, 0L, null, false);
            }
            started = true;
            ExecutionObservation observation = ExecutionMetricsPoller.awaitTermination(
                    execution, value -> metrics.accept(value.readRows(), value.writeRows()));
            ExecutionMetrics value = observation.metrics();
            return new RouteExecutionOutcome(
                    observation.status(),
                    value.readRows(),
                    value.writeRows(),
                    execution.failure().orElse(null),
                    true);
        } catch (InterruptedException exception) {
            Thread.currentThread().interrupt();
            ExecutionRuntimeTermination.cancelAndAwait(execution);
            return failedOutcome(execution, exception, started);
        } catch (Exception exception) {
            ExecutionRuntimeTermination.cancelAndAwait(execution);
            return failedOutcome(execution, exception, started);
        } finally {
            ExecutionRuntimeTermination.stopAndAwait(execution);
            control.clearActive();
        }
    }

    private RouteExecutionOutcome failedOutcome(LocalExecution<?> execution, Throwable failure, boolean started) {
        ExecutionMetrics value = execution == null ? null : execution.metrics();
        return new RouteExecutionOutcome(
                ExecutionStatus.FAILED,
                value == null ? 0L : value.readRows(),
                value == null ? 0L : value.writeRows(),
                failure,
                started);
    }

    private DataSyncDefinitionSnapshotVO singleRouteSnapshot(
            DataSyncDefinitionSnapshotVO root, DataSyncTableRouteSnapshotVO route) {
        DataSyncDefinitionSnapshotVO result = new DataSyncDefinitionSnapshotVO();
        result.setTaskId(root.getTaskId());
        result.setTaskName(root.getTaskName());
        result.setTaskVersion(root.getTaskVersion());
        result.setSyncType(root.getSyncType());
        result.setWriteMode(root.getWriteMode());
        result.setRetryPolicy(root.getRetryPolicy());
        result.setTableRoutes(List.of(route));
        result.setSource(route.getSource());
        result.setTarget(route.getTarget());
        result.setMapping(route.getMapping());
        result.setAutoCreateTable(route.getAutoCreateTable());
        result.setRuntimeConfig(route.getRuntimeConfig());
        result.setOfflineRuntimePlan(route.getOfflineRuntimePlan());
        return result;
    }

    private void finishRoot(String workspaceId, String rootExecutionId) {
        List<DataSyncTableExecutionEntity> tables =
                tableExecutionRepository.queryByExecution(workspaceId, rootExecutionId);
        boolean incomplete = tables.stream()
                .anyMatch(
                        table -> table.getStatus() == null || !table.getStatus().isTerminal());
        if (incomplete) throw new IllegalStateException("cannot finish Root with nonterminal Table Execution");
        boolean failed = tables.stream()
                .anyMatch(table -> table.getStatus() == DataSyncTableExecutionStatus.FAILED
                        || table.getStatus() == DataSyncTableExecutionStatus.LOST);
        long[] summary = totals(tables);
        String message = tables.stream()
                .filter(table -> table.getStatus() == DataSyncTableExecutionStatus.FAILED)
                .map(DataSyncTableExecutionEntity::getErrorMessage)
                .filter(value -> value != null && !value.isBlank())
                .findFirst()
                .orElse(null);
        if (!instanceRepository.completeExecution(
                workspaceId,
                rootExecutionId,
                DataSyncInstanceStatus.RUNNING,
                failed ? DataSyncInstanceStatus.FAILED : DataSyncInstanceStatus.SUCCEEDED,
                1,
                DateUtils.now(),
                summary[0],
                summary[1],
                failed ? DataSyncErrorCode.EXECUTION_FAILED.getCode() : null,
                failed ? message : null)) {
            throw new IllegalStateException("Root Execution final state changed concurrently");
        }
    }

    private boolean rootRunning(String workspaceId, String rootExecutionId) {
        return instanceRepository
                .queryById(workspaceId, rootExecutionId)
                .map(root -> root.getStatus() == DataSyncInstanceStatus.RUNNING)
                .orElse(false);
    }

    private void finishPendingAfterRootChange(String workspaceId, String rootExecutionId) {
        DataSyncInstanceEntity root =
                instanceRepository.queryById(workspaceId, rootExecutionId).orElse(null);
        DataSyncTableExecutionStatus target = root != null && root.getStatus() == DataSyncInstanceStatus.CANCELED
                ? DataSyncTableExecutionStatus.CANCELED
                : DataSyncTableExecutionStatus.LOST;
        tableAttemptLifecycle.cancelUnfinished(workspaceId, rootExecutionId, target);
    }

    private void refreshRootMetrics(MultiTableRootContext root) {
        ExecutionMetrics summary = root.metrics().totals();
        instanceRepository.updateMetrics(
                root.workspaceId(), root.executionId(), summary.readRows(), summary.writeRows());
    }

    private long[] totals(String workspaceId, String rootExecutionId) {
        return totals(tableExecutionRepository.queryByExecution(workspaceId, rootExecutionId));
    }

    private long[] totals(List<DataSyncTableExecutionEntity> tables) {
        long read = 0L;
        long write = 0L;
        for (DataSyncTableExecutionEntity table : tables) {
            read = addSaturating(read, table.getReadRows());
            write = addSaturating(write, table.getWriteRows());
        }
        return new long[] {read, write};
    }

    private long addSaturating(long total, Long next) {
        long value = next == null ? 0L : Math.max(0L, next);
        return total > Long.MAX_VALUE - value ? Long.MAX_VALUE : total + value;
    }
}
