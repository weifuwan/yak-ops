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
        first.emitRecord(0, "after-0");
        second.emitRecord(0, "before-1");

        assertEquals(InputStatus.MORE_AVAILABLE, gate.emitNext(records::add, barriers::add));
        assertEquals(InputStatus.MORE_AVAILABLE, gate.emitNext(records::add, barriers::add));
        assertEquals(InputStatus.MORE_AVAILABLE, gate.emitNext(records::add, barriers::add));
        assertEquals(2, records.size());
        assertEquals(List.of(), barriers);
        assertEquals(InputStatus.NOTHING_AVAILABLE, gate.emitNext(records::add, barriers::add));
        assertFalse(gate.getAvailableFuture().isDone());

        second.broadcastBarrier(1);
        assertTrue(gate.getAvailableFuture().isDone());
        assertEquals(InputStatus.MORE_AVAILABLE, gate.emitNext(records::add, barriers::add));
        assertEquals(List.of(1L), barriers);
        assertEquals(InputStatus.MORE_AVAILABLE, gate.emitNext(records::add, barriers::add));
        assertEquals(List.of("before-0", "before-1", "after-0"), records);
        first.finish();
        second.finish();
        assertEquals(InputStatus.END_OF_INPUT, gate.emitNext(records::add, barriers::add));
    }

    @Test
    void barrierCanPassFullDataBufferAndMismatchedBarriersFailClosed() throws Exception {
        InputGate<String> gate = new InputGate<>(1, 2);
        ResultPartition<String> first = new ResultPartition<>(0, List.of(gate));
        ResultPartition<String> second = new ResultPartition<>(1, List.of(gate));
        first.emitRecord(0, "full");
        // Barriers do not count against the single data record slot.
        first.broadcastBarrier(1);
        second.broadcastBarrier(2);
        assertEquals(InputStatus.MORE_AVAILABLE, gate.emitNext(ignored -> {}, id -> {}));
        assertEquals(InputStatus.MORE_AVAILABLE, gate.emitNext(ignored -> {}, id -> {}));
        assertThrows(IllegalStateException.class, () -> gate.emitNext(ignored -> {}, id -> {}));
        assertTrue(gate.getAvailableFuture().isCompletedExceptionally());
    }
}
