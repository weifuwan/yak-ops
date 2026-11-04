package io.yak.ops.extract.base.metrics;

import io.yak.ops.common.utils.NetUtils;
import io.yak.ops.extract.base.SyncRequestDto;
import io.yak.ops.extract.base.protocal.Transporter;

import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class ClientSyncDurationMetrics {

    private Transporter transporter;

    private long milliseconds;

    @Builder.Default
    private String clientHost = NetUtils.getHost();

    private String serverHost;

    public static ClientSyncDurationMetrics of(SyncRequestDto syncRequestDto) {
        return ClientSyncDurationMetrics.builder()
                .transporter(syncRequestDto.getTransporter())
                .serverHost(syncRequestDto.getServerHost().getIp())
                .build();
    }

    public ClientSyncDurationMetrics withMilliseconds(long milliseconds) {
        this.milliseconds = milliseconds;
        return this;
    }

}
