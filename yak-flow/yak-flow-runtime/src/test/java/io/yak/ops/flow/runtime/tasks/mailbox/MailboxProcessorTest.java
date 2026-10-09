package io.yak.ops.flow.runtime.tasks.mailbox;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNotEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

import java.util.concurrent.CompletableFuture;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.ExecutionException;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.atomic.AtomicInteger;
import java.util.concurrent.atomic.AtomicReference;
import org.junit.jupiter.api.Test;

class MailboxProcessorTest {

    @Test
    void shouldRunControlMailDuringInputSuspensionAndResumeDefaultAction() throws Exception {
        TaskMailboxImpl mailbox = new TaskMailboxImpl();
        AtomicInteger inputs = new AtomicInteger();
        AtomicReference<Thread> defaultThread = new AtomicReference<>();
        AtomicReference<MailboxDefaultAction.Suspension> suspension = new AtomicReference<>();
        CountDownLatch suspended = new CountDownLatch(1);

        MailboxProcessor processor = new MailboxProcessor(mailbox, controller -> {
            defaultThread.set(Thread.currentThread());
            if (inputs.incrementAndGet() == 1) {
                suspension.set(controller.suspendDefaultAction());
                suspended.countDown();
            } else {
                controller.allActionsCompleted();
            }
        }, () -> {}, () -> false);

        CompletableFuture<Void> exited = new CompletableFuture<>();
        Thread worker = Thread.ofVirtual().start(() -> {
            try {
                processor.runMailboxLoop();
            } catch (Throwable failure) {
                exited.completeExceptionally(failure);
            } finally {
                processor.close(new IllegalStateException("finished"));
                // Signal the test only after the asynchronous mailbox cleanup is visible.
                exited.complete(null);
            }
        });

        try {
            assertTrue(suspended.await(5, TimeUnit.SECONDS));
            CompletableFuture<Thread> acknowledged = processor.getMailboxExecutor().submit(Thread::currentThread);
            Thread controlThread = acknowledged.get(5, TimeUnit.SECONDS);
            assertEquals(defaultThread.get(), controlThread);
            assertNotEquals(Thread.currentThread(), controlThread);
            assertEquals(1, inputs.get(), "Mailbox control must not resume suspended input");

            suspension.get().resume();
            suspension.get().resume(); // stale/duplicate resume is harmless
            exited.get(5, TimeUnit.SECONDS);
            assertEquals(2, inputs.get());
            assertEquals(TaskMailbox.State.CLOSED, mailbox.getState());
        } finally {
            processor.prepareClose();
            worker.interrupt();
            worker.join(5000);
            assertFalse(worker.isAlive());
        }
    }

    @Test
    void shouldExecuteOneMailBeforeDefaultInputEvenWithQueuedControlEvents() throws Exception {
        TaskMailboxImpl mailbox = new TaskMailboxImpl();
        MailboxProcessor processor = new MailboxProcessor(
                mailbox, controller -> controller.allActionsCompleted(), () -> {}, () -> false);
        AtomicInteger processed = new AtomicInteger();
        CompletableFuture<Integer> first = processor.getMailboxExecutor().submit(processed::incrementAndGet);
        CompletableFuture<Integer> second = processor.getMailboxExecutor().submit(processed::incrementAndGet);

        processor.runMailboxLoop();
        processor.close(new IllegalStateException("job completed"));

        assertEquals(1, first.get(5, TimeUnit.SECONDS));
        assertEquals(1, processed.get());
        assertThrows(ExecutionException.class, () -> second.get(5, TimeUnit.SECONDS));
    }

    @Test
    void selfReschedulingControlMailMustNotStarveInput() throws Exception {
        TaskMailboxImpl mailbox = new TaskMailboxImpl();
        java.util.List<String> sequence = new java.util.ArrayList<>();
        AtomicInteger inputSteps = new AtomicInteger();
        MailboxProcessor processor = new MailboxProcessor(mailbox, controller -> {
            sequence.add("input");
            if (inputSteps.incrementAndGet() == 4) {
                controller.allActionsCompleted();
            }
        }, () -> {}, () -> false);

        AtomicInteger mailSteps = new AtomicInteger();
        MailboxExecutor executor = processor.getMailboxExecutor();
        Runnable selfReschedulingMail = new Runnable() {
            @Override
            public void run() {
                sequence.add("mail");
                if (mailSteps.incrementAndGet() < 20) {
                    executor.submit(() -> {
                        run();
                        return null;
                    });
                }
            }
        };
        executor.submit(() -> {
            selfReschedulingMail.run();
            return null;
        });

        processor.runMailboxLoop();
        processor.close(new IllegalStateException("finished"));

        // Flink-style mailbox fairness: a self-enqueueing mail must not monopolize
        // the Task thread. Every input step gets a turn, even with pending controls.
        assertEquals(java.util.List.of(
                "mail", "input", "mail", "input", "mail", "input", "mail", "input"), sequence);
        assertEquals(4, inputSteps.get());
        assertEquals(4, mailSteps.get());
        assertEquals(TaskMailbox.State.CLOSED, mailbox.getState());
    }

    @Test
    void shouldAcknowledgeMailsOnlyAfterExecutionAndRejectPendingOnClose() throws Exception {
        TaskMailboxImpl mailbox = new TaskMailboxImpl();
        MailboxExecutor executor = new MailboxExecutorImpl(mailbox);
        CompletableFuture<Integer> first = executor.submit(() -> 10);
        CompletableFuture<Integer> second = executor.submit(() -> 20);
        assertFalse(first.isDone());
        assertFalse(second.isDone());

        mailbox.bindMailboxThread(Thread.currentThread());
        mailbox.tryTake().run();
        assertEquals(10, first.get(5, TimeUnit.SECONDS));
        assertFalse(second.isDone());

        mailbox.quiesce();
        assertThrows(ExecutionException.class,
                () -> executor.submit(() -> 30).get(5, TimeUnit.SECONDS));
        for (Mail<?> mail : mailbox.close()) {
            mail.reject(new IllegalStateException("stopped"));
        }
        assertThrows(ExecutionException.class, () -> second.get(5, TimeUnit.SECONDS));
        assertEquals(TaskMailbox.State.CLOSED, mailbox.getState());
        assertThrows(IllegalStateException.class, () -> mailbox.tryTake());
    }
}
