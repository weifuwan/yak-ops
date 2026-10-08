package io.yak.ops.business.datasync.execution.executor;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

import io.yak.ops.business.datasync.exception.DataSyncErrorCode;
import io.yak.ops.business.datasync.execution.lifecycle.DataSyncRetryAssessment;
import io.yak.ops.business.datasync.execution.lifecycle.DataSyncRetryDecision;
import io.yak.ops.common.bean.vo.datasync.DataSyncDefinitionSnapshotVO;
import io.yak.ops.common.bean.vo.datasync.DataSyncRetryPolicyVO;
import io.yak.ops.common.context.WorkspaceContext;
import io.yak.ops.common.enums.datasync.DataSyncAttemptStatus;
import io.yak.ops.common.enums.datasync.DataSyncInstanceStatus;
import io.yak.ops.common.enums.datasync.DataSyncRetryPolicyMode;
import io.yak.ops.dao.entity.datasync.DataSyncAttemptEntity;
import io.yak.ops.flow.runtime.ExecutionMetrics;
import java.time.LocalDateTime;
import java.util.ArrayList;
import java.util.List;
import org.junit.jupiter.api.Test;

class SingleTableAttemptRunnerTest {

    @Test
    void retryUsesExpectedWaitingStateAndClearsWorkspace() {
        ExecutorAttemptLifecycleProbe lifecycle = new ExecutorAttemptLifecycleProbe();
        SingleTableRunContext context = SingleTableRunContext.submission("ws-test", "root-1", snapshot(3, 0));
        List<Integer> attempts = new ArrayList<>();
        List<DataSyncInstanceStatus> expected = new ArrayList<>();

        SingleTableAttemptRunner.run(context, lifecycle, (number, status) -> {
            assertEquals("ws-test", WorkspaceContext.getWorkspaceId());
            attempts.add(number);
            expected.add(status);
            return number == 1
                    ? DataSyncRetryDecision.retryAt(LocalDateTime.now().minusSeconds(1))
                    : DataSyncRetryDecision.stop();
        });

        assertEquals(List.of(1, 2), attempts);
        assertEquals(List.of(DataSyncInstanceStatus.PENDING, DataSyncInstanceStatus.RETRY_WAITING), expected);
        assertEquals(1, lifecycle.retryChecks);
        assertEquals("ws-test", lifecycle.workspaceId);
        assertEquals("root-1", lifecycle.executionId);
        assertNull(WorkspaceContext.getWorkspaceId());
    }

    @Test
    void recoveredRunStartsAtPersistedAttemptWithoutReplayingPreviousAttempts() {
        ExecutorAttemptLifecycleProbe lifecycle = new ExecutorAttemptLifecycleProbe();
        SingleTableRunContext context = SingleTableRunContext.recovery(
                "ws-test", "root-2", snapshot(1, 0), 3, 4, 90, LocalDateTime.now().minusSeconds(1));
        List<Integer> attempts = new ArrayList<>();

        SingleTableAttemptRunner.run(context, lifecycle, (number, status) -> {
            attempts.add(number);
            assertEquals(DataSyncInstanceStatus.RETRY_WAITING, status);
            return DataSyncRetryDecision.stop();
        });

        assertEquals(List.of(3), attempts);
        assertEquals(4, context.maxAttempts());
        assertEquals(90, context.backoffSeconds());
        assertEquals(1, lifecycle.retryChecks);
        assertNull(WorkspaceContext.getWorkspaceId());
    }

    @Test
    void canceledRetryWaitingDoesNotStartTheRecoveredAttempt() {
        ExecutorAttemptLifecycleProbe lifecycle = new ExecutorAttemptLifecycleProbe();
        lifecycle.retryWaiting = false;
        SingleTableRunContext context = SingleTableRunContext.recovery(
                "ws-test", "root-3", snapshot(3, 0), 2, 3, 0, LocalDateTime.now().minusSeconds(1));
        List<Integer> attempts = new ArrayList<>();

        SingleTableAttemptRunner.run(context, lifecycle, (number, status) -> {
            attempts.add(number);
            return DataSyncRetryDecision.stop();
        });

        assertTrue(attempts.isEmpty());
        assertEquals(1, lifecycle.retryChecks);
        assertNull(WorkspaceContext.getWorkspaceId());
    }

    @Test
    void failureAlsoClearsWorkspaceAndInvalidRecoveryIsRejected() {
        ExecutorAttemptLifecycleProbe lifecycle = new ExecutorAttemptLifecycleProbe();
        SingleTableRunContext context = SingleTableRunContext.submission("ws-test", "root-4", snapshot(3, 0));

        assertThrows(IllegalStateException.class, () -> SingleTableAttemptRunner.run(context, lifecycle, (number, status) -> {
            throw new IllegalStateException("test callback failure");
        }));
        assertNull(WorkspaceContext.getWorkspaceId());
        assertThrows(
                IllegalArgumentException.class,
                () -> SingleTableRunContext.recovery(
                        "ws-test", "root-4", snapshot(3, 0), 1, 3, 0, LocalDateTime.now()));
        assertThrows(
                IllegalArgumentException.class,
                () -> SingleTableRunContext.recovery("ws-test", "root-4", snapshot(3, 0), 4, 3, 0, null));
    }

    @Test
    void failureRecordingPreservesAttemptMetricsAndFrozenRetryParameters() {
        ExecutorAttemptLifecycleProbe lifecycle = new ExecutorAttemptLifecycleProbe();
        SingleTableRunContext context = SingleTableRunContext.submission("ws-test", "root-5", snapshot(3, 15));
        DataSyncAttemptEntity attempt = new DataSyncAttemptEntity();
        attempt.setId("attempt-2");
        AttemptFailureDetails details = new AttemptFailureDetails(
                DataSyncAttemptStatus.RUNNING,
                DataSyncInstanceStatus.RUNNING,
                new ExecutionMetrics(17, 11),
                new IllegalStateException("transient"),
                "password=******");

        DataSyncRetryDecision decision = SingleTableAttemptFailureRecorder.record(
                lifecycle, context, attempt, 2, details, DataSyncRetryAssessment.retryable("连接可恢复"));

        assertFalse(decision.retry());
        assertEquals("ws-test", lifecycle.workspaceId);
        assertEquals("root-5", lifecycle.executionId);
        assertEquals("attempt-2", lifecycle.attemptId);
        assertEquals(2, lifecycle.attemptNo);
        assertEquals(3, lifecycle.maxAttempts);
        assertEquals(30, lifecycle.backoffSeconds);
        assertEquals(17, lifecycle.readRows);
        assertEquals(11, lifecycle.writeRows);
        assertTrue(lifecycle.retryAllowed);
        assertEquals("连接可恢复", lifecycle.retryReason);
        assertEquals(DataSyncAttemptStatus.RUNNING, lifecycle.expectedAttemptStatus);
        assertEquals(DataSyncInstanceStatus.RUNNING, lifecycle.expectedExecutionStatus);
        assertEquals(DataSyncErrorCode.EXECUTION_FAILED.getCode(), lifecycle.errorCode);
        assertEquals("password=******", lifecycle.errorMessage);
    }

    private DataSyncDefinitionSnapshotVO snapshot(int maxAttempts, int backoffSeconds) {
        DataSyncDefinitionSnapshotVO snapshot = new DataSyncDefinitionSnapshotVO();
        DataSyncRetryPolicyVO policy = new DataSyncRetryPolicyVO();
        policy.setMode(DataSyncRetryPolicyMode.SMART);
        policy.setMaxAttempts(maxAttempts);
        policy.setBackoffSeconds(backoffSeconds);
        snapshot.setRetryPolicy(policy);
        return snapshot;
    }
}
