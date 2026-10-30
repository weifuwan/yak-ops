package io.yak.ops.sync.runtime.plan;

import io.yak.ops.plugin.task.api.execution.ExecutionResource;
import java.util.Objects;

/**
 * Stable replication identity for attempts of the same execution instance.
 *
 * <p>The exclusive claim is scoped by the calling execution platform.
 */
public final class MySqlCdcReplicationIdentity {

    private MySqlCdcReplicationIdentity() {}

    /** Returns the first ID of an aligned block reserved for one Binlog and sixteen snapshot readers. */
    public static int serverId(String instanceId) {
        Objects.requireNonNull(instanceId, "instanceId");
        return Math.floorMod(instanceId.hashCode(), 62_500_000) * 32 + 1;
    }

    /** Returns the stable Debezium topic namespace for one instance. */
    public static String topicPrefix(String instanceId) {
        Objects.requireNonNull(instanceId, "instanceId");
        return "yakflow_" + instanceId;
    }

    /** Declares the aligned replication block resource for atomic admission; all platform instances use this allocator. */
    public static ExecutionResource exclusiveResource(String instanceId) {
        return new ExecutionResource("MYSQL_CDC_SERVER_ID", Integer.toString(serverId(instanceId)));
    }
}
