package io.yak.ops.server.master.rpc;

import io.yak.ops.server.master.config.MasterConfig;

import org.junit.jupiter.api.Assertions;
import org.junit.jupiter.api.Test;

class MasterRpcServerTest {

    private final MasterRpcServer masterRpcServer = new MasterRpcServer(new MasterConfig());

    @Test
    void testStartAndClose() {
        Assertions.assertDoesNotThrow(masterRpcServer::start);
        Assertions.assertDoesNotThrow(masterRpcServer::close);
    }
}
