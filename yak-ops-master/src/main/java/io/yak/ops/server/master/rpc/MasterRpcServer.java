package io.yak.ops.server.master.rpc;

import io.yak.ops.extract.base.config.NettyServerConfig;
import io.yak.ops.extract.base.server.SpringServerMethodInvokerDiscovery;
import io.yak.ops.server.master.config.MasterConfig;

import org.springframework.stereotype.Component;

import lombok.extern.slf4j.Slf4j;

@Component
@Slf4j
public class MasterRpcServer extends SpringServerMethodInvokerDiscovery implements AutoCloseable {

    public MasterRpcServer(MasterConfig masterConfig) {
        super(NettyServerConfig.builder().serverName("MasterRpcServer").listenPort(masterConfig.getListenPort())
                .build());
    }

}
