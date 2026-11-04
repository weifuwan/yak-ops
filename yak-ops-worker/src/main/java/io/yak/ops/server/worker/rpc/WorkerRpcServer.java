package io.yak.ops.server.worker.rpc;

import io.yak.ops.extract.base.config.NettyServerConfig;
import io.yak.ops.extract.base.server.SpringServerMethodInvokerDiscovery;
import io.yak.ops.server.worker.config.WorkerConfig;

import java.io.Closeable;

import org.springframework.stereotype.Service;

import lombok.extern.slf4j.Slf4j;

@Slf4j
@Service
public class WorkerRpcServer extends SpringServerMethodInvokerDiscovery implements Closeable {

    public WorkerRpcServer(WorkerConfig workerConfig) {
        super(NettyServerConfig.builder().serverName("WorkerRpcServer").listenPort(workerConfig.getListenPort())
                .build());
    }

}
