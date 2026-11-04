package io.yak.ops.server.master.cluster;

import io.yak.ops.common.enums.ServerStatus;

import lombok.Data;
import lombok.ToString;
import lombok.experimental.SuperBuilder;

@Data
@ToString
@SuperBuilder
public abstract class BaseServerMetadata implements IClusters.IServerMetadata {

    private final int processId;

    // The server startup time in milliseconds.
    private final long serverStartupTime;

    private final String address;

    private final double cpuUsage;

    private final double memoryUsage;

    private final ServerStatus serverStatus;

    @Override
    public String getAddress() {
        return address;
    }

    @Override
    public ServerStatus getServerStatus() {
        return serverStatus;
    }

}
