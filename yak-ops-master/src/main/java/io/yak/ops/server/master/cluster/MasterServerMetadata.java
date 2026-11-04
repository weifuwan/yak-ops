package io.yak.ops.server.master.cluster;

import static com.google.common.base.Preconditions.checkNotNull;

import io.yak.ops.common.constants.Constants;
import io.yak.ops.common.model.MasterHeartBeat;

import lombok.Data;
import lombok.EqualsAndHashCode;
import lombok.ToString;
import lombok.experimental.SuperBuilder;

@Data
@ToString(callSuper = true)
@SuperBuilder
@EqualsAndHashCode(callSuper = true)
public class MasterServerMetadata extends BaseServerMetadata implements Comparable<MasterServerMetadata> {

    public static MasterServerMetadata parseFromHeartBeat(final MasterHeartBeat masterHeartBeat) {
        checkNotNull(masterHeartBeat);
        return MasterServerMetadata.builder()
                .processId(masterHeartBeat.getProcessId())
                .serverStartupTime(masterHeartBeat.getStartupTime())
                .address(masterHeartBeat.getHost() + Constants.COLON + masterHeartBeat.getPort())
                .cpuUsage(masterHeartBeat.getCpuUsage())
                .memoryUsage(masterHeartBeat.getMemoryUsage())
                .serverStatus(masterHeartBeat.getServerStatus())
                .build();
    }

    // Use the master address to sort the master server
    @Override
    public int compareTo(final MasterServerMetadata o) {
        return this.getAddress().compareTo(o.getAddress());
    }

}
