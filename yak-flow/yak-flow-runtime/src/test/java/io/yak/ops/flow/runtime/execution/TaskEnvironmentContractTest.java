package io.yak.ops.flow.runtime.execution;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNotEquals;
import static org.junit.jupiter.api.Assertions.assertNotSame;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

import io.yak.ops.core.api.common.JobID;
import io.yak.ops.core.api.connector.source.InputStatus;
import io.yak.ops.core.configuration.Configuration;
import io.yak.ops.core.configuration.CoreOptions;
import io.yak.ops.flow.runtime.tasks.StreamTask;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.atomic.AtomicBoolean;
import java.util.concurrent.atomic.AtomicReference;
import org.junit.jupiter.api.Test;

class TaskEnvironmentContractTest {

    @Test
    void shouldValidateTaskIdentity() {
        JobID jobID = JobID.generate();

        assertThrows(NullPointerException.class, () -> new RuntimeTaskInfo(null, 1, 0, 1, 0));
        assertThrows(IllegalArgumentException.class, () -> new RuntimeTaskInfo(jobID, 0, 0, 1, 0));
        assertThrows(IllegalArgumentException.class, () -> new RuntimeTaskInfo(jobID, 1, 0, 0, 0));
        assertThrows(IllegalArgumentException.class, () -> new RuntimeTaskInfo(jobID, 1, -1, 2, 0));
        assertThrows(IllegalArgumentException.class, () -> new RuntimeTaskInfo(jobID, 1, 2, 2, 0));
        assertThrows(IllegalArgumentException.class, () -> new RuntimeTaskInfo(jobID, 1, 0, 2, -1));

        RuntimeTaskInfo first = new RuntimeTaskInfo(jobID, 12, 1, 3, 0);
        RuntimeTaskInfo retry = new RuntimeTaskInfo(jobID, 12, 1, 3, 1);
        io.yak.ops.core.api.common.TaskInfo publicView = first;
        assertEquals(1, publicView.getIndexOfThisSubtask());
        assertEquals(3, publicView.getNumberOfParallelSubtasks());
        assertEquals(0, publicView.getAttemptNumber());
        assertEquals(1, first.subtaskIndex());
        assertEquals(3, first.parallelism());
        assertNotEquals(first, retry);
        assertNotEquals(first.threadName(), retry.threadName());
        assertTrue(first.threadName().contains(jobID.toHexString()));
    }

    @Test
    void shouldKeepTaskConfigurationIndependentOfExternalMutation() {
        RuntimeTaskInfo info = new RuntimeTaskInfo(JobID.generate(), 3, 1, 2, 0);
        Configuration original = new Configuration();
        original.set(CoreOptions.DEFAULT_PARALLELISM, 8);

        TaskEnvironment environment = new TaskEnvironment(info, original);
        original.set(CoreOptions.DEFAULT_PARALLELISM, 16);

        Configuration copy = environment.configuration();
        assertNotSame(copy, environment.configuration());
        copy.set(CoreOptions.DEFAULT_PARALLELISM, 32);

        assertEquals(8, (int) environment.configuration().get(CoreOptions.DEFAULT_PARALLELISM));
        assertEquals(2, environment.taskInfo().parallelism());
        assertEquals(info, environment.taskInfo());
        assertThrows(NullPointerException.class, () -> new TaskEnvironment(null, original));
        assertThrows(NullPointerException.class, () -> new TaskEnvironment(info, null));
    }

    @Test
    void shouldBindCancellationWithoutMutatingTheOriginalContext() {
        TaskEnvironment environment = new TaskEnvironment(
                new RuntimeTaskInfo(JobID.generate(), 1, 0, 1, 0), new Configuration());
        AtomicBoolean cancelled = new AtomicBoolean();
        TaskEnvironment scoped = environment.withCancellation(cancelled::get);

        assertFalse(environment.isCancellationRequested());
        assertFalse(scoped.isCancellationRequested());
        cancelled.set(true);
        assertTrue(scoped.isCancellationRequested());
        assertFalse(environment.isCancellationRequested());
        assertThrows(NullPointerException.class, () -> environment.withCancellation(null));
    }

    @Test
    void shouldProvideTaskBoundCancellationAndDiagnosticIdentity() throws Exception {
        RuntimeTaskInfo info = new RuntimeTaskInfo(JobID.generate(), 6, 0, 2, 3);
        TaskEnvironment original = new TaskEnvironment(info, new Configuration());
        ProbeTask task = new ProbeTask(original);

        task.start().get(5, TimeUnit.SECONDS);
        assertEquals(info, task.taskInfo());
        assertEquals(info.threadName(), task.threadName.get());
        assertFalse(task.context().isCancellationRequested());

        task.cancelAsync().get(5, TimeUnit.SECONDS);
        assertTrue(task.context().isCancellationRequested());
        assertFalse(original.isCancellationRequested());
        assertTrue(task.closed.get());
        assertTrue(task.completionFuture().isCompletedExceptionally());
    }

    private static final class ProbeTask extends StreamTask {

        private final AtomicReference<String> threadName = new AtomicReference<>();
        private final AtomicBoolean closed = new AtomicBoolean();
        private final CompletableFuture<Void> available = new CompletableFuture<>();

        private ProbeTask(TaskEnvironment environment) {
            super(environment);
        }

        private TaskEnvironment context() {
            return taskEnvironment();
        }

        @Override
        protected void openTask() {
            threadName.set(Thread.currentThread().getName());
        }

        @Override
        protected InputStatus processInput() {
            return InputStatus.NOTHING_AVAILABLE;
        }

        @Override
        protected CompletableFuture<Void> getAvailableFuture() {
            return available;
        }

        @Override
        protected void closeTask() {
            closed.set(true);
        }
    }
}
