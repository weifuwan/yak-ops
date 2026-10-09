package io.yak.ops.business.datasync.history;

import static org.junit.jupiter.api.Assertions.assertEquals;

import io.yak.ops.common.enums.datasync.DataSyncInstanceStatus;
import io.yak.ops.dao.entity.datasync.DataSyncInstanceEntity;
import io.yak.ops.dao.repository.datasync.DataSyncAttemptRepository;
import io.yak.ops.dao.repository.datasync.DataSyncInstanceRepository;
import io.yak.ops.dao.repository.datasync.DataSyncTableAttemptRepository;
import io.yak.ops.dao.repository.datasync.DataSyncTableExecutionRepository;
import java.lang.reflect.Field;
import java.lang.reflect.Proxy;
import java.util.List;
import java.util.concurrent.atomic.AtomicInteger;
import org.junit.jupiter.api.Test;

class DataSyncHistoryRecoveryTest {

    @Test
    void abandonedPendingAndRetryWaitingExecutionsMustBeClosedWithoutLaunchingJobs() throws Exception {
        DataSyncInstanceEntity pending = execution("pending", DataSyncInstanceStatus.PENDING);
        DataSyncInstanceEntity waiting = execution("waiting", DataSyncInstanceStatus.RETRY_WAITING);
        AtomicInteger unfinishedTables = new AtomicInteger();
        DataSyncHistoryRecovery recovery = new DataSyncHistoryRecovery();

        inject(recovery, "instanceRepository", instanceRepository(pending, waiting));
        inject(recovery, "attemptRepository", repository(DataSyncAttemptRepository.class, "markActiveAsLost"));
        inject(recovery, "tableAttemptRepository", repository(DataSyncTableAttemptRepository.class, "markActiveAsLost"));
        inject(recovery, "tableExecutionRepository", (DataSyncTableExecutionRepository) Proxy.newProxyInstance(
                DataSyncTableExecutionRepository.class.getClassLoader(),
                new Class<?>[] {DataSyncTableExecutionRepository.class},
                (proxy, method, args) -> {
                    if ("finishUnfinished".equals(method.getName())) {
                        unfinishedTables.incrementAndGet();
                        return 0;
                    }
                    throw new AssertionError("No runtime operation expected: " + method.getName());
                }));

        recovery.closeAbandonedExecutions();

        assertEquals(DataSyncInstanceStatus.LOST, pending.getStatus());
        assertEquals(DataSyncInstanceStatus.LOST, waiting.getStatus());
        assertEquals(2, unfinishedTables.get());
    }

    private DataSyncInstanceRepository instanceRepository(
            DataSyncInstanceEntity pending, DataSyncInstanceEntity waiting) {
        return (DataSyncInstanceRepository) Proxy.newProxyInstance(
                DataSyncInstanceRepository.class.getClassLoader(),
                new Class<?>[] {DataSyncInstanceRepository.class},
                (proxy, method, args) -> {
                    if ("queryActive".equals(method.getName())) return List.of(pending, waiting);
                    if ("markActiveAsLost".equals(method.getName())) {
                        pending.setStatus(DataSyncInstanceStatus.LOST);
                        return 1;
                    }
                    if ("transitionStatus".equals(method.getName())) {
                        waiting.setStatus(DataSyncInstanceStatus.LOST);
                        return true;
                    }
                    throw new AssertionError("No runtime operation expected: " + method.getName());
                });
    }

    @SuppressWarnings("unchecked")
    private static <T> T repository(Class<T> repositoryType, String allowedMethod) {
        return (T) Proxy.newProxyInstance(
                repositoryType.getClassLoader(), new Class<?>[] {repositoryType},
                (proxy, method, args) -> {
                    if (allowedMethod.equals(method.getName())) return 1;
                    throw new AssertionError("Unexpected call: " + method.getName());
                });
    }

    private static DataSyncInstanceEntity execution(String id, DataSyncInstanceStatus status) {
        DataSyncInstanceEntity value = new DataSyncInstanceEntity();
        value.setId(id);
        value.setWorkspaceId("workspace-1");
        value.setStatus(status);
        return value;
    }

    private static void inject(DataSyncHistoryRecovery service, String field, Object dependency) throws Exception {
        Field target = DataSyncHistoryRecovery.class.getDeclaredField(field);
        target.setAccessible(true);
        target.set(service, dependency);
    }
}
