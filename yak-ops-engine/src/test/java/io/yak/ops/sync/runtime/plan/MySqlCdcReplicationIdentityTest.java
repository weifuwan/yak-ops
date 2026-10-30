package io.yak.ops.sync.runtime.plan;

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

    @org.junit.jupiter.api.Test
    void allocatedReplicationBlocksCannotPartiallyOverlap() {
        var blocks = new java.util.TreeSet<Integer>();
        for(int i=0;i<10000;i++) {
            int base = MySqlCdcReplicationIdentity.serverId("instance-"+i);
            org.junit.jupiter.api.Assertions.assertEquals(1,base%32);
            org.junit.jupiter.api.Assertions.assertTrue(base>0 && base<=Integer.MAX_VALUE-16);
            blocks.add(base);
        }
        Integer previous=null;
        for(int base:blocks) {
            if(previous!=null) org.junit.jupiter.api.Assertions.assertTrue(base-previous>=32);
            previous=base;
        }
        // Complete block collisions remain subject to the existing atomic admission claim.
        org.junit.jupiter.api.Assertions.assertEquals(MySqlCdcReplicationIdentity.exclusiveResource("Aa"),
                MySqlCdcReplicationIdentity.exclusiveResource("BB"));
    }
}
