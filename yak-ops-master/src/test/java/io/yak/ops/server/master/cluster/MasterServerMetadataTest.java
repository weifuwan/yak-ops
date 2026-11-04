package io.yak.ops.server.master.cluster;

import io.yak.ops.common.enums.ServerStatus;
import io.yak.ops.common.model.MasterHeartBeat;

import org.junit.jupiter.api.Test;

import com.google.common.truth.Truth;

class MasterServerMetadataTest {

    @Test
    void parseFromHeartBeat() {
        MasterHeartBeat masterHeartBeat = MasterHeartBeat.builder()
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
                .build();
        MasterServerMetadata masterServer = MasterServerMetadata.parseFromHeartBeat(masterHeartBeat);
        Truth.assertThat(masterServer.getCpuUsage()).isEqualTo(0.2);
        Truth.assertThat(masterServer.getMemoryUsage()).isEqualTo(0.4);
        Truth.assertThat(masterServer.getServerStatus()).isEqualTo(ServerStatus.NORMAL);
        Truth.assertThat(masterServer.getAddress()).isEqualTo("localhost:12345");
    }
}
