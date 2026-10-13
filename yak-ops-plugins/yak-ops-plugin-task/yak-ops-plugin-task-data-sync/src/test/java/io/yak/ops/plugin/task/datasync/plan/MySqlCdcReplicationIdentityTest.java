package io.yak.ops.plugin.task.datasync.plan;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNotEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

import io.yak.ops.plugin.task.api.execution.TaskExecutionIdentity;
import org.junit.jupiter.api.Test;

/** Verifies stable CDC replication identity and collision detection through resource admission. */
class MySqlCdcReplicationIdentityTest {
    @Test
    void keepsServerIdAndTopicPrefixStableForRepeatedInstancePlanning() {
        TaskExecutionIdentity first = new TaskExecutionIdentity("ws", "definition", 1, "instance-123");
        TaskExecutionIdentity next = new TaskExecutionIdentity("ws", "definition", 1, "instance-123");
        assertEquals(MySqlCdcReplicationIdentity.serverId(first.instanceId()), MySqlCdcReplicationIdentity.serverId(next.instanceId()));
        assertEquals(MySqlCdcReplicationIdentity.topicPrefix(first.instanceId()), MySqlCdcReplicationIdentity.topicPrefix(next.instanceId()));
        assertEquals(MySqlCdcReplicationIdentity.exclusiveResource(first.instanceId()), MySqlCdcReplicationIdentity.exclusiveResource(next.instanceId()));
        assertTrue(MySqlCdcReplicationIdentity.serverId(first.instanceId()) > 0);
    }
    @Test
    void exposesHashCollisionsAsTheSameExclusiveAdmissionClaim() {
        String first = "Aa";
        String second = "BB";
        assertNotEquals(first, second);
        assertEquals(MySqlCdcReplicationIdentity.serverId(first), MySqlCdcReplicationIdentity.serverId(second));
        assertEquals(MySqlCdcReplicationIdentity.exclusiveResource(first), MySqlCdcReplicationIdentity.exclusiveResource(second));
        assertNotEquals(MySqlCdcReplicationIdentity.topicPrefix(first), MySqlCdcReplicationIdentity.topicPrefix(second));
    }
}
