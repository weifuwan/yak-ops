package io.yak.ops.flow.runtime.executiongraph;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertSame;
import static org.junit.jupiter.api.Assertions.assertTrue;

import java.io.IOException;
import java.util.ArrayList;
import java.util.List;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.atomic.AtomicInteger;
import org.junit.jupiter.api.Test;

/** Deployment cleanup retains ownership until every asynchronous shutdown has completed. */
class TaskDeploymentCleanupTest {

    @Test
    void clearsPendingInterruptUntilEveryResourceHasClosed() {
        List<String> closed = new ArrayList<>();
        AutoCloseable task = () -> {
            if (Thread.currentThread().isInterrupted()) {
                throw new InterruptedException("Task shutdown was interrupted");
            }
            closed.add("task");
        };
        AutoCloseable coordinator = () -> {
            if (Thread.currentThread().isInterrupted()) {
                throw new InterruptedException("Coordinator shutdown was interrupted");
            }
            closed.add("coordinator");
        };

        Thread.currentThread().interrupt();
        try {
            assertNull(TaskDeployment.closeAll(List.of(task, coordinator)));
            assertEquals(List.of("task", "coordinator"), closed);
            assertTrue(Thread.currentThread().isInterrupted());
        } finally {
            Thread.interrupted();
        }
    }

    @Test
    void interruptedWaitStillBlocksUntilShutdownCompletes() throws Exception {
        CountDownLatch entered = new CountDownLatch(1);
        CountDownLatch retried = new CountDownLatch(1);
        CompletableFuture<Void> release = new CompletableFuture<>();
        CompletableFuture<Boolean> finished = new CompletableFuture<>();
        AtomicInteger attempts = new AtomicInteger();
        AtomicInteger closed = new AtomicInteger();
        AutoCloseable task = () -> {
            if (attempts.incrementAndGet() == 1) {
                entered.countDown();
            } else {
                retried.countDown();
            }
            try {
                release.get();
            } catch (InterruptedException failure) {
                Thread.currentThread().interrupt();
                throw failure;
            }
            closed.incrementAndGet();
        };
        AutoCloseable coordinator = () -> closed.incrementAndGet();
        Thread worker = Thread.ofPlatform().daemon(true).unstarted(() -> {
            Throwable failure = TaskDeployment.closeAll(List.of(task, coordinator));
            if (failure == null) {
                finished.complete(Thread.currentThread().isInterrupted());
            } else {
                finished.completeExceptionally(failure);
            }
        });
        worker.start();
        try {
            assertTrue(entered.await(5, TimeUnit.SECONDS));
            worker.interrupt();
            assertTrue(retried.await(5, TimeUnit.SECONDS));
            assertFalse(finished.isDone());
            assertEquals(0, closed.get());
            release.complete(null);
            assertTrue(finished.get(5, TimeUnit.SECONDS));
            assertEquals(2, attempts.get());
            assertEquals(2, closed.get());
        } finally {
            release.complete(null);
            worker.join(5000);
        }
        assertFalse(worker.isAlive());
    }

    @Test
    void preservesCleanupFailuresAndContinuesClosingRemainingResources() {
        IOException first = new IOException("Task cleanup failed");
        IllegalStateException second = new IllegalStateException("Coordinator cleanup failed");
        AtomicInteger closed = new AtomicInteger();
        AutoCloseable task = () -> {
            throw first;
        };
        AutoCloseable coordinator = () -> {
            throw second;
        };
        AutoCloseable store = () -> closed.incrementAndGet();

        Throwable failure = TaskDeployment.closeAll(List.of(task, coordinator, store));

        assertSame(first, failure);
        assertEquals(1, failure.getSuppressed().length);
        assertSame(second, failure.getSuppressed()[0]);
        assertEquals(1, closed.get());
    }
}
