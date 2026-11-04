package io.yak.ops.server.master.cluster;

import io.yak.ops.common.constants.Constants;
import io.yak.ops.common.model.WorkerHeartBeat;

import lombok.Builder;
import lombok.Data;
import lombok.EqualsAndHashCode;
import lombok.experimental.SuperBuilder;

@Data
@SuperBuilder
@EqualsAndHashCode(callSuper = true)
public class WorkerServerMetadata extends BaseServerMetadata {

    @Builder.Default
    private final String workerGroup = "default";

    // Only used in FixedWeightedRoundRobinWorkerLoadBalancer
    @Builder.Default
    private final double workerWeight = 1;

    private final double taskThreadPoolUsage;

    public static WorkerServerMetadata parseFromHeartBeat(final WorkerHeartBeat workerHeartBeat) {
        return WorkerServerMetadata.builder()
                .processId(workerHeartBeat.getProcessId())
                .serverStartupTime(workerHeartBeat.getStartupTime())
                .address(workerHeartBeat.getHost() + Constants.COLON + workerHeartBeat.getPort())
                .workerGroup(workerHeartBeat.getWorkerGroup())
                .cpuUsage(workerHeartBeat.getCpuUsage())
                .memoryUsage(workerHeartBeat.getMemoryUsage())
                .serverStatus(workerHeartBeat.getServerStatus())
                .workerWeight(workerHeartBeat.getWorkerHostWeight())
                .taskThreadPoolUsage(workerHeartBeat.getThreadPoolUsage())
                .build();
    }

}
