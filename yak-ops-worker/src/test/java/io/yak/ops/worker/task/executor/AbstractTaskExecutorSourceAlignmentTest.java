package io.yak.ops.worker.task.executor;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

import io.yak.ops.worker.task.executor.eventbus.TaskExecutorEventBus;
import io.yak.ops.worker.task.executor.events.TaskExecutorLifecycleEventType;
import org.junit.jupiter.api.Test;

/** Locks the upstream RUNNING -> plugin initialization -> trigger order. */
class AbstractTaskExecutorSourceAlignmentTest {

    @Test
    void publishesRunningBeforePhysicalPluginInitialization() {
        TestTaskExecutor executor = new TestTaskExecutor();
        executor.start();
        assertTrue(executor.initialized);
        assertTrue(executor.triggered);
        assertEquals(TaskExecutorState.SUCCEEDED, executor.trackTaskExecutorState());
    }

    private static final class TestTaskExecutor extends AbstractTaskExecutor {
        private boolean initialized;
        private boolean triggered;

        private TestTaskExecutor() {
            super(new TaskExecutionContext("workspace", "task"), new TaskExecutorEventBus());
        }

        @Override
        protected void initializeTaskPlugin() {
            assertEquals(TaskExecutorState.RUNNING, taskExecutorState.get());
            assertEquals(TaskExecutorLifecycleEventType.RUNNING,
                    taskExecutorEventBus.poll().orElseThrow().getType());
            initialized = true;
        }

        @Override
        protected void doTriggerTaskPlugin() {
            assertTrue(initialized);
            triggered = true;
            transitTaskExecutorState(TaskExecutorState.SUCCEEDED);
        }

        @Override
        protected TaskExecutorState doTrackTaskPluginStatus() {
            return taskExecutorState.get();
        }

        @Override
        public void pause() {
            throw new UnsupportedOperationException();
        }

        @Override
        public void kill() {
            transitTaskExecutorState(TaskExecutorState.KILLED);
        }

        @Override
        public void finalizeTask() { }
    }
}
