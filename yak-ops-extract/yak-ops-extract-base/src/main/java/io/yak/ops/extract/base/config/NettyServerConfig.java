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
public class NettyServerConfig {

    private String serverName;

    /**
     * init the server connectable queue
     */
    @Builder.Default
    private int soBacklog = 1024;

    /**
     * whether tcp delay
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

    /**
     * worker threads，default get machine cpus
     */
    @Builder.Default
    private int workerThread = Runtime.getRuntime().availableProcessors() * 2;

    /**
     * If done's receive any data from a {@link io.netty.channel.Channel} during 180s then will close it.
     */
    @Builder.Default
    private long connectionIdleTime = Duration.ofSeconds(60).toMillis();

    private int listenPort;

}
