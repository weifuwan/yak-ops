package io.yak.ops.cdc.runtime.api.source;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNotEquals;
import static org.junit.jupiter.api.Assertions.assertSame;
import static org.junit.jupiter.api.Assertions.assertTrue;

import io.yak.ops.cdc.runtime.api.checkpoint.CheckpointListener;
import java.util.Collections;
import java.util.Map;
import org.junit.jupiter.api.Test;

class SourceContractTest {
    @Test
    void sourceReadersCarryCheckpointAndAvailabilityContracts() {
        assertTrue(CheckpointListener.class.isAssignableFrom(SourceReader.class));
        assertEquals(InputStatus.NOTHING_AVAILABLE, InputStatus.valueOf("NOTHING_AVAILABLE"));
        assertEquals(2, Boundedness.values().length);
    }

    @Test
    void splitAssignmentsRetainReaderAndSplitIdentity() {
        SourceSplit split = new SourceSplit() {
            @Override
            public String splitId() {
                return "snapshot-0";
            }
        };

        SplitsAssignment<SourceSplit> assignment = new SplitsAssignment<>(split, 2);
        Map<Integer, java.util.List<SourceSplit>> assigned = assignment.assignment();
        assertEquals(Collections.singleton(2), assigned.keySet());
        assertSame(split, assigned.get(2).get(0));
        assertEquals("snapshot-0", assigned.get(2).get(0).splitId());
    }

    @Test
    void readerIdentityUsesSubtaskAndLocation() {
        ReaderInfo reader = new ReaderInfo(0, "local");
        assertEquals(reader, new ReaderInfo(0, "local"));
        assertEquals(reader.hashCode(), new ReaderInfo(0, "local").hashCode());
        assertNotEquals(reader, new ReaderInfo(1, "local"));
    }
}
