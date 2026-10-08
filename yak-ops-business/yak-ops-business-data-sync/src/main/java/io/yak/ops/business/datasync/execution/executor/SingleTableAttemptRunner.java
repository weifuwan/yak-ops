package io.yak.ops.business.datasync.execution.executor;

import io.yak.ops.business.datasync.execution.lifecycle.DataSyncAttemptLifecycle;
import io.yak.ops.business.datasync.execution.lifecycle.DataSyncRetryDecision;
import io.yak.ops.common.context.WorkspaceContext;
import io.yak.ops.common.enums.datasync.DataSyncInstanceStatus;
import java.time.Duration;
import java.time.LocalDateTime;
import java.util.function.BiFunction;

/**
 * 单表 OFFLINE / REALTIME 共用的 Attempt 循环和持久化 Retry 等待，不拥有 Runtime 或状态迁移。
 *
 * @author weifuwan
 * @since 2026-10-08
 */
final class SingleTableAttemptRunner {

    private SingleTableAttemptRunner() {}

    static void start(
            SingleTableRunContext context,
            String threadPrefix,
            DataSyncAttemptLifecycle lifecycle,
            BiFunction<Integer, DataSyncInstanceStatus, DataSyncRetryDecision> attempt) {
        Thread.ofVirtual().name(threadPrefix + context.executionId()).start(() -> run(context, lifecycle, attempt));
    }

    static void run(
            SingleTableRunContext context,
            DataSyncAttemptLifecycle lifecycle,
            BiFunction<Integer, DataSyncInstanceStatus, DataSyncRetryDecision> attempt) {
        WorkspaceContext.bind(context.workspaceId());
        try {
            if (context.initialRetryTime() != null
                    && !waitForRetry(context, lifecycle, context.initialRetryTime())) return;

            DataSyncInstanceStatus expectedStatus = context.expectedExecutionStatus();
            for (int attemptNo = context.firstAttemptNo(); attemptNo <= context.maxAttempts(); attemptNo++) {
                DataSyncRetryDecision decision = attempt.apply(attemptNo, expectedStatus);
                if (!decision.retry()) return;
                if (!waitForRetry(context, lifecycle, decision.nextRetryTime())) return;
                expectedStatus = DataSyncInstanceStatus.RETRY_WAITING;
            }
        } finally {
            WorkspaceContext.clear();
        }
    }

    private static boolean waitForRetry(
            SingleTableRunContext context, DataSyncAttemptLifecycle lifecycle, LocalDateTime nextRetryTime) {
        if (nextRetryTime == null) return false;
        long delayMillis = Math.max(0L, Duration.between(LocalDateTime.now(), nextRetryTime).toMillis());
        try {
            if (delayMillis > 0) Thread.sleep(delayMillis);
        } catch (InterruptedException exception) {
            Thread.currentThread().interrupt();
            return false;
        }
        return lifecycle.isRetryWaiting(context.workspaceId(), context.executionId());
    }
}
