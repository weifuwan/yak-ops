package io.yak.ops.flow.runtime.tasks;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

import io.yak.ops.core.api.common.JobID;
import io.yak.ops.core.api.connector.source.InputStatus;
import io.yak.ops.core.configuration.Configuration;
import io.yak.ops.flow.runtime.execution.RuntimeTaskInfo;
import io.yak.ops.flow.runtime.execution.TaskEnvironment;
import java.util.concurrent.CancellationException;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.ExecutionException;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.atomic.AtomicInteger;
import java.util.concurrent.atomic.AtomicReference;
import org.junit.jupiter.api.Test;

class StreamTaskMailboxTest {

    @Test
    void shouldSuspendInputWithoutBlockingCoordinatorMailAndResumeOnAvailability() throws Exception {
        TestTask task = new TestTask();
        task.start().get(5, TimeUnit.SECONDS);
        assertTrue(task.firstPoll.await(5, TimeUnit.SECONDS));

        Thread handling = task.runControl().get(5, TimeUnit.SECONDS);
        assertEquals(task.inputThread.get(), handling);
        assertEquals(1, task.polls.get(), "Control processing must not poll unavailable input");

        task.available.complete(null);
        task.completionFuture().get(5, TimeUnit.SECONDS);
        assertEquals(2, task.polls.get());
        assertEquals(1, task.closes.get());
        assertEquals(task.inputThread.get(), task.closeThread.get());
        assertThrows(ExecutionException.class, () -> task.runControl().get(5, TimeUnit.SECONDS));
    }

    @Test
    void shouldResumeInputAfterSplitEventEvenWhenReaderAvailabilityFutureStaysPending() throws Exception {
        TestTask task = new TestTask();
        task.start().get(5, TimeUnit.SECONDS);
        assertTrue(task.firstPoll.await(5, TimeUnit.SECONDS));

        // A Reader may not complete an old availability Future when a new split arrives.
        // The coordinator event explicitly reactivates the mailbox default action.
        task.deliverInputChange().get(5, TimeUnit.SECONDS);
        task.completionFuture().get(5, TimeUnit.SECONDS);
        assertFalse(task.available.isDone());
        assertEquals(2, task.polls.get());
        assertEquals(1, task.finishes.get());
        assertEquals(1, task.closes.get());
    }

    @Test
    void shouldCancelWhileInputSuspendedWithoutPollingAgainOrFinalizing() throws Exception {
        TestTask task = new TestTask();
        task.start().get(5, TimeUnit.SECONDS);
        assertTrue(task.firstPoll.await(5, TimeUnit.SECONDS));

        task.cancelAsync().get(5, TimeUnit.SECONDS);
        assertThrows(ExecutionException.class, () -> task.completionFuture().get(5, TimeUnit.SECONDS));
        assertEquals(1, task.polls.get());
        assertEquals(0, task.finishes.get());
        assertEquals(1, task.closes.get());
        assertThrows(ExecutionException.class, () -> task.runControl().get(5, TimeUnit.SECONDS));
    }

    @Test
    void shouldFailQueuedControlMailWhenAnEarlierControlActionFails() throws Exception {
        TestTask task = new TestTask();
        task.start().get(5, TimeUnit.SECONDS);
        assertTrue(task.firstPoll.await(5, TimeUnit.SECONDS));

        CountDownLatch entered = new CountDownLatch(1);
        CountDownLatch release = new CountDownLatch(1);
        try {
            CompletableFuture<Void> failing = task.failControl(entered, release);
            assertTrue(entered.await(5, TimeUnit.SECONDS));
            CompletableFuture<Thread> queued = task.runControl();
            assertFalse(queued.isDone());

            release.countDown();
            ExecutionException failure = assertThrows(
                    ExecutionException.class, () -> failing.get(5, TimeUnit.SECONDS));
            assertTrue(failure.getCause().getMessage().contains("control failed"));
            assertThrows(ExecutionException.class, () -> queued.get(5, TimeUnit.SECONDS));
            assertThrows(ExecutionException.class, () -> task.completionFuture().get(5, TimeUnit.SECONDS));
            assertEquals(0, task.finishes.get());
            assertEquals(1, task.closes.get());
        } finally {
            release.countDown();
        }
    }

    @Test
    void shouldFailOnAvailabilityErrorAndCloseTheTask() throws Exception {
        TestTask task = new TestTask();
        task.start().get(5, TimeUnit.SECONDS);
        assertTrue(task.firstPoll.await(5, TimeUnit.SECONDS));

        task.available.completeExceptionally(new IllegalStateException("reader failed"));
        ExecutionException failure = assertThrows(
                ExecutionException.class, () -> task.completionFuture().get(5, TimeUnit.SECONDS));
        assertTrue(failure.getCause().toString().contains("reader failed"));
        assertEquals(0, task.finishes.get());
        assertEquals(1, task.closes.get());
    }

    @Test
    void shouldRejectRepeatedImmediatelyCompletedAvailabilityWithoutSpinning() throws Exception {
        TestTask task = new TestTask() {
            @Override
            protected InputStatus processInput() {
                polls.incrementAndGet();
                return InputStatus.NOTHING_AVAILABLE;
            }

            @Override
            protected CompletableFuture<Void> getAvailableFuture() {
                return CompletableFuture.completedFuture(null);
            }
        };
        task.start().get(5, TimeUnit.SECONDS);
        ExecutionException failure = assertThrows(
                ExecutionException.class, () -> task.completionFuture().get(5, TimeUnit.SECONDS));
        assertTrue(failure.getCause().getMessage().contains("持续已完成"));
        assertEquals(2, task.polls.get());
        assertEquals(1, task.closes.get());
    }

    @Test
    void shouldRejectStartupAfterCancellationWithoutLeakingTaskThread() throws Exception {
        TestTask task = new TestTask();
        task.cancelAsync().get(5, TimeUnit.SECONDS);
        assertThrows(ExecutionException.class, () -> task.start().get(5, TimeUnit.SECONDS));
        assertThrows(ExecutionException.class, () -> task.completionFuture().get(5, TimeUnit.SECONDS));
        assertEquals(0, task.polls.get());
    }

    private static class TestTask extends StreamTask {

        final AtomicInteger polls = new AtomicInteger();
        final AtomicInteger finishes = new AtomicInteger();
        final AtomicInteger closes = new AtomicInteger();
        final CountDownLatch firstPoll = new CountDownLatch(1);
        final CompletableFuture<Void> available = new CompletableFuture<>();
        final AtomicReference<Thread> inputThread = new AtomicReference<>();
        final AtomicReference<Thread> closeThread = new AtomicReference<>();

        TestTask() {
            super(new TaskEnvironment(
                    new RuntimeTaskInfo(JobID.generate(), 1, 0, 1, 0), new Configuration()));
        }

        CompletableFuture<Thread> runControl() {
            return submitMailbox(Thread::currentThread);
        }

        CompletableFuture<Void> deliverInputChange() {
            return submitMailbox(() -> {
                resumeInputProcessing();
                return null;
            });
        }

        CompletableFuture<Void> failControl(CountDownLatch entered, CountDownLatch release) {
            return submitMailbox(() -> {
                entered.countDown();
                release.await();
                throw new IllegalStateException("control failed");
            });
        }

        @Override
        protected void openTask() {}

        @Override
        protected InputStatus processInput() {
            inputThread.set(Thread.currentThread());
            if (polls.incrementAndGet() == 1) {
                firstPoll.countDown();
                return InputStatus.NOTHING_AVAILABLE;
            }
            return InputStatus.END_OF_INPUT;
        }

        @Override
        protected CompletableFuture<Void> getAvailableFuture() {
            return available;
        }

        @Override
        protected void finishTask() {
            finishes.incrementAndGet();
        }

        @Override
        protected void closeTask() {
            closes.incrementAndGet();
            closeThread.set(Thread.currentThread());
        }
    }
}
