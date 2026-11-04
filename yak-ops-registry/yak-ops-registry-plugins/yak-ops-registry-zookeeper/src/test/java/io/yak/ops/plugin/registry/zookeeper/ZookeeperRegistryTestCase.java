package io.yak.ops.plugin.registry.zookeeper;

import io.yak.ops.plugin.registry.RegistryTestCase;

import java.util.stream.Stream;

import org.junit.jupiter.api.AfterAll;
import org.junit.jupiter.api.BeforeAll;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.autoconfigure.SpringBootApplication;
import org.springframework.boot.test.context.SpringBootTest;
import org.testcontainers.containers.GenericContainer;
import org.testcontainers.containers.Network;
import org.testcontainers.lifecycle.Startables;
import org.testcontainers.utility.DockerImageName;

import lombok.SneakyThrows;

@SpringBootTest(classes = ZookeeperRegistryProperties.class)
@SpringBootApplication(scanBasePackageClasses = ZookeeperRegistryProperties.class)
class ZookeeperRegistryTestCase extends RegistryTestCase<ZookeeperRegistry> {

    @Autowired
    private ZookeeperRegistryProperties zookeeperRegistryProperties;

    private static GenericContainer<?> zookeeperContainer;

    private static final Network NETWORK = Network.newNetwork();

    @SneakyThrows
    @BeforeAll
    public static void setUpTestingServer() {
        zookeeperContainer = new GenericContainer<>(DockerImageName.parse("zookeeper:3.8"))
                .withNetwork(NETWORK)
                .withExposedPorts(2181);
        Startables.deepStart(Stream.of(zookeeperContainer)).join();
        System.clearProperty("registry.zookeeper.connect-string");
        System.setProperty("registry.zookeeper.connect-string", "localhost:" + zookeeperContainer.getMappedPort(2181));
    }

    @SneakyThrows
    @Override
    public ZookeeperRegistry createRegistry() {
        return new ZookeeperRegistry(zookeeperRegistryProperties);
    }

    @SneakyThrows
    @AfterAll
    public static void tearDownTestingServer() {
        zookeeperContainer.close();
    }
}
