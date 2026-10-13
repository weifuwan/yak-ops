package io.yak.ops.flow.runtime.tasks;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertSame;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

import io.yak.ops.configuration.Configuration;
import io.yak.ops.core.api.common.JobID;
import io.yak.ops.core.api.connector.source.InputStatus;
import io.yak.ops.flow.runtime.execution.RuntimeTaskInfo;
import io.yak.ops.flow.runtime.execution.TaskEnvironment;
import java.io.IOException;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.ExecutionException;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.atomic.AtomicInteger;
import org.junit.jupiter.api.Test;

/** Verifies failure visibility before resource cleanup completes, without publishing early completion. */
class StreamTaskFailureTest {

    @Test
    void lateControlMailRetainsInputFailureWhileCleanupIsBlocked() throws Exception {
        verifyFailureDuringCleanup(false);
    }

    @Test
    void cleanupFailureIsSuppressedWithoutReplacingThePublishedInputFailure() throws Exception {
        verifyFailureDuringCleanup(true);
    }

    private void verifyFailureDuringCleanup(boolean failCleanup) throws Exception {
        try (FailingTask task = new FailingTask(failCleanup)) {
            try {
                task.start().get(5, TimeUnit.SECONDS);
                assertTrue(task.closing.await(5, TimeUnit.SECONDS));
                assertFalse(task.completionFuture().isDone());

                ExecutionException rejected = assertThrows(
                        ExecutionException.class, () -> task.control().get(5, TimeUnit.SECONDS));
                assertSame(task.inputFailure, rejected.getCause());
                assertEquals(0, task.controlCalls.get());
                assertEquals(0, task.closed.get());
                assertFalse(task.completionFuture().isDone());
            } finally {
                task.releaseClose.countDown();
            }

            ExecutionException completed = assertThrows(
                    ExecutionException.class, () -> task.completionFuture().get(5, TimeUnit.SECONDS));
            assertSame(task.inputFailure, completed.getCause());
            assertEquals(1, task.closed.get());
            ExecutionException rejected = assertThrows(
                    ExecutionException.class, () -> task.control().get(5, TimeUnit.SECONDS));
            assertSame(task.inputFailure, rejected.getCause());
            if (failCleanup) {
                assertEquals(1, task.inputFailure.getSuppressed().length);
                assertSame(task.cleanupFailure, task.inputFailure.getSuppressed()[0]);
            } else {
                assertEquals(0, task.inputFailure.getSuppressed().length);
            }
        }
    }

    private static final class FailingTask extends StreamTask {
        private final IllegalStateException inputFailure = new IllegalStateException("input failed");
        private final IOException cleanupFailure = new IOException("cleanup failed");
        private final CountDownLatch closing = new CountDownLatch(1);
        private final CountDownLatch releaseClose = new CountDownLatch(1);
        private final AtomicInteger controlCalls = new AtomicInteger();
        private final AtomicInteger closed = new AtomicInteger();
        private final boolean failCleanup;

        private FailingTask(boolean failCleanup) {
            super(new TaskEnvironment(new RuntimeTaskInfo(JobID.generate(), 1, 0, 1, 0, 128), new Configuration()));
            this.failCleanup = failCleanup;
        }

        private CompletableFuture<Integer> control() {
            return submitMailbox(controlCalls::incrementAndGet);
        }

        @Override
        protected void openTask() {}

        @Override
        protected InputStatus processInput() {
            throw inputFailure;
        }

        @Override
        protected CompletableFuture<Void> getAvailableFuture() {
            throw new AssertionError("Input fails before requesting availability");
        }

        @Override
        protected void closeTask() throws Exception {
            closing.countDown();
            assertTrue(releaseClose.await(5, TimeUnit.SECONDS));
            closed.incrementAndGet();
            if (failCleanup) {
                throw cleanupFailure;
            }
        }
    }
}
