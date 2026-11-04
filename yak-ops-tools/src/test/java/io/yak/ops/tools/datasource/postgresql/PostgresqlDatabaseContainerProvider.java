package io.yak.ops.tools.datasource.postgresql;

import io.yak.ops.tools.datasource.jupiter.DatabaseContainerProvider;
import io.yak.ops.tools.datasource.jupiter.YakOpsDatabaseContainer;

import java.util.stream.Stream;

import org.testcontainers.containers.GenericContainer;
import org.testcontainers.containers.Network;
import org.testcontainers.containers.PostgreSQLContainer;
import org.testcontainers.lifecycle.Startables;
import org.testcontainers.utility.DockerImageName;

import com.google.auto.service.AutoService;

import lombok.extern.slf4j.Slf4j;

@Slf4j
@AutoService(DatabaseContainerProvider.class)
public class PostgresqlDatabaseContainerProvider implements DatabaseContainerProvider {

    private static final Network NETWORK = Network.newNetwork();

    @SuppressWarnings("unchecked")
    @Override
    public GenericContainer<?> getContainer(YakOpsDatabaseContainer dataSourceContainer) {
        // todo: test with multiple pg version
        GenericContainer<?> postgresqlContainer =
                new PostgreSQLContainer(DockerImageName.parse(dataSourceContainer.imageName()))
                        .withUsername("root")
                        .withPassword("root")
                        .withDatabaseName("yak-ops")
                        .withNetwork(NETWORK)
                        .withExposedPorts(5432);

        log.info("Create {} successfully.", postgresqlContainer.getDockerImageName());
        postgresqlContainer.start();

        log.info("Starting {}...", postgresqlContainer.getDockerImageName());
        Startables.deepStart(Stream.of(postgresqlContainer)).join();
        log.info("{} started", postgresqlContainer.getDockerImageName());

        String jdbcUrl = "jdbc:mysql://localhost:" + postgresqlContainer.getMappedPort(5432)
                + "/yak-ops?useUnicode=true&characterEncoding=UTF-8";
        System.clearProperty("spring.datasource.url");
        System.setProperty("spring.datasource.url", jdbcUrl);
        return postgresqlContainer;
    }

    @Override
    public String getType() {
        return "postgres";
    }
}
