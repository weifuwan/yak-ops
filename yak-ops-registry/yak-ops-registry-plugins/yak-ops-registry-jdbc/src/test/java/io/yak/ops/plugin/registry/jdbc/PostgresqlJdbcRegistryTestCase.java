package io.yak.ops.plugin.registry.jdbc;

import io.yak.ops.common.sql.SqlScriptRunner;

import java.util.stream.Stream;

import org.junit.jupiter.api.AfterAll;
import org.junit.jupiter.api.BeforeAll;
import org.springframework.boot.autoconfigure.SpringBootApplication;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.test.context.ActiveProfiles;
import org.testcontainers.containers.GenericContainer;
import org.testcontainers.containers.Network;
import org.testcontainers.containers.PostgreSQLContainer;
import org.testcontainers.lifecycle.Startables;
import org.testcontainers.utility.DockerImageName;

import com.zaxxer.hikari.HikariConfig;
import com.zaxxer.hikari.HikariDataSource;

import lombok.SneakyThrows;

@ActiveProfiles("postgresql")
@SpringBootTest(classes = {JdbcRegistryProperties.class})
@SpringBootApplication(scanBasePackageClasses = JdbcRegistryProperties.class)
public class PostgresqlJdbcRegistryTestCase extends JdbcRegistryTestCase {

    private static GenericContainer<?> postgresqlContainer;

    @SneakyThrows
    @BeforeAll
    public static void setUpTestingServer() {
        postgresqlContainer = new PostgreSQLContainer(DockerImageName.parse("postgres:16.0"))
                .withUsername("root")
                .withPassword("root")
                .withDatabaseName("yak-ops")
                .withNetwork(Network.newNetwork())
                .withExposedPorts(5432);

        Startables.deepStart(Stream.of(postgresqlContainer)).join();

        String jdbcUrl = "jdbc:postgresql://localhost:" + postgresqlContainer.getMappedPort(5432) + "/yak-ops";
        System.clearProperty("spring.datasource.url");
        System.setProperty("spring.datasource.url", jdbcUrl);

        HikariConfig config = new HikariConfig();
        config.setJdbcUrl(jdbcUrl);
        config.setUsername("root");
        config.setPassword("root");

        try (HikariDataSource dataSource = new HikariDataSource(config)) {
            SqlScriptRunner sqlScriptRunner = new SqlScriptRunner(dataSource, "postgresql_registry_init.sql");
            sqlScriptRunner.execute();
        }
    }

    @SneakyThrows
    @AfterAll
    public static void tearDownTestingServer() {
        postgresqlContainer.close();
    }
}
