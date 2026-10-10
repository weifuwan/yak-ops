package io.yak.ops.flow.runtime.tasks;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

import io.yak.ops.core.api.common.JobID;
import io.yak.ops.core.api.connector.sink.ProcessingTimeService;
import io.yak.ops.core.api.connector.source.InputStatus;
import io.yak.ops.core.configuration.Configuration;
import io.yak.ops.flow.runtime.execution.RuntimeTaskInfo;
import io.yak.ops.flow.runtime.execution.TaskEnvironment;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.ExecutionException;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.atomic.AtomicInteger;
import java.util.concurrent.atomic.AtomicReference;
import org.junit.jupiter.api.Test;

/** Verifies that task timers use mailbox serialization and obey task termination. */
class SinkProcessingTimeServiceTest {

    @Test
    void processingTimeCallbackRunsOnMailboxWhileInputIsUnavailable() throws Exception {
        IdleTask task = new IdleTask();
        task.start().get(5, TimeUnit.SECONDS);
        assertTrue(task.inputEntered.await(5, TimeUnit.SECONDS));
        AtomicReference<Thread> firedThread = new AtomicReference<>();
        CountDownLatch callback = new CountDownLatch(1);
        ProcessingTimeService timers = task.timers();
        timers.registerTimer(timers.currentProcessingTime() + 20, () -> {
            firedThread.set(Thread.currentThread());
            callback.countDown();
        });
        try {
            assertTrue(callback.await(5, TimeUnit.SECONDS));
            assertEquals(task.inputThread.get(), firedThread.get());
            assertEquals(1, task.polls.get());
        } finally {
            task.cancelAsync().get(5, TimeUnit.SECONDS);
        }
        assertEquals(1, task.closes.get());
    }

    @Test
    void timerFailureFailsTaskWithoutReenteringTheInput() throws Exception {
        IdleTask task = new IdleTask();
        task.start().get(5, TimeUnit.SECONDS);
        assertTrue(task.inputEntered.await(5, TimeUnit.SECONDS));
        ProcessingTimeService timers = task.timers();
        timers.registerTimer(timers.currentProcessingTime() + 20, () -> {
            throw new IllegalStateException("timed flush failed");
        });
        ExecutionException failure = assertThrows(
                ExecutionException.class, () -> task.completionFuture().get(5, TimeUnit.SECONDS));
        assertNotNull(failure.getCause());
        assertTrue(failure.toString().contains("timed flush failed")
                || failure.getCause().toString().contains("timed flush failed"));
        assertEquals(1, task.polls.get());
        assertEquals(1, task.closes.get());
    }

    @Test
    void mailboxDoesNotRunTimerConcurrentWithBlockedInput() throws Exception {
        CountDownLatch unblock = new CountDownLatch(1);
        CountDownLatch blocked = new CountDownLatch(1);
        IdleTask task = new IdleTask() {
            @Override
            protected InputStatus processInput() {
                polls.incrementAndGet();
                inputThread.set(Thread.currentThread());
                blocked.countDown();
                try {
                    unblock.await();
                } catch (InterruptedException failure) {
                    Thread.currentThread().interrupt();
                }
                return InputStatus.NOTHING_AVAILABLE;
            }
        };
        task.start().get(5, TimeUnit.SECONDS);
        assertTrue(blocked.await(5, TimeUnit.SECONDS));
        CountDownLatch callback = new CountDownLatch(1);
        ProcessingTimeService timers = task.timers();
        timers.registerTimer(timers.currentProcessingTime() + 10, callback::countDown);
        try {
            assertFalse(callback.await(100, TimeUnit.MILLISECONDS));
            unblock.countDown();
            assertTrue(callback.await(5, TimeUnit.SECONDS));
        } finally {
            unblock.countDown();
            task.cancelAsync().get(5, TimeUnit.SECONDS);
        }
        assertEquals(1, task.closes.get());
    }

    @Test
    void cancellationDiscardsPendingTimersAndRejectsNewRegistration() throws Exception {
        IdleTask task = new IdleTask();
        task.start().get(5, TimeUnit.SECONDS);
        assertTrue(task.inputEntered.await(5, TimeUnit.SECONDS));
        AtomicInteger callbacks = new AtomicInteger();
        ProcessingTimeService timers = task.timers();
        timers.registerTimer(timers.currentProcessingTime() + 1000, callbacks::incrementAndGet);
        task.cancelAsync().get(5, TimeUnit.SECONDS);
        assertThrows(
                IllegalStateException.class,
                () -> timers.registerTimer(timers.currentProcessingTime(), callbacks::incrementAndGet));
        assertEquals(0, callbacks.get());
        assertEquals(1, task.closes.get());
    }

    private static class IdleTask extends StreamTask {
        final CountDownLatch inputEntered = new CountDownLatch(1);
        final AtomicReference<Thread> inputThread = new AtomicReference<>();
        final AtomicInteger polls = new AtomicInteger();
        final AtomicInteger closes = new AtomicInteger();
        private final CompletableFuture<Void> unavailable = new CompletableFuture<>();

        IdleTask() {
            super(new TaskEnvironment(
                    new RuntimeTaskInfo(JobID.generate(), 1, 0, 1, 0, 128), new Configuration()));
        }

        ProcessingTimeService timers() {
            return taskEnvironment().getProcessingTimeService();
        }

        @Override
        protected void openTask() {}

        @Override
        protected InputStatus processInput() {
            inputThread.set(Thread.currentThread());
            polls.incrementAndGet();
            inputEntered.countDown();
            return InputStatus.NOTHING_AVAILABLE;
        }

        @Override
        protected CompletableFuture<Void> getAvailableFuture() {
            return unavailable;
        }

        @Override
        protected void closeTask() {
            closes.incrementAndGet();
        }
    }
}
