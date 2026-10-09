package io.yak.ops.business.datasync.execution.executor;

import io.yak.ops.business.datasync.execution.lifecycle.DataSyncAttemptLifecycle;
import io.yak.ops.common.enums.datasync.DataSyncInstanceStatus;
import io.yak.ops.dao.entity.datasync.DataSyncAttemptEntity;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.TimeUnit;

/**
 * 单表启动竞态测试的可控 Lifecycle 替身。
 */
final class ExecutorStartupLifecycleProbe extends DataSyncAttemptLifecycle {

    final CountDownLatch enteredStart = new CountDownLatch(1);
    final CountDownLatch continueStart = new CountDownLatch(1);
    final CountDownLatch cleaned = new CountDownLatch(1);
    volatile boolean startAccepted;

    @Override
    public DataSyncAttemptEntity createAttempt(String workspaceId, String executionId, int attemptNo) {
        DataSyncAttemptEntity attempt = new DataSyncAttemptEntity();
        attempt.setId(executionId + "-attempt-" + attemptNo);
        return attempt;
    }

    @Override
    public boolean startAttempt(
            String workspaceId,
            String executionId,
            String attemptId,
            int attemptNo,
            DataSyncInstanceStatus expectedExecutionStatus) {
        enteredStart.countDown();
        try {
            if (!continueStart.await(5, TimeUnit.SECONDS)) {
                throw new IllegalStateException("startup test did not release the lifecycle");
            }
            return startAccepted;
        } catch (InterruptedException exception) {
            Thread.currentThread().interrupt();
            return false;
        }
    }

    @Override
    public void cancelActiveAttempt(String workspaceId, String executionId) {
        cleaned.countDown();
    }
}
