package io.yak.ops.extract.base;

import io.yak.ops.extract.base.protocal.Transporter;
import io.yak.ops.extract.base.utils.Host;

import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

@Data
@Builder
@AllArgsConstructor
@NoArgsConstructor
public class SyncRequestDto {

    private Host serverHost;

    private Transporter transporter;

    private long timeoutMillis;

    private RpcMethodRetryStrategy retryStrategy;

}
