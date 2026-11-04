package io.yak.ops.plugin.registry.etcd;

import io.yak.ops.plugin.registry.RegistryTestCase;

import java.net.URI;
import java.util.stream.Collectors;

import org.junit.jupiter.api.AfterAll;
import org.junit.jupiter.api.BeforeAll;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.autoconfigure.SpringBootApplication;
import org.springframework.boot.test.context.SpringBootTest;

import io.etcd.jetcd.launcher.EtcdCluster;
import io.etcd.jetcd.test.EtcdClusterExtension;
import lombok.SneakyThrows;

@SpringBootTest(classes = EtcdRegistryProperties.class)
@SpringBootApplication(scanBasePackageClasses = EtcdRegistryProperties.class)
public class EtcdRegistryTestCase extends RegistryTestCase<EtcdRegistry> {

    @Autowired
    private EtcdRegistryProperties etcdRegistryProperties;

    private static EtcdCluster etcdCluster;

    @SneakyThrows
    @BeforeAll
    public static void setUpTestingServer() {
        etcdCluster = EtcdClusterExtension.builder()
                .withNodes(1)
                .withImage("ibmcom/etcd:3.2.24")
                .build()
                .cluster();
        etcdCluster.start();
        System.clearProperty("registry.endpoints");
        System.setProperty("registry.endpoints",
                etcdCluster.clientEndpoints().stream().map(URI::toString).collect(Collectors.joining(",")));
    }

    @SneakyThrows
    @Override
    public EtcdRegistry createRegistry() {
        return new EtcdRegistry(etcdRegistryProperties);
    }

    @SneakyThrows
    @AfterAll
    public static void tearDownTestingServer() {
        try (EtcdCluster cluster = etcdCluster) {
        }
    }
}
