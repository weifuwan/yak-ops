package io.yak.ops.business.datasync.execution.executor;

import io.yak.ops.business.datasync.execution.lifecycle.DataSyncAttemptLifecycle;
import io.yak.ops.business.datasync.execution.lifecycle.DataSyncExecutionControl;
import io.yak.ops.business.datasync.execution.lifecycle.DataSyncExecutionRegistry;
import io.yak.ops.business.datasync.execution.lifecycle.DataSyncRetryAssessment;
import io.yak.ops.business.datasync.execution.lifecycle.DataSyncRetryClassifier;
import io.yak.ops.business.datasync.execution.lifecycle.DataSyncRetryDecision;
import io.yak.ops.business.datasync.execution.planning.OfflineSyncExecutionPlan;
import io.yak.ops.business.datasync.execution.planning.OfflineSyncExecutionPlanner;
import io.yak.ops.business.datasync.execution.trace.ExecutionTraceSession;
import io.yak.ops.business.datasync.execution.trace.ExecutionTraceStore;
import io.yak.ops.common.bean.vo.datasync.DataSyncDefinitionSnapshotVO;
import io.yak.ops.common.enums.datasync.DataSyncAttemptStatus;
import io.yak.ops.common.enums.datasync.DataSyncInstanceStatus;
import io.yak.ops.dao.entity.datasync.DataSyncAttemptEntity;
import jakarta.annotation.Resource;
import java.time.LocalDateTime;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Component;

/**
 * 将 Offline Execution 按 Retry Policy 拆成连续 Attempt，并复用同一个 Execution Root 收口最终状态。
 *
 * @author weifuwan
 * @since 2026-09-27
 */
@Component
public class OfflineSyncExecutor {

    private static final Logger LOG = LoggerFactory.getLogger(OfflineSyncExecutor.class);

    private final DataSyncRetryClassifier retryClassifier = new DataSyncRetryClassifier();

    @Resource
    private OfflineSyncExecutionPlanner executionPlanner;

    @Resource
    private DataSyncExecutionRegistry executionRegistry;

    @Resource
    private DataSyncAttemptLifecycle attemptLifecycle;

    @Resource
    private ExecutionTraceStore executionTraceStore;

    public void submit(String workspaceId, String instanceId, DataSyncDefinitionSnapshotVO snapshot) {
        start(SingleTableRunContext.submission(workspaceId, instanceId, snapshot));
    }

    public void resumeRetry(
            String workspaceId,
            String instanceId,
            DataSyncDefinitionSnapshotVO snapshot,
            int nextAttemptNo,
            int maxAttempts,
            int backoffSeconds,
            LocalDateTime nextRetryTime) {
        start(SingleTableRunContext.recovery(
                workspaceId, instanceId, snapshot, nextAttemptNo, maxAttempts, backoffSeconds, nextRetryTime));
    }

    private void start(SingleTableRunContext context) {
        SingleTableAttemptRunner.start(
                context,
                "yak-offline-sync-",
                attemptLifecycle,
                (attemptNo, expectedStatus) -> executeAttempt(context, attemptNo, expectedStatus));
    }

    private DataSyncRetryDecision executeAttempt(
            SingleTableRunContext context, int attemptNo, DataSyncInstanceStatus expectedExecutionStatus) {
        String workspaceId = context.workspaceId();
        String instanceId = context.executionId();
        DataSyncDefinitionSnapshotVO snapshot = context.snapshot();
        int maxAttempts = context.maxAttempts();
        DataSyncAttemptEntity attempt = attemptLifecycle.createAttempt(workspaceId, instanceId, attemptNo);
        DataSyncExecutionControl control = executionRegistry.reserve(instanceId);
        LocalExecution<?> execution = null;
        boolean started = false;
        ExecutionTraceSession traceSession = null;
        try {
            if (!attemptLifecycle.startAttempt(
                    workspaceId, instanceId, attempt.getId(), attemptNo, expectedExecutionStatus)) {
                attemptLifecycle.cancelActiveAttempt(workspaceId, instanceId);
                return DataSyncRetryDecision.stop();
            }
            started = true;
            if (control.isCanceled()) {
                attemptLifecycle.cancelActiveAttempt(workspaceId, instanceId);
                return DataSyncRetryDecision.stop();
            }
            traceSession = executionTraceStore.openSession(workspaceId, instanceId, attempt.getId(), attemptNo);
            OfflineSyncExecutionPlan plan = executionPlanner.plan(snapshot, traceSession.listener());
            execution = control.launch(() -> new LocalExecutionEngine()
                    .start(plan.source(), plan.sink(), plan.sourceSchema(), plan.sourceParallelism()));
            if (execution == null) {
                attemptLifecycle.cancelActiveAttempt(workspaceId, instanceId);
                return DataSyncRetryDecision.stop();
            }
            attemptLifecycle.recordSourceReady(workspaceId, instanceId, attempt.getId());
            attemptLifecycle.recordTargetReady(workspaceId, instanceId, attempt.getId());

            LOG.info(
                    "离线同步Attempt开始执行，workspaceId={}, taskId={}, instanceId={}, attempt={}/{}",
                    workspaceId,
                    snapshot.getTaskId(),
                    instanceId,
                    attemptNo,
                    maxAttempts);

            ExecutionObservation observation = ExecutionMetricsPoller.awaitTermination(
                    execution,
                    value -> attemptLifecycle.updateMetrics(
                            workspaceId, instanceId, attempt.getId(), value.readRows(), value.writeRows()));
            ExecutionStatus status = observation.status();
            ExecutionMetrics metrics = observation.metrics();

            if (status == ExecutionStatus.SUCCEEDED) {
                attemptLifecycle.succeedAttempt(
                        workspaceId, instanceId, attempt.getId(), attemptNo, metrics.readRows(), metrics.writeRows());
                LOG.info(
                        "离线同步Execution执行成功，workspaceId={}, taskId={}, instanceId={}, attempt={}",
                        workspaceId,
                        snapshot.getTaskId(),
                        instanceId,
                        attemptNo);
                return DataSyncRetryDecision.stop();
            }

            if (status == ExecutionStatus.CANCELED) {
                attemptLifecycle.cancelActiveAttempt(workspaceId, instanceId);
                LOG.info(
                        "离线同步Execution已取消，workspaceId={}, taskId={}, instanceId={}, attempt={}",
                        workspaceId,
                        snapshot.getTaskId(),
                        instanceId,
                        attemptNo);
                return DataSyncRetryDecision.stop();
            }

            Throwable failure = execution.failure().orElse(null);
            String message = ExecutionErrorMessages.attemptFailure(failure);
            return failAttempt(
                    context,
                    attempt,
                    attemptNo,
                    new AttemptFailureDetails(
                            DataSyncAttemptStatus.RUNNING, DataSyncInstanceStatus.RUNNING, metrics, failure, message),
                    true);
        } catch (InterruptedException exception) {
            Thread.currentThread().interrupt();
            ExecutionRuntimeTermination.cancelAndAwait(execution);
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
                    execution != null);
        } catch (Exception exception) {
            ExecutionRuntimeTermination.cancelAndAwait(execution);
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
                    execution != null);
        } finally {
            ExecutionRuntimeTermination.stopAndAwait(execution);
            executionRegistry.remove(instanceId, control);
            if (traceSession != null) traceSession.close();
        }
    }

    private DataSyncRetryDecision failAttempt(
            SingleTableRunContext context,
            DataSyncAttemptEntity attempt,
            int attemptNo,
            AttemptFailureDetails details,
            boolean runtimeStarted) {
        String workspaceId = context.workspaceId();
        String instanceId = context.executionId();
        int maxAttempts = context.maxAttempts();
        String message = details.message();
        long writeRows = details.metrics() == null ? 0L : details.metrics().writeRows();
        DataSyncRetryAssessment assessment =
                retryAssessment(context.snapshot(), details.cause(), runtimeStarted, writeRows);
        DataSyncRetryDecision decision = SingleTableAttemptFailureRecorder.record(
                attemptLifecycle, context, attempt, attemptNo, details, assessment);
        if (decision.retry()) {
            LOG.warn(
                    "离线同步Attempt失败等待重试，workspaceId={}, instanceId={}, attempt={}/{}, nextRetryTime={}, retryReason={}, error={}",
                    workspaceId,
                    instanceId,
                    attemptNo,
                    maxAttempts,
                    decision.nextRetryTime(),
                    assessment.reason(),
                    message);
        } else {
            LOG.error(
                    "离线同步Execution执行失败，workspaceId={}, instanceId={}, attempt={}/{}, retryReason={}, error={}",
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
            DataSyncDefinitionSnapshotVO snapshot, Throwable failure, boolean runtimeStarted, long writeRows) {
        if (ExecutionRetryPolicy.from(snapshot.getRetryPolicy()).isFixed()) {
            return DataSyncRetryAssessment.retryable("固定重试策略");
        }
        return retryClassifier.classify(snapshot, failure, runtimeStarted, writeRows);
    }
}
