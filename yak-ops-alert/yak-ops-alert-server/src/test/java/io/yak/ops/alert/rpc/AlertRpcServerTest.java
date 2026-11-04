package io.yak.ops.alert.rpc;

import io.yak.ops.alert.config.AlertConfig;

import org.junit.jupiter.api.Test;

class AlertRpcServerTest {

    private final AlertRpcServer alertRpcServer = new AlertRpcServer(new AlertConfig());

    @Test
    void testStart() {
        alertRpcServer.start();
    }

    @Test
    void testClose() {
        alertRpcServer.close();
    }

}
