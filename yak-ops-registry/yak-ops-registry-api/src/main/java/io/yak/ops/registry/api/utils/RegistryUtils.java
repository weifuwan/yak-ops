package io.yak.ops.registry.api.utils;

import io.yak.ops.common.model.BaseHeartBeat;
import io.yak.ops.registry.api.enums.RegistryNodeType;

import com.google.common.base.Preconditions;

public class RegistryUtils {

    public static String getMasterFailoverLockPath(final String masterAddress) {
        Preconditions.checkNotNull(masterAddress, "master address cannot be null");
        return RegistryNodeType.MASTER_FAILOVER_LOCK.getRegistryPath() + "/" + masterAddress;
    }

    public static String getGlobalMasterFailoverNodePath(final String serverAddress) {
        return RegistryNodeType.FAILOVER_FINISH_NODES.getRegistryPath() + "/" + serverAddress + "-" + "unknown" + "-"
                + "unknown";
    }

    public static String getFailoveredNodePath(final BaseHeartBeat baseHeartBeat) {
        return getFailoveredNodePath(
                baseHeartBeat.getHost() + ":" + baseHeartBeat.getPort(),
                baseHeartBeat.getStartupTime(),
                baseHeartBeat.getProcessId());
    }

    public static String getFailoveredNodePath(final String serverAddress, final long serverStartupTime,
                                               final int processId) {
        return RegistryNodeType.FAILOVER_FINISH_NODES.getRegistryPath() + "/" + serverAddress + "-" + serverStartupTime
                + "-" + processId;
    }
}
