package io.yak.ops.server.master.cluster;

import io.yak.ops.common.enums.ServerStatus;
import io.yak.ops.common.model.WorkerHeartBeat;

import org.junit.jupiter.api.Test;

import com.google.common.truth.Truth;

class WorkerServerMetadataTest {

    @Test
    void parseFromHeartBeat() {
        WorkerHeartBeat workerHeartBeat = WorkerHeartBeat.builder()
                .processId(30001)
                .startupTime(System.currentTimeMillis())
                .reportTime(System.currentTimeMillis())
                .jvmCpuUsage(0.1)
                .cpuUsage(0.2)
                .jvmMemoryUsage(0.3)
                .memoryUsage(0.4)
                .diskUsage(0.5)
                .serverStatus(ServerStatus.NORMAL)
                .host("localhost")
                .port(12345)
                .workerHostWeight(2)
                .threadPoolUsage(0.6)
                .workerGroup("test")
                .build();
        WorkerServerMetadata workerServerMetadata = WorkerServerMetadata.parseFromHeartBeat(workerHeartBeat);
        Truth.assertThat(workerServerMetadata.getCpuUsage()).isEqualTo(0.2);
        Truth.assertThat(workerServerMetadata.getMemoryUsage()).isEqualTo(0.4);
        Truth.assertThat(workerServerMetadata.getServerStatus()).isEqualTo(ServerStatus.NORMAL);
        Truth.assertThat(workerServerMetadata.getAddress()).isEqualTo("localhost:12345");
        Truth.assertThat(workerServerMetadata.getWorkerWeight()).isEqualTo(2);
        Truth.assertThat(workerServerMetadata.getTaskThreadPoolUsage()).isEqualTo(0.6);
        Truth.assertThat(workerServerMetadata.getWorkerGroup()).isEqualTo("test");
    }
}
