package io.yak.ops.dao.repository.task.impl;

import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

import java.time.LocalDateTime;
import org.junit.jupiter.api.Test;

class TaskGroupClaimTest {
    private static final LocalDateTime NOW = LocalDateTime.of(2026, 10, 31, 1, 0);

    @Test
    void scopeConflictsOnlyWithItsExactLengthEncodedDatasource() {
        TaskGroupClaim scope = claim("a", "DATA_SYNC_TARGET_SCOPE", "4:sink", 0, NOW);
        TaskGroupClaim table = claim("b", "DATA_SYNC_TARGET", "4:sink2:db-1:1:a", 0, NOW);
        TaskGroupClaim otherTable = claim("c", "DATA_SYNC_TARGET", "4:sink2:db-1:1:b", 0, NOW);
        TaskGroupClaim otherDatasource = claim("d", "DATA_SYNC_TARGET", "5:sink22:db-1:1:a", 0, NOW);
        assertTrue(scope.conflicts(table));
        assertTrue(table.conflicts(scope));
        assertTrue(scope.conflicts(otherTable));
        assertFalse(table.conflicts(otherTable));
        assertFalse(scope.conflicts(otherDatasource));
        assertThrows(IllegalArgumentException.class,
                () -> claim("e", "DATA_SYNC_TARGET_SCOPE", "4:sink-extra", 0, NOW));
        assertThrows(IllegalArgumentException.class,
                () -> claim("e", "DATA_SYNC_TARGET", "4:sink", 0, NOW));
    }

    @Test
    void priorityPrecedesTimeAndInstanceIdBreaksTimestampTies() {
        TaskGroupClaim first = claim("a", "DATA_SYNC_TARGET_SCOPE", "4:sink", 0, NOW);
        TaskGroupClaim second = claim("b", "DATA_SYNC_TARGET_SCOPE", "4:sink", 0, NOW);
        TaskGroupClaim older = claim("c", "DATA_SYNC_TARGET_SCOPE", "4:sink", 0, NOW.minusSeconds(1));
        TaskGroupClaim priority = claim("d", "DATA_SYNC_TARGET_SCOPE", "4:sink", 1, NOW.plusSeconds(1));
        assertTrue(first.order().precedes(second.order()));
        assertFalse(second.order().precedes(first.order()));
        assertTrue(older.order().precedes(first.order()));
        assertTrue(priority.order().precedes(older.order()));
    }

    @Test
    void allResourceTypesShareTheEarliestInstanceTimeAndHighestPriority() {
        TaskGroupClaim first = claim("a", "DATA_SYNC_TARGET_SCOPE", "4:sink", 0, NOW);
        TaskGroupClaim later = claim("a", "MYSQL_CDC_SERVER_ID", "31700", 5, NOW.plusSeconds(1));
        TaskGroupClaim other = claim("b", "MYSQL_CDC_SERVER_ID", "31700", 5, NOW.plusNanos(1));
        assertTrue(first.order().merge(later.order()).precedes(other.order()));
        assertTrue(later.conflicts(other));
        assertFalse(later.conflicts(first));
        assertFalse(later.exclusiveTarget());
        assertTrue(first.exclusiveTarget());
    }

    private static TaskGroupClaim claim(String id, String type, String key, int priority, LocalDateTime time) {
        return new TaskGroupClaim(id, type, key, priority, false, time);
    }
}
