package io.yak.ops.server.worker.rpc;

import io.yak.ops.server.worker.config.WorkerConfig;

import org.junit.jupiter.api.Assertions;
import org.junit.jupiter.api.Test;

class WorkerRpcServerTest {

    private final WorkerRpcServer workerRpcServer = new WorkerRpcServer(new WorkerConfig());

    @Test
    void testStart() {
        Assertions.assertDoesNotThrow(workerRpcServer::start);
    }

    @Test
    void testClose() {
        Assertions.assertDoesNotThrow(workerRpcServer::close);
    }

}
