package io.yak.ops.extract.base.config;

import java.time.Duration;

import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class NettyClientConfig {

    /**
     * worker threads，default get machine cpus
     */
    @Builder.Default
    private int workerThreads = Runtime.getRuntime().availableProcessors() * 2;

    /**
     * whether tpc delay
     */
    @Builder.Default
    private boolean tcpNoDelay = true;

    /**
     * whether keep alive
     */
    @Builder.Default
    private boolean soKeepalive = true;

    @Builder.Default
    private int sendBufferSize = 65535;

    @Builder.Default
    private int receiveBufferSize = 65535;

    @Builder.Default
    private int connectTimeoutMillis = 3000;

    /**
     * Will send {@link io.yak.ops.extract.base.protocal.HeartBeatTransporter} to netty server every
     * heartBeatIntervalMillis, used to keep the {@link io.netty.channel.Channel} active.
     */
    @Builder.Default
    private long heartBeatIntervalMillis = Duration.ofSeconds(10).toMillis();

    @Builder.Default
    private int defaultRpcTimeoutMillis = 10_000;

}
