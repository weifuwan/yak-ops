package io.yak.ops.plugin.registry.etcd;

import java.io.IOException;
import java.util.Optional;

import org.junit.jupiter.api.AfterAll;
import org.junit.jupiter.api.Assertions;
import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.Test;

import io.etcd.jetcd.Client;
import io.etcd.jetcd.launcher.EtcdCluster;
import io.etcd.jetcd.test.EtcdClusterExtension;

class EtcdKeepAliveLeaseManagerTest {

    static EtcdClusterExtension server;

    static Client client;

    static EtcdKeepAliveLeaseManager etcdKeepAliveLeaseManager;

    @BeforeAll
    public static void before() throws Exception {
        server = EtcdClusterExtension.builder()
                .withNodes(1)
                .withImage("ibmcom/etcd:3.2.24")
                .build();
        server.cluster().start();

        client = Client.builder().endpoints(server.clientEndpoints()).build();

        etcdKeepAliveLeaseManager = new EtcdKeepAliveLeaseManager(client);
    }

    @Test
    void getOrCreateKeepAliveLeaseTest() throws Exception {
        long first = etcdKeepAliveLeaseManager.getOrCreateKeepAliveLease("/test", 3);
        long second = etcdKeepAliveLeaseManager.getOrCreateKeepAliveLease("/test", 3);
        Assertions.assertEquals(first, second);

        client.getLeaseClient().revoke(first).get();

        // wait for lease expire
        Thread.sleep(3000);
        Optional<Long> keepAliveLease = etcdKeepAliveLeaseManager.getKeepAliveLease("/test");
        Assertions.assertFalse(keepAliveLease.isPresent());
    }

    @AfterAll
    public static void after() throws IOException {
        try (
                EtcdCluster closeServer = server.cluster();
                Client closedClient = client) {
        }
    }
}
