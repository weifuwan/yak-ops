package io.yak.ops.connector.base.source.reader.synchronization;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertTrue;

import java.util.concurrent.CompletableFuture;
import java.util.concurrent.TimeUnit;
import org.junit.jupiter.api.Test;

/** Exercises queue capacity, sticky producer wakeups and availability handoff races. */
class FutureCompletingBlockingQueueTest {

    @Test
    void availabilityFollowsEnqueueAndDrain() throws Exception {
        FutureCompletingBlockingQueue<Integer> queue = new FutureCompletingBlockingQueue<>(1);
        CompletableFuture<Void> waiting = queue.getAvailabilityFuture();

        assertFalse(waiting.isDone());
        assertTrue(queue.put(0, 17));
        waiting.get(2, TimeUnit.SECONDS);
        assertTrue(queue.getAvailabilityFuture().isDone());

        assertEquals(17, queue.poll());
        assertFalse(queue.getAvailabilityFuture().isDone());
        assertNull(queue.poll());

        queue.notifyAvailable();
        assertTrue(queue.getAvailabilityFuture().isDone());
        assertNull(queue.poll());
        assertFalse(queue.getAvailabilityFuture().isDone());
    }

    @Test
    void producerWaitsUntilCapacityIsReleased() throws Exception {
        FutureCompletingBlockingQueue<Integer> queue = new FutureCompletingBlockingQueue<>(1);
        assertTrue(queue.put(1, 1));

        CompletableFuture<Boolean> second =
                CompletableFuture.supplyAsync(() -> {
                    try {
                        return queue.put(2, 2);
                    } catch (InterruptedException exception) {
                        Thread.currentThread().interrupt();
                        throw new IllegalStateException(exception);
                    }
                });

        assertFalse(second.isDone());
        assertEquals(1, queue.poll());
        assertTrue(second.get(2, TimeUnit.SECONDS));
        assertEquals(2, queue.poll());
        queue.releaseProducer(2);
    }

    @Test
    void producerWakeupIsStickyAndDoesNotDropOtherProducer() throws Exception {
        FutureCompletingBlockingQueue<Integer> queue = new FutureCompletingBlockingQueue<>(1);
        assertTrue(queue.put(1, 1));
        queue.wakeUpPuttingThread(2);

        assertFalse(queue.put(2, 2));
        assertEquals(1, queue.poll());
        assertTrue(queue.put(3, 3));
        assertEquals(3, queue.poll());
        queue.releaseProducer(2);
        assertTrue(queue.put(2, 4));
        assertEquals(4, queue.poll());
    }

    @Test
    void invalidQueueCapacityIsRejected() {
        org.junit.jupiter.api.Assertions.assertThrows(
                IllegalArgumentException.class, () -> new FutureCompletingBlockingQueue<>(0));
    }
}
