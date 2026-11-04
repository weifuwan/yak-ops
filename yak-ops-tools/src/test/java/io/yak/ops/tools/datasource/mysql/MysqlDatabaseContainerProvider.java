package io.yak.ops.tools.datasource.mysql;

import io.yak.ops.tools.datasource.jupiter.DatabaseContainerProvider;
import io.yak.ops.tools.datasource.jupiter.YakOpsDatabaseContainer;

import java.util.stream.Stream;

import org.testcontainers.containers.GenericContainer;
import org.testcontainers.containers.MySQLContainer;
import org.testcontainers.containers.Network;
import org.testcontainers.containers.wait.strategy.Wait;
import org.testcontainers.lifecycle.Startables;
import org.testcontainers.utility.DockerImageName;

import com.google.auto.service.AutoService;

import lombok.extern.slf4j.Slf4j;

@Slf4j
@AutoService(DatabaseContainerProvider.class)
public class MysqlDatabaseContainerProvider implements DatabaseContainerProvider {

    private static final Network NETWORK = Network.newNetwork();

    @Override
    public GenericContainer<?> getContainer(YakOpsDatabaseContainer dataSourceContainer) {
        GenericContainer mysqlContainer = new MySQLContainer(DockerImageName.parse(dataSourceContainer.imageName()))
                .withUsername("root")
                .withPassword("root")
                .withDatabaseName("yak-ops")
                .withNetwork(NETWORK)
                .withExposedPorts(3306)
                .waitingFor(Wait.forHealthcheck());

        log.info("Create {} successfully.", mysqlContainer.getDockerImageName());
        mysqlContainer.start();

        log.info("Starting {}...", mysqlContainer.getDockerImageName());
        Startables.deepStart(Stream.of(mysqlContainer)).join();
        log.info("{} started", mysqlContainer.getDockerImageName());

        String jdbcUrl = "jdbc:mysql://localhost:" + mysqlContainer.getMappedPort(3306)
                + "/yak-ops?useUnicode=true&characterEncoding=UTF-8";
        System.clearProperty("spring.datasource.url");
        System.setProperty("spring.datasource.url", jdbcUrl);

        return mysqlContainer;
    }

    @Override
    public String getType() {
        return "mysql";
    }

}
