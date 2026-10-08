package io.yak.ops.business.datasync.execution.executor;

import io.yak.ops.business.datasync.exception.DataSyncErrorCode;
import io.yak.ops.business.datasync.execution.lifecycle.DataSyncAttemptLifecycle;
import io.yak.ops.business.datasync.execution.lifecycle.DataSyncExecutionRegistry;
import io.yak.ops.business.datasync.execution.lifecycle.DataSyncRetryAssessment;
import io.yak.ops.business.datasync.execution.lifecycle.DataSyncRetryClassifier;
import io.yak.ops.business.datasync.execution.lifecycle.DataSyncRetryDecision;
import io.yak.ops.business.datasync.execution.planning.RealtimeSyncExecutionPlan;
import io.yak.ops.business.datasync.execution.planning.RealtimeSyncExecutionPlanner;
import io.yak.ops.business.datasync.execution.realtime.MySqlCdcServerIdAllocator;
import io.yak.ops.business.datasync.execution.realtime.RealtimeSyncStateNamespace;
import io.yak.ops.common.bean.vo.datasync.DataSyncDefinitionSnapshotVO;
import io.yak.ops.common.context.WorkspaceContext;
import io.yak.ops.common.enums.datasync.DataSyncAttemptStatus;
import io.yak.ops.common.enums.datasync.DataSyncInstanceStatus;
import io.yak.ops.dao.entity.datasync.DataSyncAttemptEntity;
import io.yak.ops.flow.runtime.ExecutionMetrics;
import io.yak.ops.flow.runtime.ExecutionStatus;
import io.yak.ops.flow.runtime.LocalExecution;
import io.yak.ops.flow.runtime.LocalExecutionEngine;
import jakarta.annotation.Resource;
import java.time.Duration;
import java.time.LocalDateTime;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Component;

/**
 * 将 REALTIME Execution 按 Retry Policy 拆成连续 Attempt，并持续复用同一 Task/version 的 CDC state。
 *
 * @author weifuwan
 * @since 2026-09-28
 */
@Component
public class RealtimeSyncExecutor {

    private static final Logger LOG = LoggerFactory.getLogger(RealtimeSyncExecutor.class);
    private static final long METRICS_FLUSH_INTERVAL_MILLIS = 500L;

    private final DataSyncRetryClassifier retryClassifier = new DataSyncRetryClassifier();

    @Resource
    private RealtimeSyncExecutionPlanner executionPlanner;

    @Resource
    private RealtimeSyncStateNamespace stateNamespace;

    @Resource
    private MySqlCdcServerIdAllocator serverIdAllocator;

    @Resource
    private DataSyncExecutionRegistry executionRegistry;

    @Resource
    private DataSyncAttemptLifecycle attemptLifecycle;

    public void submit(String workspaceId, String instanceId, DataSyncDefinitionSnapshotVO snapshot) {
        ExecutionRetryPolicy policy = ExecutionRetryPolicy.from(snapshot.getRetryPolicy());
        start(new SingleTableRunContext(
                workspaceId,
                instanceId,
                snapshot,
                1,
                policy.maxAttempts(),
                policy.baseBackoffSeconds(),
                DataSyncInstanceStatus.PENDING,
                null));
    }

    public void resumeRetry(
            String workspaceId,
            String instanceId,
            DataSyncDefinitionSnapshotVO snapshot,
            int nextAttemptNo,
            int maxAttempts,
            int backoffSeconds,
            LocalDateTime nextRetryTime) {
        if (nextAttemptNo < 2 || maxAttempts < nextAttemptNo || nextRetryTime == null) {
            throw new IllegalArgumentException("invalid durable retry recovery state");
        }
        start(new SingleTableRunContext(
                workspaceId,
                instanceId,
                snapshot,
                nextAttemptNo,
                maxAttempts,
                Math.max(0, backoffSeconds),
                DataSyncInstanceStatus.RETRY_WAITING,
                nextRetryTime));
    }

    private void start(SingleTableRunContext context) {
        Thread.ofVirtual().name("yak-realtime-sync-" + context.executionId()).start(() -> execute(context));
    }

    private void execute(SingleTableRunContext context) {
        String workspaceId = context.workspaceId();
        String instanceId = context.executionId();
        DataSyncInstanceStatus expectedExecutionStatus = context.expectedExecutionStatus();
        WorkspaceContext.bind(workspaceId);
        try {
            if (context.initialRetryTime() != null
                    && !waitForRetry(workspaceId, instanceId, context.initialRetryTime())) return;

            for (int attemptNo = context.firstAttemptNo(); attemptNo <= context.maxAttempts(); attemptNo++) {
                DataSyncRetryDecision decision = executeAttempt(context, attemptNo, expectedExecutionStatus);
                if (!decision.retry()) return;
                if (!waitForRetry(workspaceId, instanceId, decision.nextRetryTime())) return;
                expectedExecutionStatus = DataSyncInstanceStatus.RETRY_WAITING;
            }
        } finally {
            WorkspaceContext.clear();
        }
    }

    private DataSyncRetryDecision executeAttempt(
            SingleTableRunContext context, int attemptNo, DataSyncInstanceStatus expectedExecutionStatus) {
        String workspaceId = context.workspaceId();
        String instanceId = context.executionId();
        DataSyncDefinitionSnapshotVO snapshot = context.snapshot();
        int maxAttempts = context.maxAttempts();
        DataSyncAttemptEntity attempt = attemptLifecycle.createAttempt(workspaceId, instanceId, attemptNo);
        LocalExecution<?> execution = null;
        String stateKey = null;
        Long serverId = null;
        boolean started = false;

        try {
            stateKey = stateNamespace.stateKey(workspaceId, snapshot.getTaskId(), snapshot.getTaskVersion());
            serverId = serverIdAllocator.allocate(stateKey);
            RealtimeSyncExecutionPlan plan = executionPlanner.plan(workspaceId, snapshot, serverId);
            execution = new LocalExecutionEngine(plan.checkpointInterval())
                    .start(plan.source(), plan.sink(), plan.sourceSchema());
            executionRegistry.register(instanceId, execution);

            if (!attemptLifecycle.startAttempt(
                    workspaceId, instanceId, attempt.getId(), attemptNo, expectedExecutionStatus)) {
                execution.cancel();
                execution.await();
                attemptLifecycle.cancelActiveAttempt(workspaceId, instanceId);
                return DataSyncRetryDecision.stop();
            }
            started = true;
            attemptLifecycle.recordSourceReady(workspaceId, instanceId, attempt.getId());
            attemptLifecycle.recordTargetReady(workspaceId, instanceId, attempt.getId());

            LOG.info(
                    "实时同步Attempt开始执行，workspaceId={}, taskId={}, instanceId={}, attempt={}/{}",
                    workspaceId,
                    snapshot.getTaskId(),
                    instanceId,
                    attemptNo,
                    maxAttempts);

            while (execution.status() == ExecutionStatus.RUNNING) {
                persistMetrics(workspaceId, instanceId, attempt.getId(), execution.metrics());
                Thread.sleep(METRICS_FLUSH_INTERVAL_MILLIS);
            }

            ExecutionStatus status = execution.await();
            ExecutionMetrics metrics = execution.metrics();
            persistMetrics(workspaceId, instanceId, attempt.getId(), metrics);

            if (status == ExecutionStatus.CANCELED) {
                attemptLifecycle.cancelActiveAttempt(workspaceId, instanceId);
                LOG.info(
                        "实时同步Execution已停止，workspaceId={}, taskId={}, instanceId={}, attempt={}",
                        workspaceId,
                        snapshot.getTaskId(),
                        instanceId,
                        attemptNo);
                return DataSyncRetryDecision.stop();
            }

            if (status == ExecutionStatus.FAILED) {
                Throwable failure = execution.failure().orElse(null);
                String message = ExecutionErrorMessages.attemptFailure(failure);
                return failAttempt(
                        context,
                        attempt,
                        attemptNo,
                        new AttemptFailureDetails(
                                DataSyncAttemptStatus.RUNNING,
                                DataSyncInstanceStatus.RUNNING,
                                metrics,
                                failure,
                                message),
                        false);
            }

            String message = "实时同步连续 Source 意外结束";
            return failAttempt(
                    context,
                    attempt,
                    attemptNo,
                    new AttemptFailureDetails(
                            DataSyncAttemptStatus.RUNNING, DataSyncInstanceStatus.RUNNING, metrics, null, message),
                    true);
        } catch (InterruptedException exception) {
            Thread.currentThread().interrupt();
            if (execution != null) execution.cancel();
            return failAttempt(
                    context,
                    attempt,
                    attemptNo,
                    new AttemptFailureDetails(
                            started ? DataSyncAttemptStatus.RUNNING : DataSyncAttemptStatus.PENDING,
                            started ? DataSyncInstanceStatus.RUNNING : expectedExecutionStatus,
                            execution == null ? null : execution.metrics(),
                            exception,
                            ExecutionErrorMessages.attemptFailure(exception)),
                    false);
        } catch (Exception exception) {
            if (execution != null) execution.cancel();
            return failAttempt(
                    context,
                    attempt,
                    attemptNo,
                    new AttemptFailureDetails(
                            started ? DataSyncAttemptStatus.RUNNING : DataSyncAttemptStatus.PENDING,
                            started ? DataSyncInstanceStatus.RUNNING : expectedExecutionStatus,
                            execution == null ? null : execution.metrics(),
                            exception,
                            ExecutionErrorMessages.attemptFailure(exception)),
                    false);
        } finally {
            if (execution != null) executionRegistry.remove(instanceId, execution);
            if (stateKey != null && serverId != null) serverIdAllocator.release(stateKey, serverId);
        }
    }

    private DataSyncRetryDecision failAttempt(
            SingleTableRunContext context,
            DataSyncAttemptEntity attempt,
            int attemptNo,
            AttemptFailureDetails details,
            boolean unexpectedContinuousEnd) {
        String workspaceId = context.workspaceId();
        String instanceId = context.executionId();
        DataSyncDefinitionSnapshotVO snapshot = context.snapshot();
        int maxAttempts = context.maxAttempts();
        int backoffSeconds = context.backoffSeconds();
        DataSyncAttemptStatus expectedAttemptStatus = details.expectedAttemptStatus();
        DataSyncInstanceStatus expectedExecutionStatus = details.expectedExecutionStatus();
        ExecutionMetrics metrics = details.metrics();
        Throwable failure = details.cause();
        String message = details.message();
        long readRows = metrics == null ? 0L : metrics.readRows();
        long writeRows = metrics == null ? 0L : metrics.writeRows();
        DataSyncRetryAssessment assessment = retryAssessment(snapshot, failure, writeRows, unexpectedContinuousEnd);
        int effectiveBackoffSeconds = ExecutionRetryPolicy.from(snapshot.getRetryPolicy())
                .delayForAttempt(attemptNo, backoffSeconds);
        DataSyncRetryDecision decision = attemptLifecycle.failAttempt(
                workspaceId,
                instanceId,
                attempt.getId(),
                attemptNo,
                expectedAttemptStatus,
                expectedExecutionStatus,
                assessment.retryable(),
                assessment.reason(),
                maxAttempts,
                effectiveBackoffSeconds,
                readRows,
                writeRows,
                DataSyncErrorCode.EXECUTION_FAILED.getCode(),
                message);
        if (decision.retry()) {
            LOG.warn(
                    "实时同步Attempt失败等待重试，workspaceId={}, instanceId={}, attempt={}/{}, nextRetryTime={}, retryReason={}, error={}",
                    workspaceId,
                    instanceId,
                    attemptNo,
                    maxAttempts,
                    decision.nextRetryTime(),
                    assessment.reason(),
                    message);
        } else {
            LOG.error(
                    "实时同步Execution执行失败，workspaceId={}, instanceId={}, attempt={}/{}, retryReason={}, error={}",
                    workspaceId,
                    instanceId,
                    attemptNo,
                    maxAttempts,
                    assessment.reason(),
                    message);
        }
        return decision;
    }

    private DataSyncRetryAssessment retryAssessment(
            DataSyncDefinitionSnapshotVO snapshot, Throwable failure, long writeRows, boolean unexpectedContinuousEnd) {
        if (ExecutionRetryPolicy.from(snapshot.getRetryPolicy()).isFixed()) {
            return DataSyncRetryAssessment.retryable("固定重试策略");
        }
        if (unexpectedContinuousEnd) {
            return retryClassifier.classifyUnexpectedContinuousEnd(snapshot);
        }
        return retryClassifier.classify(snapshot, failure, true, writeRows);
    }

    private void persistMetrics(String workspaceId, String instanceId, String attemptId, ExecutionMetrics metrics) {
        attemptLifecycle.updateMetrics(workspaceId, instanceId, attemptId, metrics.readRows(), metrics.writeRows());
    }

    private boolean waitForRetry(String workspaceId, String instanceId, LocalDateTime nextRetryTime) {
        if (nextRetryTime == null) return false;
        long delayMillis = Math.max(
                0L, Duration.between(LocalDateTime.now(), nextRetryTime).toMillis());
        try {
            if (delayMillis > 0) Thread.sleep(delayMillis);
        } catch (InterruptedException exception) {
            Thread.currentThread().interrupt();
            return false;
        }
        return attemptLifecycle.isRetryWaiting(workspaceId, instanceId);
    }

}
