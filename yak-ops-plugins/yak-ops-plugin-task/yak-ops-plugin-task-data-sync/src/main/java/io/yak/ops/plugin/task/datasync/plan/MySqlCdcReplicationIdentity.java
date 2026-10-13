package io.yak.ops.plugin.task.datasync.plan;

import io.yak.ops.plugin.task.api.execution.ExecutionResource;
import java.util.Objects;

/**
 * Stable replication identity for attempts of the same execution instance.
 *
 * <p>The exclusive claim is scoped by the calling execution platform.
 */
public final class MySqlCdcReplicationIdentity {

    private MySqlCdcReplicationIdentity() {}

    /** Returns the stable positive replication server ID for one instance. */
    public static int serverId(String instanceId) {
        Objects.requireNonNull(instanceId, "instanceId");
        return Math.floorMod(instanceId.hashCode(), 2_000_000_000) + 1;
    }

    /** Returns the stable Debezium topic namespace for one instance. */
    public static String topicPrefix(String instanceId) {
        Objects.requireNonNull(instanceId, "instanceId");
        return "yakflow_" + instanceId;
    }

    /** Declares the replication server-ID resource for atomic admission. */
    public static ExecutionResource exclusiveResource(String instanceId) {
        return new ExecutionResource("MYSQL_CDC_SERVER_ID", Integer.toString(serverId(instanceId)));
    }
}
