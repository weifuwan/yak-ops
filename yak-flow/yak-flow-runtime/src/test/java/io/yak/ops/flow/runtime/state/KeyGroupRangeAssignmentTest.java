package io.yak.ops.flow.runtime.state;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNotEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

import io.yak.ops.flow.runtime.io.partitioner.KeyedPartitioner;
import org.junit.jupiter.api.Test;

class KeyGroupRangeAssignmentTest {

    @Test
    void shouldUseFlinkCompatibleMurmurKeyGroupHash() {
        assertEquals(104, KeyGroupRangeAssignment.assignToKeyGroup("A", 128));
        assertEquals(17, KeyGroupRangeAssignment.assignToKeyGroup("B", 128));
        assertEquals(1, KeyGroupRangeAssignment.assignKeyToParallelOperator("A", 128, 2));
        assertEquals(0, KeyGroupRangeAssignment.assignKeyToParallelOperator("B", 128, 2));
        assertEquals(6, KeyGroupRangeAssignment.assignKeyToParallelOperator("A", 128, 8));
        assertEquals(1, KeyGroupRangeAssignment.assignKeyToParallelOperator("B", 128, 8));
    }

    @Test
    void shouldRetainKeyGroupIdentityAcrossParallelismChangesAndCoverAllGroups() {
        int max = 128;
        for (int parallelism : new int[] {1, 2, 3, 5, 8, 16}) {
            for (int group = 0; group < max; group++) {
                int target = KeyGroupRangeAssignment.computeOperatorIndexForKeyGroup(max, parallelism, group);
                assertTrue(target >= 0 && target < parallelism);
                assertTrue(KeyGroupRangeAssignment.computeKeyGroupRangeForOperatorIndex(
                        max, parallelism, target).contains(group));
            }
        }
        assertEquals(new KeyGroupRange(43, 85),
                KeyGroupRangeAssignment.computeKeyGroupRangeForOperatorIndex(128, 3, 1));
        assertNotEquals(
                KeyGroupRangeAssignment.computeOperatorIndexForKeyGroup(128, 2, 104),
                KeyGroupRangeAssignment.computeOperatorIndexForKeyGroup(128, 2, 17));
    }

    @Test
    void shouldRejectInvalidParallelismAndArrayKeys() throws Exception {
        assertThrows(IllegalArgumentException.class,
                () -> KeyGroupRangeAssignment.assignToKeyGroup("A", 0));
        assertThrows(IllegalArgumentException.class,
                () -> KeyGroupRangeAssignment.assignToKeyGroup("A", 32769));
        assertThrows(IllegalArgumentException.class,
                () -> KeyGroupRangeAssignment.computeOperatorIndexForKeyGroup(16, 17, 0));
        assertThrows(IllegalArgumentException.class,
                () -> KeyGroupRangeAssignment.computeOperatorIndexForKeyGroup(16, 2, 16));
        assertThrows(IllegalArgumentException.class,
                () -> new KeyedPartitioner<>((String value) -> value, 0));

        KeyedPartitioner<String> partitioner = new KeyedPartitioner<>(value -> value, 128);
        assertEquals(128, partitioner.getMaxParallelism());
        assertEquals(1, partitioner.selectChannel("A", 2));
        assertThrows(IllegalArgumentException.class, () -> partitioner.selectChannel("A", 129));
        assertThrows(IllegalArgumentException.class, () -> new KeyedPartitioner<>(
                (String ignored) -> new byte[] {1}, 128).selectChannel("A", 2));
    }
}
