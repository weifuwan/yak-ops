package io.yak.ops.task.execution.dispatch.embedded;

import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import io.yak.ops.dao.entity.task.TaskGroupQueueEntity;
import io.yak.ops.dao.repository.task.TaskGroupQueueRepository;
import io.yak.ops.task.execution.dispatch.TaskExecutionCoordinator;
import io.yak.ops.task.execution.owner.EmbeddedExecutionOwner;
import java.time.LocalDateTime;
import java.util.ArrayList;
import java.util.List;
import org.junit.jupiter.api.Test;

/** Master-to-Worker notification is allowed only after durable queue acquisition. */
class TaskGroupCoordinatorTest {

    @Test
    void dispatchesEachReadyInstanceOnlyOnceAcrossMultipleQueueRows() {
        EmbeddedExecutionOwner owner = mock(EmbeddedExecutionOwner.class);
        TaskGroupQueueRepository queues = mock(TaskGroupQueueRepository.class);
        TaskExecutionCoordinator dispatcher = mock(TaskExecutionCoordinator.class);
        TaskGroupQueueEntity first = queue("run-1");
        TaskGroupQueueEntity secondResource = queue("run-1");
        TaskGroupQueueEntity third = queue("run-2");
        when(owner.incarnation()).thenReturn("owner");
        when(queues.waiting(1000, null)).thenReturn(List.of(first, secondResource, third));
        when(queues.tryAcquire("ws", "run-1")).thenReturn(true);
        when(queues.tryAcquire("ws", "run-2")).thenReturn(false);

        TaskGroupCoordinator coordinator = new TaskGroupCoordinator(owner, queues, dispatcher);
        try {
            coordinator.scanWaiting();
            verify(queues).tryAcquire("ws", "run-1");
            verify(queues).tryAcquire("ws", "run-2");
            verify(dispatcher).dispatch("ws", "run-1");
            verify(dispatcher, never()).dispatch("ws", "run-2");
        } finally {
            coordinator.close();
        }
    }

    @Test
    void continuesPastThousandBlockedEntriesOnNextTick() {
        EmbeddedExecutionOwner owner = mock(EmbeddedExecutionOwner.class);
        TaskGroupQueueRepository queues = mock(TaskGroupQueueRepository.class);
        TaskExecutionCoordinator dispatcher = mock(TaskExecutionCoordinator.class);
        List<TaskGroupQueueEntity> blocked = new ArrayList<>();
        for (int i = 0; i < 1000; i++) {
            TaskGroupQueueEntity row = queue("blocked-" + i);
            row.setId(String.format("%05d", i));
            row.setPriority(10);
            row.setCreateTime(LocalDateTime.of(2026, 10, 30, 0, 0));
            blocked.add(row);
        }
        TaskGroupQueueEntity free = queue("free-resource");
        free.setId("last");
        free.setPriority(0);
        free.setCreateTime(LocalDateTime.of(2026, 10, 30, 0, 1));
        when(owner.incarnation()).thenReturn("owner");
        when(queues.waiting(1000, null)).thenReturn(blocked);
        when(queues.waiting(1000, blocked.get(999))).thenReturn(List.of(free));
        when(queues.tryAcquire("ws", "free-resource")).thenReturn(true);

        TaskGroupCoordinator coordinator = new TaskGroupCoordinator(owner, queues, dispatcher);
        try {
            coordinator.scanWaiting();
            verify(dispatcher, never()).dispatch("ws", "free-resource");
            coordinator.scanWaiting();
            verify(dispatcher).dispatch("ws", "free-resource");
        } finally {
            coordinator.close();
        }
    }

    @Test
    void failedInstanceDoesNotPreventDispatchingNextReadyInstance() {
        EmbeddedExecutionOwner owner = mock(EmbeddedExecutionOwner.class);
        TaskGroupQueueRepository queues = mock(TaskGroupQueueRepository.class);
        TaskExecutionCoordinator dispatcher = mock(TaskExecutionCoordinator.class);
        when(owner.incarnation()).thenReturn("owner");
        when(queues.waiting(1000, null)).thenReturn(List.of(queue("broken"), queue("ready")));
        when(queues.tryAcquire("ws", "broken")).thenThrow(new IllegalStateException("invalid queue"));
        when(queues.tryAcquire("ws", "ready")).thenReturn(true);
        TaskGroupCoordinator coordinator = new TaskGroupCoordinator(owner, queues, dispatcher);
        try {
            coordinator.scanWaiting();
            verify(dispatcher).dispatch("ws", "ready");
        } finally {
            coordinator.close();
        }
    }

    private static TaskGroupQueueEntity queue(String instanceId) {
        TaskGroupQueueEntity queue = new TaskGroupQueueEntity();
        queue.setWorkspaceId("ws");
        queue.setInstanceId(instanceId);
        return queue;
    }
}
