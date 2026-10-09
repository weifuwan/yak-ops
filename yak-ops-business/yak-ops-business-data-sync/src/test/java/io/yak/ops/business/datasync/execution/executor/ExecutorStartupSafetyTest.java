package io.yak.ops.business.datasync.execution.executor;

import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

import io.yak.ops.business.datasync.execution.lifecycle.DataSyncExecutionRegistry;
import io.yak.ops.common.bean.vo.datasync.DataSyncDefinitionSnapshotVO;
import java.lang.reflect.Field;
import java.util.concurrent.TimeUnit;
import org.junit.jupiter.api.Test;

class ExecutorStartupSafetyTest {

    @Test
    void offlineCanceledDuringStartupDoesNotReachRuntimePlanning() throws Exception {
        ExecutorStartupLifecycleProbe lifecycle = new ExecutorStartupLifecycleProbe();
        lifecycle.startAccepted = true;
        DataSyncExecutionRegistry registry = new DataSyncExecutionRegistry();
        OfflineSyncExecutor executor = new OfflineSyncExecutor();
        inject(executor, "attemptLifecycle", lifecycle);
        inject(executor, "executionRegistry", registry);
        executor.submit("workspace-1", "offline-1", new DataSyncDefinitionSnapshotVO());

        assertTrue(lifecycle.enteredStart.await(5, TimeUnit.SECONDS));
        assertTrue(registry.cancel("offline-1"));
        lifecycle.continueStart.countDown();
        assertTrue(lifecycle.cleaned.await(5, TimeUnit.SECONDS));
    }

    @Test
    void realtimeCanceledDuringStartupDoesNotAllocateCdcServerId() throws Exception {
        ExecutorStartupLifecycleProbe lifecycle = new ExecutorStartupLifecycleProbe();
        lifecycle.startAccepted = true;
        DataSyncExecutionRegistry registry = new DataSyncExecutionRegistry();
        RealtimeSyncExecutor executor = new RealtimeSyncExecutor();
        inject(executor, "attemptLifecycle", lifecycle);
        inject(executor, "executionRegistry", registry);
        executor.submit("workspace-1", "realtime-1", new DataSyncDefinitionSnapshotVO());

        assertTrue(lifecycle.enteredStart.await(5, TimeUnit.SECONDS));
        assertTrue(registry.cancel("realtime-1"));
        lifecycle.continueStart.countDown();
        assertTrue(lifecycle.cleaned.await(5, TimeUnit.SECONDS));
    }

    @Test
    void rejectedAttemptTransitionDoesNotStartRuntime() throws Exception {
        ExecutorStartupLifecycleProbe lifecycle = new ExecutorStartupLifecycleProbe();
        lifecycle.startAccepted = false;
        DataSyncExecutionRegistry registry = new DataSyncExecutionRegistry();
        OfflineSyncExecutor executor = new OfflineSyncExecutor();
        inject(executor, "attemptLifecycle", lifecycle);
        inject(executor, "executionRegistry", registry);
        executor.submit("workspace-1", "offline-rejected", new DataSyncDefinitionSnapshotVO());

        assertTrue(lifecycle.enteredStart.await(5, TimeUnit.SECONDS));
        lifecycle.continueStart.countDown();
        assertTrue(lifecycle.cleaned.await(5, TimeUnit.SECONDS));
        assertFalse(lifecycle.startAccepted);
    }

    private static void inject(Object target, String fieldName, Object value) throws Exception {
        Field field = target.getClass().getDeclaredField(fieldName);
        field.setAccessible(true);
        field.set(target, value);
    }
}
