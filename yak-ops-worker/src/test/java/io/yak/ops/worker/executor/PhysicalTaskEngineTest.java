package io.yak.ops.worker.executor;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

import io.yak.ops.plugin.task.api.execution.TaskOutcome;
import io.yak.ops.worker.task.executor.TaskEngine;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.atomic.AtomicInteger;
import org.junit.jupiter.api.Test;

/** Verifies exclusive DolphinScheduler-style execution slots with deferred TaskHandle outcomes. */
class PhysicalTaskEngineTest {

    @Test
    void exclusiveSlotIsRetainedUntilTaskCompletionAndCanThenBeReused() throws Exception {
        PhysicalTaskExecutorRepository repository = new PhysicalTaskExecutorRepository();
        PhysicalTaskExecutorContainerProvider containers = new PhysicalTaskExecutorContainerProvider(1);
        TaskEngine engine = new PhysicalTaskEngineFactory().createTaskEngine(repository, containers);
        engine.start();
        try {
            CompletableFuture<TaskOutcome> first = new CompletableFuture<>();
            PhysicalTaskExecutor task1 = new PhysicalTaskExecutorFactory()
                    .createTaskExecutor("workspace", "instance-1", () -> first, () -> first.complete(TaskOutcome.CANCELED));
            engine.submitTask(task1);
            assertTrue(repository.contains("instance-1"));

            PhysicalTaskExecutor task2 = new PhysicalTaskExecutorFactory()
                    .createTaskExecutor("workspace", "instance-2",
                            () -> CompletableFuture.completedFuture(TaskOutcome.FINISHED), () -> {});
            assertThrows(IllegalStateException.class, () -> engine.submitTask(task2));
            assertEquals(1.0, containers.getExecutorContainer().slotUsage(), 0.01);

            first.complete(TaskOutcome.FINISHED);
            awaitReleased(repository, "instance-1");
            engine.submitTask(task2);
            awaitReleased(repository, "instance-2");
            assertEquals(0.0, containers.getExecutorContainer().slotUsage(), 0.01);
        } finally {
            engine.close();
        }
    }

    @Test
    void duplicateSubmissionIsRejectedWithoutReplacingTheOriginal() {
        PhysicalTaskExecutorRepository repository = new PhysicalTaskExecutorRepository();
        PhysicalTaskExecutorContainerProvider containers = new PhysicalTaskExecutorContainerProvider(1);
        TaskEngine engine = new PhysicalTaskEngineFactory().createTaskEngine(repository, containers);
        engine.start();
        try {
            CompletableFuture<TaskOutcome> completion = new CompletableFuture<>();
            PhysicalTaskExecutor original = new PhysicalTaskExecutorFactory()
                    .createTaskExecutor("workspace", "instance", () -> completion,
                            () -> completion.complete(TaskOutcome.CANCELED));
            engine.submitTask(original);
            PhysicalTaskExecutor duplicate = new PhysicalTaskExecutorFactory()
                    .createTaskExecutor("workspace", "instance", () -> completion, () -> {});
            assertThrows(IllegalStateException.class, () -> engine.submitTask(duplicate));
            assertEquals(original, repository.get("instance").orElseThrow());
            completion.complete(TaskOutcome.FINISHED);
        } finally {
            engine.close();
        }
    }

    @Test
    void killIsForwardedOnceToThePhysicalTask() throws Exception {
        PhysicalTaskExecutorRepository repository = new PhysicalTaskExecutorRepository();
        PhysicalTaskExecutorContainerProvider containers = new PhysicalTaskExecutorContainerProvider(1);
        TaskEngine engine = new PhysicalTaskEngineFactory().createTaskEngine(repository, containers);
        engine.start();
        try {
            CompletableFuture<TaskOutcome> completion = new CompletableFuture<>();
            AtomicInteger kills = new AtomicInteger();
            PhysicalTaskExecutor task = new PhysicalTaskExecutorFactory()
                    .createTaskExecutor("workspace", "instance", () -> completion, () -> {
                        kills.incrementAndGet();
                        completion.complete(TaskOutcome.CANCELED);
                    });
            engine.submitTask(task);
            awaitStarted(task);
            engine.killTask("instance");
            engine.killTask("instance");
            awaitReleased(repository, "instance");
            assertEquals(1, kills.get());
        } finally {
            engine.close();
        }
    }

    private static void awaitStarted(PhysicalTaskExecutor executor) throws InterruptedException {
        for (int attempt = 0; attempt < 200 && !executor.isStarted(); attempt++) Thread.sleep(10);
        assertTrue(executor.isStarted(), "Task should start");
    }

    private static void awaitReleased(PhysicalTaskExecutorRepository repository, String id)
            throws InterruptedException {
        for (int attempt = 0; attempt < 200 && repository.contains(id); attempt++) Thread.sleep(10);
        assertTrue(!repository.contains(id), "Finalized task must release its Worker slot");
    }
}
