package io.yak.ops.flow.runtime.io;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

import io.yak.ops.core.api.connector.source.InputStatus;
import io.yak.ops.flow.runtime.graph.StreamEdge;
import io.yak.ops.flow.runtime.graph.StreamPartitioning;
import io.yak.ops.flow.runtime.io.partition.InputGate;
import io.yak.ops.flow.runtime.io.partition.ResultPartition;
import java.util.ArrayList;
import java.util.List;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.ExecutionException;
import java.util.concurrent.TimeUnit;
import org.junit.jupiter.api.Test;

class ResultPartitionInputGateTest {

    @Test
    void shouldReturnEndOfInputOnlyAfterAllSubpartitionsFinishAndDataDrain() throws Exception {
        InputGate<String> gate = new InputGate<>(2, 2);
        RecordWriterOutput<String> first = output(new StreamEdge(10, 20, StreamPartitioning.REBALANCE), 0, 2,
                List.of(gate));
        RecordWriterOutput<String> second = output(new StreamEdge(10, 20, StreamPartitioning.REBALANCE), 1, 2,
                List.of(gate));
        List<String> received = new ArrayList<>();

        assertEquals(2, gate.getNumberOfInputChannels());
        assertFalse(gate.getAvailableFuture().isDone());
        first.collect("first");
        first.finish();
        assertEquals(InputStatus.MORE_AVAILABLE, gate.emitNext(received::add));
        assertEquals(List.of("first"), received);
        assertEquals(InputStatus.NOTHING_AVAILABLE, gate.emitNext(received::add));
        assertFalse(gate.getAvailableFuture().isDone());

        second.finish();
        assertEquals(InputStatus.END_OF_INPUT, gate.emitNext(received::add));
        assertTrue(gate.getAvailableFuture().isDone());
        assertThrows(IllegalStateException.class, second::finish);
        assertThrows(IllegalStateException.class, () -> first.collect("after-end"));
    }

    @Test
    void shouldShareOneBoundedBufferBudgetAcrossAllProducerSubpartitions() throws Exception {
        InputGate<String> gate = new InputGate<>(1, 2);
        RecordWriterOutput<String> first = output(new StreamEdge(1, 2, StreamPartitioning.REBALANCE), 0, 2,
                List.of(gate));
        RecordWriterOutput<String> second = output(new StreamEdge(1, 2, StreamPartitioning.REBALANCE), 1, 2,
                List.of(gate));
        first.collect("a");

        CompletableFuture<Void> secondSend = new CompletableFuture<>();
        CountDownLatch started = new CountDownLatch(1);
        Thread producer = Thread.ofVirtual().start(() -> {
            started.countDown();
            try {
                second.collect("b");
                secondSend.complete(null);
            } catch (Throwable error) {
                secondSend.completeExceptionally(error);
            }
        });
        try {
            assertTrue(started.await(5, TimeUnit.SECONDS));
            Thread.sleep(60);
            assertFalse(secondSend.isDone(), "Producer buffers must share one target capacity");
            assertEquals(1, gate.queuedRecords());
            List<String> received = new ArrayList<>();
            assertEquals(InputStatus.MORE_AVAILABLE, gate.emitNext(received::add));
            secondSend.get(5, TimeUnit.SECONDS);
            first.finish();
            second.finish();
            assertEquals(InputStatus.MORE_AVAILABLE, gate.emitNext(received::add));
            assertEquals(InputStatus.END_OF_INPUT, gate.emitNext(received::add));
            assertEquals(List.of("a", "b"), received);
        } finally {
            gate.abort(new IllegalStateException("test end"));
            producer.join(5000);
            assertFalse(producer.isAlive());
        }
    }

    @Test
    void shouldWakeIdleReaderAndBlockedWriterAfterAbort() throws Exception {
        InputGate<String> idle = new InputGate<>(1, 1);
        CompletableFuture<Void> waiting = idle.getAvailableFuture();
        idle.abort(new IllegalStateException("reader stop"));
        assertThrows(ExecutionException.class, () -> waiting.get(5, TimeUnit.SECONDS));

        InputGate<String> gate = new InputGate<>(1, 1);
        RecordWriterOutput<String> producerOutput = output(new StreamEdge(1, 2), 0, 1, List.of(gate));
        producerOutput.collect("a");
        CountDownLatch started = new CountDownLatch(1);
        CompletableFuture<Void> blocked = new CompletableFuture<>();
        Thread producer = Thread.ofVirtual().start(() -> {
            started.countDown();
            try {
                producerOutput.collect("b");
                blocked.complete(null);
            } catch (Throwable error) {
                blocked.completeExceptionally(error);
            }
        });
        try {
            assertTrue(started.await(5, TimeUnit.SECONDS));
            gate.abort(new IllegalStateException("downstream failed"));
            assertThrows(IllegalStateException.class, () -> gate.emitNext(ignored -> {}));
            assertThrows(ExecutionException.class, () -> blocked.get(5, TimeUnit.SECONDS));
        } finally {
            producer.join(5000);
            assertFalse(producer.isAlive());
        }
    }

    @Test
    void shouldPreserveForwardAndProducerLocalRebalance() throws Exception {
        List<InputGate<String>> forwardGates = List.of(new InputGate<>(4, 2), new InputGate<>(4, 2));
        StreamEdge forwardEdge = new StreamEdge(10, 20, StreamPartitioning.FORWARD);
        RecordWriterOutput<String> first = output(forwardEdge, 0, 2, forwardGates);
        RecordWriterOutput<String> second = output(forwardEdge, 1, 2, forwardGates);
        first.collect("a");
        second.collect("b");
        first.finish();
        second.finish();

        List<String> f0 = new ArrayList<>();
        List<String> f1 = new ArrayList<>();
        assertEquals(InputStatus.MORE_AVAILABLE, forwardGates.get(0).emitNext(f0::add));
        assertEquals(InputStatus.MORE_AVAILABLE, forwardGates.get(1).emitNext(f1::add));
        assertEquals(List.of("a"), f0);
        assertEquals(List.of("b"), f1);
        assertEquals(InputStatus.END_OF_INPUT, forwardGates.get(0).emitNext(f0::add));
        assertEquals(InputStatus.END_OF_INPUT, forwardGates.get(1).emitNext(f1::add));

        List<InputGate<String>> rebalance = List.of(
                new InputGate<>(4, 1), new InputGate<>(4, 1), new InputGate<>(4, 1));
        RecordWriterOutput<String> roundRobin = output(
                new StreamEdge(10, 20, StreamPartitioning.REBALANCE), 0, 1, rebalance);
        for (int i = 0; i < 6; i++) {
            roundRobin.collect("record-" + i);
        }
        roundRobin.finish();
        for (int i = 0; i < rebalance.size(); i++) {
            List<String> rows = new ArrayList<>();
            assertEquals(InputStatus.MORE_AVAILABLE, rebalance.get(i).emitNext(rows::add));
            assertEquals(InputStatus.MORE_AVAILABLE, rebalance.get(i).emitNext(rows::add));
            assertEquals(List.of("record-" + i, "record-" + (i + 3)), rows);
            assertEquals(InputStatus.END_OF_INPUT, rebalance.get(i).emitNext(rows::add));
        }
    }

    @Test
    void shouldKeepTheSameBusinessKeyInOneInputGate() throws Exception {
        List<InputGate<String>> gates = List.of(new InputGate<>(8, 2), new InputGate<>(8, 2));
        StreamEdge keyed = StreamEdge.keyed(1, 2, (String row) -> row.substring(0, 1));
        RecordWriterOutput<String> first = output(keyed, 0, 2, gates);
        RecordWriterOutput<String> second = output(keyed, 1, 2, gates);
        first.collect("A1");
        second.collect("A2");
        first.collect("B1");
        second.collect("B2");
        first.finish();
        second.finish();

        List<String> all = new ArrayList<>();
        for (InputGate<String> gate : gates) {
            List<String> records = new ArrayList<>();
            while (gate.emitNext(records::add) != InputStatus.END_OF_INPUT) {}
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
    void shouldRejectInvalidForwardParallelismAndUnstableKeys() {
        List<InputGate<String>> gates = List.of(new InputGate<>(2, 1), new InputGate<>(2, 1));
        assertThrows(IllegalArgumentException.class,
                () -> output(new StreamEdge(1, 2), 0, 1, gates));
        assertThrows(IllegalArgumentException.class, () -> new StreamEdge(1, 2, StreamPartitioning.KEYED));
        assertThrows(IllegalArgumentException.class, () -> new StreamEdge(1, 2, StreamPartitioning.FORWARD,
                (io.yak.ops.core.api.operators.KeySelector<String>) value -> value));

        RecordWriterOutput<String> nullKey = output(
                StreamEdge.keyed(1, 2, (String value) -> null), 0, 1, gates);
        assertThrows(NullPointerException.class, () -> nullKey.collect("record"));
        RecordWriterOutput<String> arrayKey = output(
                StreamEdge.keyed(1, 2, (String value) -> new byte[] {1}), 0, 1, gates);
        assertThrows(IllegalArgumentException.class, () -> arrayKey.collect("record"));
    }

    @Test
    void shouldFairlyConsumeAcrossProducerSubpartitions() throws Exception {
        InputGate<String> gate = new InputGate<>(6, 2);
        RecordWriterOutput<String> first = output(new StreamEdge(1, 2, StreamPartitioning.REBALANCE), 0, 2,
                List.of(gate));
        RecordWriterOutput<String> second = output(new StreamEdge(1, 2, StreamPartitioning.REBALANCE), 1, 2,
                List.of(gate));
        first.collect("a0");
        first.collect("a1");
        second.collect("b0");
        second.collect("b1");
        first.finish();
        second.finish();

        List<String> values = new ArrayList<>();
        for (int i = 0; i < 4; i++) {
            assertEquals(InputStatus.MORE_AVAILABLE, gate.emitNext(values::add));
        }
        assertEquals(List.of("a0", "b0", "a1", "b1"), values);
        assertEquals(InputStatus.END_OF_INPUT, gate.emitNext(values::add));
    }

    @Test
    void shouldWaitForInFlightSinkWriteBeforeCompletingCheckpointDrain() throws Exception {
        InputGate<String> gate = new InputGate<>(1, 1);
        output(new StreamEdge(1, 2), 0, 1, List.of(gate)).collect("row");
        CompletableFuture<Void> drained = gate.drainedFuture();
        assertFalse(drained.isDone());

        CountDownLatch entered = new CountDownLatch(1);
        CountDownLatch release = new CountDownLatch(1);
        CompletableFuture<Void> processed = new CompletableFuture<>();
        Thread consumer = Thread.ofVirtual().start(() -> {
            try {
                assertEquals(InputStatus.MORE_AVAILABLE, gate.emitNext(value -> {
                    entered.countDown();
                    release.await();
                }));
                processed.complete(null);
            } catch (Throwable error) {
                processed.completeExceptionally(error);
            }
        });
        try {
            assertTrue(entered.await(5, TimeUnit.SECONDS));
            assertEquals(0, gate.queuedRecords());
            assertFalse(gate.drainedFuture().isDone());
            release.countDown();
            processed.get(5, TimeUnit.SECONDS);
            drained.get(5, TimeUnit.SECONDS);
        } finally {
            release.countDown();
            consumer.join(5000);
            assertFalse(consumer.isAlive());
        }
    }

    @Test
    void shouldFailCheckpointDrainAndAvailabilityOnAbortOrWriterFailure() throws Exception {
        InputGate<String> gate = new InputGate<>(1, 1);
        output(new StreamEdge(1, 2), 0, 1, List.of(gate)).collect("row");
        CompletableFuture<Void> drained = gate.drainedFuture();
        gate.abort(new IllegalStateException("sink failed"));
        assertThrows(ExecutionException.class, () -> drained.get(5, TimeUnit.SECONDS));
        assertThrows(IllegalStateException.class, () -> gate.emitNext(ignored -> {}));

        InputGate<String> failing = new InputGate<>(1, 1);
        output(new StreamEdge(1, 2), 0, 1, List.of(failing)).collect("row");
        CompletableFuture<Void> pending = failing.drainedFuture();
        assertThrows(IllegalStateException.class,
                () -> failing.emitNext(ignored -> { throw new IllegalStateException("writer failed"); }));
        assertThrows(ExecutionException.class, () -> pending.get(5, TimeUnit.SECONDS));
        assertThrows(ExecutionException.class,
                () -> failing.getAvailableFuture().get(5, TimeUnit.SECONDS));
    }

    private static RecordWriterOutput<String> output(
            StreamEdge edge, int producer, int producers, List<InputGate<String>> gates) {
        return new RecordWriterOutput<>(edge, producer, producers, new ResultPartition<>(producer, gates));
    }
}
