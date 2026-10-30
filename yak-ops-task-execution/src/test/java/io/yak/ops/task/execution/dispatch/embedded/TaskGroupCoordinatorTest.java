package io.yak.ops.task.execution.dispatch.embedded;

import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import io.yak.ops.dao.entity.task.TaskGroupQueueEntity;
import io.yak.ops.dao.repository.task.TaskGroupQueueRepository;
import io.yak.ops.task.execution.dispatch.TaskExecutionCoordinator;
import io.yak.ops.task.execution.owner.EmbeddedExecutionOwner;
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
        when(queues.waiting(1000)).thenReturn(List.of(first, secondResource, third));
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

    private static TaskGroupQueueEntity queue(String instanceId) {
        TaskGroupQueueEntity queue = new TaskGroupQueueEntity();
        queue.setWorkspaceId("ws");
        queue.setInstanceId(instanceId);
        return queue;
    }
}
