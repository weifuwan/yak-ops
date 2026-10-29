package io.yak.ops.worker.executor;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.mockito.Mockito.mock;

import io.yak.ops.plugin.task.api.TaskPluginRegistry;
import io.yak.ops.plugin.task.api.execution.TaskOutcome;
import io.yak.ops.worker.task.executor.TaskEngine;
import io.yak.ops.worker.task.executor.TaskExecutionContext;
import io.yak.ops.worker.task.executor.TaskExecutorState;
import io.yak.ops.worker.task.executor.ITaskExecutor;
import io.yak.ops.worker.task.executor.events.TaskExecutorKillLifecycleEvent;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.CompletionStage;
import java.util.concurrent.atomic.AtomicInteger;
import org.junit.jupiter.api.Test;

/** Physical Worker tests for exclusive slots, durable terminal ACK, cancellation and unknown outcomes. */
class PhysicalTaskEngineTest {
    @Test
    void exclusiveSlotIsRetainedUntilTaskCompletionAndCanThenBeReused() throws Exception {
        PhysicalTaskExecutorRepository repository = new PhysicalTaskExecutorRepository();
        PhysicalTaskExecutorContainerProvider containers = new PhysicalTaskExecutorContainerProvider(1);
        TaskEngine engine = new PhysicalTaskEngineFactory().createTaskEngine(repository, containers);
        engine.start();
        try {
            CompletableFuture<TaskOutcome> first = new CompletableFuture<>();
            PhysicalTaskExecutor task1 = task("instance-1", first, new AtomicInteger());
            engine.submitTask(task1);
            assertTrue(repository.contains("instance-1"));
            PhysicalTaskExecutor task2 = task("instance-2",
                    CompletableFuture.completedFuture(TaskOutcome.FINISHED), new AtomicInteger());
            assertThrows(RuntimeException.class, () -> engine.submitTask(task2));
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
    void duplicateSubmissionDoesNotReplaceTheOriginal() throws Exception {
        PhysicalTaskExecutorRepository repository = new PhysicalTaskExecutorRepository();
        PhysicalTaskExecutorContainerProvider containers = new PhysicalTaskExecutorContainerProvider(1);
        TaskEngine engine = new PhysicalTaskEngineFactory().createTaskEngine(repository, containers);
        engine.start();
        try {
            CompletableFuture<TaskOutcome> pending = new CompletableFuture<>();
            PhysicalTaskExecutor original = task("instance", pending, new AtomicInteger());
            engine.submitTask(original);
            assertThrows(IllegalArgumentException.class,
                    () -> engine.submitTask(task("instance", pending, new AtomicInteger())));
            assertEquals(original, repository.get("instance").orElseThrow());
            pending.complete(TaskOutcome.FINISHED);
            awaitReleased(repository, "instance");
        } finally {
            engine.close();
        }
    }

    @Test
    void killIsForwardedOnce() throws Exception {
        PhysicalTaskExecutorRepository repository = new PhysicalTaskExecutorRepository();
        PhysicalTaskExecutorContainerProvider containers = new PhysicalTaskExecutorContainerProvider(1);
        TaskEngine engine = new PhysicalTaskEngineFactory().createTaskEngine(repository, containers);
        engine.start();
        try {
            CompletableFuture<TaskOutcome> completion = new CompletableFuture<>();
            AtomicInteger kills = new AtomicInteger();
            PhysicalTaskExecutor task = task("instance", completion, kills);
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

    @Test
    void uncertainOutcomeNeverEmitsTerminalAckOrReleasesSlot() throws Exception {
        PhysicalTaskExecutorRepository repository = new PhysicalTaskExecutorRepository();
        PhysicalTaskExecutorContainerProvider containers = new PhysicalTaskExecutorContainerProvider(1);
        TaskEngine engine = new PhysicalTaskEngineFactory().createTaskEngine(repository, containers);
        engine.start();
        try {
            CompletableFuture<TaskOutcome> outcome = new CompletableFuture<>();
            PhysicalTaskExecutor task = task("unknown", outcome, new AtomicInteger());
            engine.submitTask(task);
            awaitStarted(task);
            outcome.completeExceptionally(new IllegalStateException("Engine submission outcome unknown"));
            for (int i = 0; i < 20 && !task.isUncertainOutcome(); i++) Thread.sleep(10);
            assertTrue(task.isUncertainOutcome());
            assertTrue(repository.contains("unknown"));
            assertEquals(1.0, containers.getExecutorContainer().slotUsage(), 0.01);
        } finally {
            engine.close();
        }
    }

    @Test
    void requeuedLifecycleEventIsDelayedInsteadOfSpinning() {
        PhysicalTaskExecutor executor = task(
                "retry", new CompletableFuture<>(), new AtomicInteger());
        TaskExecutorKillLifecycleEvent event = TaskExecutorKillLifecycleEvent.of(executor);
        event.retryAfter(1_000);
        assertTrue(event.getDelay(TimeUnit.MILLISECONDS) > 0);
    }

    @Test
    void resolvesRegisteredPluginOnlyAfterMasterPreparationSetsTaskType() {
        TaskPluginRegistry registry = mock(TaskPluginRegistry.class);
        io.yak.ops.plugin.task.api.execution.ExecutableTaskPlugin channel =
                mock(io.yak.ops.plugin.task.api.execution.ExecutableTaskPlugin.class);
        org.mockito.Mockito.when(registry.get("DATA_SYNC")).thenReturn(channel);
        TaskExecutionContext context = new TaskExecutionContext("workspace", "plugin");
        PhysicalTaskPluginFactory factory = new PhysicalTaskPluginFactory(registry, (ctx, lazyChannel) -> {
            ctx.setTaskType("DATA_SYNC");
            assertEquals(channel, lazyChannel.get());
            return new PhysicalTask() {
                @Override public CompletionStage<TaskOutcome> handle() {
                    return CompletableFuture.completedFuture(TaskOutcome.FINISHED);
                }
                @Override public void cancel() { }
            };
        });
        PhysicalTaskExecutor executor =
                (PhysicalTaskExecutor) new PhysicalTaskExecutorFactory(factory).createTaskExecutor(context);
        executor.start();
        assertEquals(TaskExecutorState.SUCCEEDED, executor.trackTaskExecutorState());
    }

    private static PhysicalTaskExecutor task(
            String id, CompletionStage<TaskOutcome> completion, AtomicInteger kills) {
        TaskExecutionContext context = new TaskExecutionContext("workspace", id);
        PhysicalTaskPluginFactory factory = new PhysicalTaskPluginFactory(
                mock(TaskPluginRegistry.class), (ignored, taskChannel) -> new PhysicalTask() {
            @Override public CompletionStage<TaskOutcome> handle() { return completion; }
            @Override public void cancel() {
                kills.incrementAndGet();
                if (completion instanceof CompletableFuture) {
                    ((CompletableFuture<TaskOutcome>) completion).complete(TaskOutcome.CANCELED);
                }
            }
        });
        ITaskExecutor executor = new PhysicalTaskExecutorFactory(factory).createTaskExecutor(context);
        return (PhysicalTaskExecutor) executor;
    }

    private static void awaitStarted(PhysicalTaskExecutor executor) throws InterruptedException {
        for (int attempt = 0; attempt < 200 && !executor.isStarted(); attempt++) Thread.sleep(10);
        assertTrue(executor.isStarted(), "Physical Task should start");
    }

    private static void awaitReleased(PhysicalTaskExecutorRepository repository, String id)
            throws InterruptedException {
        for (int attempt = 0; attempt < 500 && repository.contains(id); attempt++) Thread.sleep(10);
        assertTrue(!repository.contains(id), "Durable terminal ACK must release the Worker slot");
    }
}
