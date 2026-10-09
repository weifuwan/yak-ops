package io.yak.ops.flow.runtime.io;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

import io.yak.ops.core.api.connector.source.InputStatus;
import io.yak.ops.flow.runtime.io.partition.InputGate;
import io.yak.ops.flow.runtime.io.partition.ResultPartition;
import java.util.ArrayList;
import java.util.List;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.CompletionException;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.TimeUnit;
import org.junit.jupiter.api.Test;

class AlignedBarrierInputGateTest {

    @Test
    void barrierAlignsAllProducersAndDoesNotConsumePostBarrierDataEarly() throws Exception {
        InputGate<String> gate = new InputGate<>(8, 2);
        ResultPartition<String> first = new ResultPartition<>(0, List.of(gate));
        ResultPartition<String> second = new ResultPartition<>(1, List.of(gate));
        List<String> records = new ArrayList<>();
        List<Long> barriers = new ArrayList<>();

        first.emitRecord(0, "before-0");
        first.broadcastBarrier(1);
        CountDownLatch started = new CountDownLatch(1);
        CompletableFuture<Void> postBarrier = writeLater(first, "after-0", started);
        assertTrue(started.await(2, TimeUnit.SECONDS));
        assertFalse(postBarrier.isDone());
        second.emitRecord(0, "before-1");

        assertEquals(InputStatus.MORE_AVAILABLE, gate.emitNext(records::add, barriers::add));
        assertEquals(InputStatus.MORE_AVAILABLE, gate.emitNext(records::add, barriers::add));
        assertEquals(InputStatus.MORE_AVAILABLE, gate.emitNext(records::add, barriers::add));
        assertEquals(2, records.size());
        assertEquals(List.of(), barriers);
        assertEquals(InputStatus.NOTHING_AVAILABLE, gate.emitNext(records::add, barriers::add));

        second.broadcastBarrier(1);
        assertEquals(InputStatus.MORE_AVAILABLE, gate.emitNext(records::add, barriers::add));
        assertEquals(List.of(1L), barriers);
        postBarrier.get(2, TimeUnit.SECONDS);
        assertEquals(InputStatus.MORE_AVAILABLE, gate.emitNext(records::add, barriers::add));
        assertEquals(List.of("before-0", "before-1", "after-0"), records);
        first.finish();
        second.finish();
        assertEquals(InputStatus.END_OF_INPUT, gate.emitNext(records::add, barriers::add));
    }

    @Test
    void postBarrierWriterMustNotStarveOtherProducerAtCapacityOne() throws Exception {
        InputGate<String> gate = new InputGate<>(1, 2);
        ResultPartition<String> first = new ResultPartition<>(0, List.of(gate));
        ResultPartition<String> second = new ResultPartition<>(1, List.of(gate));
        first.broadcastBarrier(4);
        CountDownLatch postStarted = new CountDownLatch(1);
        CompletableFuture<Void> post = writeLater(first, "post", postStarted);
        assertTrue(postStarted.await(2, TimeUnit.SECONDS));

        // This must be admitted immediately. Without a producer-side barrier fence, "post"
        // fills the shared buffer and prevents the other producer reaching its barrier.
        CountDownLatch preStarted = new CountDownLatch(1);
        CompletableFuture<Void> pre = writeLater(second, "pre", preStarted);
        assertTrue(preStarted.await(2, TimeUnit.SECONDS));
        pre.get(2, TimeUnit.SECONDS);
        assertFalse(post.isDone());

        List<String> rows = new ArrayList<>();
        List<Long> checkpoints = new ArrayList<>();
        assertEquals(InputStatus.MORE_AVAILABLE, gate.emitNext(rows::add, checkpoints::add));
        assertEquals(InputStatus.MORE_AVAILABLE, gate.emitNext(rows::add, checkpoints::add));
        assertEquals(List.of("pre"), rows);
        second.broadcastBarrier(4);
        assertEquals(InputStatus.MORE_AVAILABLE, gate.emitNext(rows::add, checkpoints::add));
        assertEquals(List.of(4L), checkpoints);
        post.get(2, TimeUnit.SECONDS);
        assertEquals(InputStatus.MORE_AVAILABLE, gate.emitNext(rows::add, checkpoints::add));
        assertEquals(List.of("pre", "post"), rows);
        first.finish();
        second.finish();
        assertEquals(InputStatus.END_OF_INPUT, gate.emitNext(rows::add, checkpoints::add));
    }

    @Test
    void earlyProducerEofDeclinesCheckpointButKeepsDataPipelineHealthy() throws Exception {
        InputGate<String> gate = new InputGate<>(1, 2);
        ResultPartition<String> first = new ResultPartition<>(0, List.of(gate));
        ResultPartition<String> second = new ResultPartition<>(1, List.of(gate));
        first.broadcastBarrier(7);
        CountDownLatch started = new CountDownLatch(1);
        CompletableFuture<Void> post = writeLater(first, "post", started);
        assertTrue(started.await(2, TimeUnit.SECONDS));

        // Producer #1 finishes without a barrier. Decline only checkpoint #7.
        second.finish();
        post.get(2, TimeUnit.SECONDS);
        first.finish();
        List<String> rows = new ArrayList<>();
        List<Long> declined = new ArrayList<>();
        InputGate.BarrierHandler handler = new InputGate.BarrierHandler() {
            @Override
            public void onBarrier(long id) {
                throw new AssertionError("Incomplete checkpoint cannot be completed");
            }

            @Override
            public void onCheckpointDeclined(long id) {
                declined.add(id);
            }
        };
        assertEquals(InputStatus.MORE_AVAILABLE, gate.emitNext(rows::add, handler));
        assertEquals(List.of(7L), declined);
        assertEquals(InputStatus.MORE_AVAILABLE, gate.emitNext(rows::add, handler));
        assertEquals(List.of("post"), rows);
        assertEquals(InputStatus.END_OF_INPUT, gate.emitNext(rows::add, handler));
    }

    @Test
    void overlappingProducerBarriersRemainInvalid() throws Exception {
        InputGate<String> gate = new InputGate<>(1, 2);
        ResultPartition<String> first = new ResultPartition<>(0, List.of(gate));
        ResultPartition<String> second = new ResultPartition<>(1, List.of(gate));
        first.emitRecord(0, "full");
        // Control barriers bypass the full data-buffer budget, without permitting overlap.
        first.broadcastBarrier(1);
        assertThrows(IllegalStateException.class, () -> second.broadcastBarrier(2));
        assertEquals(InputStatus.MORE_AVAILABLE, gate.emitNext(ignored -> {}, id -> {}));
        assertEquals(InputStatus.MORE_AVAILABLE, gate.emitNext(ignored -> {}, id -> {}));
        second.broadcastBarrier(1);
        assertEquals(InputStatus.MORE_AVAILABLE, gate.emitNext(ignored -> {}, id -> {}));
    }

    @Test
    void declinedCheckpointMustUnblockItsWriterAndAllowTheNextCheckpoint() throws Exception {
        InputGate<String> gate = new InputGate<>(1, 2);
        ResultPartition<String> first = new ResultPartition<>(0, List.of(gate));
        ResultPartition<String> second = new ResultPartition<>(1, List.of(gate));
        List<String> records = new ArrayList<>();
        List<Long> completed = new ArrayList<>();
        List<Long> declined = new ArrayList<>();
        InputGate.BarrierHandler handler = new InputGate.BarrierHandler() {
            @Override
            public void onBarrier(long id) {
                completed.add(id);
            }

            @Override
            public void onCheckpointDeclined(long id) {
                declined.add(id);
            }
        };

        first.emitRecord(0, "before-aborted");
        first.broadcastBarrier(1);
        CountDownLatch started = new CountDownLatch(1);
        CompletableFuture<Void> postBarrier = writeLater(first, "after-aborted", started);
        assertTrue(started.await(2, TimeUnit.SECONDS));

        assertEquals(InputStatus.MORE_AVAILABLE, gate.emitNext(records::add, handler));
        assertEquals(InputStatus.MORE_AVAILABLE, gate.emitNext(records::add, handler));
        assertFalse(postBarrier.isDone());
        assertEquals(InputStatus.NOTHING_AVAILABLE, gate.emitNext(records::add, handler));

        gate.declineCheckpoint(1);
        assertEquals(InputStatus.MORE_AVAILABLE, gate.emitNext(records::add, handler));
        postBarrier.get(2, TimeUnit.SECONDS);
        assertEquals(List.of(1L), declined);
        assertEquals(List.of(), completed);
        assertEquals(InputStatus.MORE_AVAILABLE, gate.emitNext(records::add, handler));
        assertEquals(List.of("before-aborted", "after-aborted"), records);

        // The declined barrier must not poison the next alignment.
        first.broadcastBarrier(2);
        second.emitRecord(0, "before-second");
        assertEquals(InputStatus.MORE_AVAILABLE, gate.emitNext(records::add, handler));
        assertEquals(InputStatus.MORE_AVAILABLE, gate.emitNext(records::add, handler));
        assertEquals(List.of(1L), declined);
        second.broadcastBarrier(2);
        assertEquals(InputStatus.MORE_AVAILABLE, gate.emitNext(records::add, handler));
        assertEquals(List.of(2L), completed);

        first.emitRecord(0, "after-second");
        assertEquals(InputStatus.MORE_AVAILABLE, gate.emitNext(records::add, handler));
        first.finish();
        second.finish();
        assertEquals(InputStatus.END_OF_INPUT, gate.emitNext(records::add, handler));
        assertEquals(List.of("before-aborted", "after-aborted", "before-second", "after-second"), records);
    }

    @Test
    void consecutiveAlignedCheckpointsMustRemainOrderedAcrossBothProducers() throws Exception {
        InputGate<String> gate = new InputGate<>(2, 2);
        ResultPartition<String> first = new ResultPartition<>(0, List.of(gate));
        ResultPartition<String> second = new ResultPartition<>(1, List.of(gate));
        List<String> records = new ArrayList<>();
        List<Long> checkpoints = new ArrayList<>();

        for (int checkpointId = 1; checkpointId <= 2; checkpointId++) {
            first.emitRecord(0, "first-" + checkpointId);
            second.emitRecord(0, "second-" + checkpointId);
            first.broadcastBarrier(checkpointId);
            second.broadcastBarrier(checkpointId);
            // Each barrier follows the data from its own producer.
            assertEquals(InputStatus.MORE_AVAILABLE, gate.emitNext(records::add, checkpoints::add));
            assertEquals(InputStatus.MORE_AVAILABLE, gate.emitNext(records::add, checkpoints::add));
            assertEquals(InputStatus.MORE_AVAILABLE, gate.emitNext(records::add, checkpoints::add));
            assertEquals(InputStatus.MORE_AVAILABLE, gate.emitNext(records::add, checkpoints::add));
        }
        assertEquals(List.of(1L, 2L), checkpoints);
        assertEquals(List.of("first-1", "second-1", "first-2", "second-2"), records);
        assertThrows(IllegalStateException.class, () -> first.broadcastBarrier(1));

        first.finish();
        second.finish();
        assertEquals(InputStatus.END_OF_INPUT, gate.emitNext(records::add, checkpoints::add));
    }

    private static CompletableFuture<Void> writeLater(
            ResultPartition<String> partition, String value, CountDownLatch started) {
        return CompletableFuture.runAsync(() -> {
            started.countDown();
            try {
                partition.emitRecord(0, value);
            } catch (Exception error) {
                throw new CompletionException(error);
            }
        });
    }
}
