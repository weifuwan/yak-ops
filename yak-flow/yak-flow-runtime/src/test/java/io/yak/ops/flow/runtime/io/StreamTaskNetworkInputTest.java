package io.yak.ops.flow.runtime.io;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

import io.yak.ops.core.api.connector.source.InputStatus;
import io.yak.ops.flow.runtime.graph.StreamEdge;
import io.yak.ops.flow.runtime.io.partition.InputGate;
import io.yak.ops.flow.runtime.io.partition.ResultPartition;
import java.util.ArrayList;
import java.util.List;
import org.junit.jupiter.api.Test;

class StreamTaskNetworkInputTest {

    @Test
    void shouldConsumeInputGateThroughCommonStreamTaskInputContract() throws Exception {
        InputGate<String> gate = new InputGate<>(2, 1);
        StreamTaskInput<String> input = new StreamTaskNetworkInput<>(gate);
        assertEquals(0, input.getInputIndex());
        assertFalse(input.getAvailableFuture().isDone());

        RecordWriterOutput<String> output = new RecordWriterOutput<>(
                new StreamEdge(1, 2), 0, 1, new ResultPartition<>(0, List.of(gate)));
        output.collect("a");
        assertTrue(input.getAvailableFuture().isDone());
        List<String> received = new ArrayList<>();
        assertEquals(InputStatus.MORE_AVAILABLE, input.emitNext(received::add));
        assertFalse(input.getAvailableFuture().isDone());
        output.collect("b");
        output.finish();
        assertEquals(InputStatus.MORE_AVAILABLE, input.emitNext(received::add));
        assertEquals(InputStatus.END_OF_INPUT, input.emitNext(received::add));
        assertEquals(List.of("a", "b"), received);
    }
}
