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
public class ClientSyncExceptionMetrics {

    private Transporter transporter;

    @Builder.Default
    private String clientHost = NetUtils.getHost();

    private String serverAddress;

    private Throwable throwable;

    public static ClientSyncExceptionMetrics of(final SyncRequestDto syncRequestDto, final Throwable throwable) {
        return ClientSyncExceptionMetrics.builder()
                .transporter(syncRequestDto.getTransporter())
                .serverAddress(syncRequestDto.getServerHost().getAddress())
                .throwable(throwable)
                .build();
    }
}
