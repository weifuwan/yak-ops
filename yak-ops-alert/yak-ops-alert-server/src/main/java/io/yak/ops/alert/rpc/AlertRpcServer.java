package io.yak.ops.alert.rpc;

import io.yak.ops.alert.config.AlertConfig;
import io.yak.ops.extract.base.config.NettyServerConfig;
import io.yak.ops.extract.base.server.SpringServerMethodInvokerDiscovery;

import org.springframework.stereotype.Service;

import lombok.extern.slf4j.Slf4j;

@Slf4j
@Service
public class AlertRpcServer extends SpringServerMethodInvokerDiscovery implements AutoCloseable {

    public AlertRpcServer(AlertConfig alertConfig) {
        super(NettyServerConfig.builder().serverName("AlertRpcServer").listenPort(alertConfig.getPort()).build());
    }

}
