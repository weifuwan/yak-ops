package io.yak.ops.flow.runtime.io;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

import io.yak.ops.core.api.connector.source.InputStatus;
import io.yak.ops.flow.runtime.graph.StreamEdge;
import io.yak.ops.flow.runtime.graph.StreamPartitioning;
import java.util.ArrayList;
import java.util.List;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.ExecutionException;
import java.util.concurrent.TimeUnit;
import org.junit.jupiter.api.Test;

class RecordChannelTest {

    @Test
    void shouldReturnEndOfInputOnlyAfterAllProducersAndDataDrain() throws Exception {
        RecordChannel<String> channel = new RecordChannel<>(2, 2);
        List<String> output = new ArrayList<>();

        assertFalse(channel.isAvailable().isDone());
        channel.send("first");
        channel.producerFinished(0);
        assertEquals(InputStatus.MORE_AVAILABLE, channel.emitNext(output::add));
        assertEquals(List.of("first"), output);
        assertEquals(InputStatus.NOTHING_AVAILABLE, channel.emitNext(output::add));
        assertFalse(channel.isAvailable().isDone());

        channel.producerFinished(1);
        assertEquals(InputStatus.END_OF_INPUT, channel.emitNext(output::add));
        assertTrue(channel.isAvailable().isDone());
        assertThrows(IllegalStateException.class, () -> channel.producerFinished(1));
    }

    @Test
    void shouldBackpressureProducerUntilConsumerDrainsCapacity() throws Exception {
        RecordChannel<String> channel = new RecordChannel<>(1, 1);
        channel.send("a");
        CompletableFuture<Void> secondSend = new CompletableFuture<>();
        CountDownLatch started = new CountDownLatch(1);
        Thread producer = Thread.ofVirtual().start(() -> {
            started.countDown();
            try {
                channel.send("b");
                secondSend.complete(null);
            } catch (Throwable failure) {
                secondSend.completeExceptionally(failure);
            }
        });
        try {
            assertTrue(started.await(5, TimeUnit.SECONDS));
            Thread.sleep(60);
            assertFalse(secondSend.isDone(), "Bounded Channel 不得无限缓冲第二条记录");
            List<String> output = new ArrayList<>();
            assertEquals(InputStatus.MORE_AVAILABLE, channel.emitNext(output::add));
            secondSend.get(5, TimeUnit.SECONDS);
            channel.producerFinished(0);
            assertEquals(InputStatus.MORE_AVAILABLE, channel.emitNext(output::add));
            assertEquals(InputStatus.END_OF_INPUT, channel.emitNext(output::add));
            assertEquals(List.of("a", "b"), output);
        } finally {
            channel.abort(new IllegalStateException("test closed"));
            producer.join(5000);
            assertFalse(producer.isAlive());
        }
    }

    @Test
    void shouldWakeWaitingReaderAndBlockedWriterOnAbort() throws Exception {
        RecordChannel<String> channel = new RecordChannel<>(1, 1);
        CompletableFuture<Void> waiting = channel.isAvailable();
        channel.send("a");
        CompletableFuture<Void> blocked = new CompletableFuture<>();
        Thread producer = Thread.ofVirtual().start(() -> {
            try {
                channel.send("b");
                blocked.complete(null);
            } catch (Throwable failure) {
                blocked.completeExceptionally(failure);
            }
        });

        try {
            channel.abort(new IllegalStateException("downstream failed"));
            assertTrue(waiting.isDone());
            assertThrows(IllegalStateException.class, () -> channel.emitNext(ignored -> {}));
            assertThrows(ExecutionException.class, () -> blocked.get(5, TimeUnit.SECONDS));
        } finally {
            producer.join(5000);
            assertFalse(producer.isAlive());
        }
    }

    @Test
    void shouldPartitionForwardAndRebalanceWithoutDuplicatingRecords() throws Exception {
        List<RecordChannel<String>> forwardChannels = List.of(new RecordChannel<>(4, 2), new RecordChannel<>(4, 2));
        StreamEdge forwardEdge = new StreamEdge(10, 20, StreamPartitioning.FORWARD);
        RecordRouter<String> first =
                new RecordRouter<>(forwardEdge, 0, 2, forwardChannels);
        RecordRouter<String> second =
                new RecordRouter<>(forwardEdge, 1, 2, forwardChannels);

        first.collect("a");
        second.collect("b");
        first.finish();
        second.finish();

        List<String> f0 = new ArrayList<>();
        List<String> f1 = new ArrayList<>();
        assertEquals(InputStatus.MORE_AVAILABLE, forwardChannels.get(0).emitNext(f0::add));
        assertEquals(InputStatus.MORE_AVAILABLE, forwardChannels.get(1).emitNext(f1::add));
        assertEquals(List.of("a"), f0);
        assertEquals(List.of("b"), f1);
        assertEquals(InputStatus.END_OF_INPUT, forwardChannels.get(0).emitNext(f0::add));
        assertEquals(InputStatus.END_OF_INPUT, forwardChannels.get(1).emitNext(f1::add));

        List<RecordChannel<String>> rebalanceChannels = List.of(
                new RecordChannel<>(4, 1), new RecordChannel<>(4, 1), new RecordChannel<>(4, 1));
        RecordRouter<String> roundRobin = new RecordRouter<>(
                new StreamEdge(10, 20, StreamPartitioning.REBALANCE), 0, 1, rebalanceChannels);
        for (int i = 0; i < 6; i++) {
            roundRobin.collect("record-" + i);
        }
        roundRobin.finish();
        for (int i = 0; i < 3; i++) {
            List<String> values = new ArrayList<>();
            assertEquals(InputStatus.MORE_AVAILABLE, rebalanceChannels.get(i).emitNext(values::add));
            assertEquals(InputStatus.MORE_AVAILABLE, rebalanceChannels.get(i).emitNext(values::add));
            assertEquals(List.of("record-" + i, "record-" + (i + 3)), values);
            assertEquals(InputStatus.END_OF_INPUT, rebalanceChannels.get(i).emitNext(values::add));
        }
    }

    @Test
    void shouldRouteSameBusinessKeyToOneChannel() throws Exception {
        List<RecordChannel<String>> channels = List.of(new RecordChannel<>(8, 2), new RecordChannel<>(8, 2));
        StreamEdge keyed = StreamEdge.keyed(1, 2, (String r) -> r.substring(0, 1));
        RecordRouter<String> first = new RecordRouter<>(keyed, 0, 2, channels);
        RecordRouter<String> second = new RecordRouter<>(keyed, 1, 2, channels);
        first.collect("A1");
        second.collect("A2");
        first.collect("B1");
        second.collect("B2");
        first.finish();
        second.finish();

        List<String> all = new ArrayList<>();
        for (RecordChannel<String> channel : channels) {
            List<String> records = new ArrayList<>();
            while (channel.emitNext(records::add) != InputStatus.END_OF_INPUT) {}
            if (!records.isEmpty()) {
                assertEquals(records.getFirst().substring(0, 1),
                        records.getLast().substring(0, 1));
            }
            all.addAll(records);
        }
        assertEquals(4, all.size());
        assertTrue(all.containsAll(List.of("A1", "A2", "B1", "B2")));
    }

    @Test
    void shouldRejectUnsupportedKeyAndInvalidForwardParallelism() {
        List<RecordChannel<String>> channels = List.of(new RecordChannel<>(2, 1), new RecordChannel<>(2, 1));
        assertThrows(IllegalArgumentException.class, () ->
                new RecordRouter<>(new StreamEdge(1, 2), 0, 1, channels));
        assertThrows(IllegalArgumentException.class, () ->
                new StreamEdge(1, 2, StreamPartitioning.KEYED));
        assertThrows(IllegalArgumentException.class, () ->
                new StreamEdge(1, 2, StreamPartitioning.FORWARD, (io.yak.ops.core.api.operators.KeySelector<String>) value -> value));

        RecordRouter<String> nullKey = new RecordRouter<>(
                StreamEdge.keyed(1, 2, (String value) -> null), 0, 1, channels);
        assertThrows(NullPointerException.class, () -> nullKey.collect("record"));
        RecordRouter<String> arrayKey = new RecordRouter<>(
                StreamEdge.keyed(1, 2, (String value) -> new byte[] {1}), 0, 1, channels);
        assertThrows(IllegalArgumentException.class, () -> arrayKey.collect("record"));
    }
    @Test
    void shouldWaitForInFlightDownstreamProcessingBeforeCheckpointDrain() throws Exception {
        RecordChannel<String> channel = new RecordChannel<>(1, 1);
        channel.send("row");
        assertFalse(channel.drainedFuture().isDone());
        CountDownLatch started = new CountDownLatch(1);
        CountDownLatch release = new CountDownLatch(1);
        CompletableFuture<Void> processed = new CompletableFuture<>();
        Thread consumer = Thread.ofVirtual().start(() -> {
            try {
                assertEquals(InputStatus.MORE_AVAILABLE, channel.emitNext(value -> {
                    started.countDown();
                    release.await();
                }));
                processed.complete(null);
            } catch (Throwable error) {
                processed.completeExceptionally(error);
            }
        });
        try {
            assertTrue(started.await(5, TimeUnit.SECONDS));
            assertEquals(0, channel.queuedRecords());
            assertFalse(channel.drainedFuture().isDone(),
                    "Checkpoint 必须等已经取走但仍在 SinkWriter.write 中的记录");
            release.countDown();
            processed.get(5, TimeUnit.SECONDS);
            channel.drainedFuture().get(5, TimeUnit.SECONDS);
        } finally {
            release.countDown();
            consumer.join(5000);
            assertFalse(consumer.isAlive());
        }
    }

    @Test
    void shouldFailCheckpointDrainOnChannelAbort() {
        RecordChannel<String> channel = new RecordChannel<>(1, 1);
        try {
            channel.send("pending");
        } catch (Exception error) {
            throw new AssertionError(error);
        }
        CompletableFuture<Void> drained = channel.drainedFuture();
        assertFalse(drained.isDone());
        channel.abort(new IllegalStateException("sink failed"));
        assertThrows(ExecutionException.class, () -> drained.get(5, TimeUnit.SECONDS));
    }

}
