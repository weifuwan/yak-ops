package io.yak.ops.business.datasync.execution.realtime;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNotEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

import org.junit.jupiter.api.Test;

class MySqlCdcServerIdAllocatorTest {

    @Test
    void shouldKeepAllocationStableWhileOwnedAndAfterRelease() {
        MySqlCdcServerIdAllocator allocator = new MySqlCdcServerIdAllocator();

        long first = allocator.allocate("workspace-1/task-1/v1");
        long second = allocator.allocate("workspace-1/task-1/v1");

        assertEquals(first, second);
        assertTrue(first >= 1L && first <= 4_294_967_295L);

        allocator.release("workspace-1/task-1/v1", first);
        assertEquals(first, allocator.allocate("workspace-1/task-1/v1"));
    }

    @Test
    void shouldAllocateDifferentStableIdsForDifferentStateKeys() {
        MySqlCdcServerIdAllocator allocator = new MySqlCdcServerIdAllocator();

        long first = allocator.allocate("workspace-1/task-1/v1");
        long second = allocator.allocate("workspace-1/task-2/v1");

        assertNotEquals(first, second);
        assertEquals(MySqlCdcServerIdAllocator.preferredServerId("workspace-1/task-1/v1"), first);
        assertEquals(MySqlCdcServerIdAllocator.preferredServerId("workspace-1/task-2/v1"), second);
    }
}
