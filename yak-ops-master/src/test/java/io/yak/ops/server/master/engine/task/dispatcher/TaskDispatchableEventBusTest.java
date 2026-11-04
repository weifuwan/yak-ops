package io.yak.ops.server.master.engine.task.dispatcher;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.mockito.Mockito.mock;

import io.yak.ops.server.master.engine.task.dispatcher.event.TaskDispatchableEvent;
import io.yak.ops.server.master.engine.task.execution.ITaskExecution;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

public class TaskDispatchableEventBusTest {

    private TaskDispatchableEventBus<TaskDispatchableEvent<ITaskExecution>, ITaskExecution> queue;
    private ITaskExecution taskExecution;

    @BeforeEach
    public void setUp() {
        queue = new TaskDispatchableEventBus<>();
        taskExecution = mock(ITaskExecution.class);
    }

    @Test
    public void testAdd() {
        queue.add(new TaskDispatchableEvent<>(1000, taskExecution));
        assertEquals(1, queue.size());

        queue.add(new TaskDispatchableEvent<>(2000, taskExecution));
        assertEquals(2, queue.size());
    }

    @Test
    public void testTake() throws InterruptedException {
        queue.add(new TaskDispatchableEvent<>(1000, taskExecution));
        TaskDispatchableEvent<ITaskExecution> entry = queue.take();
        assertNotNull(entry);
        assertEquals(0, queue.size());

    }

    @Test
    public void testSize() {
        assertEquals(0, queue.size());

        queue.add(new TaskDispatchableEvent<>(1000, taskExecution));
        assertEquals(1, queue.size());
    }

    @Test
    public void testClear() {
        queue.add(new TaskDispatchableEvent<>(1000, taskExecution));
        queue.add(new TaskDispatchableEvent<>(2000, taskExecution));
        assertEquals(2, queue.size());

        queue.clear();
        assertEquals(0, queue.size());
    }
}
