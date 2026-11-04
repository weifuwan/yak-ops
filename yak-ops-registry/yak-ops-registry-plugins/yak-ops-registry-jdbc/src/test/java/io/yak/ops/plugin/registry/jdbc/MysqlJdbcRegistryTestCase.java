package io.yak.ops.plugin.registry.jdbc;

import io.yak.ops.common.sql.SqlScriptRunner;

import java.time.Duration;
import java.util.stream.Stream;

import org.junit.jupiter.api.AfterAll;
import org.junit.jupiter.api.BeforeAll;
import org.springframework.test.context.ActiveProfiles;
import org.testcontainers.containers.GenericContainer;
import org.testcontainers.containers.MySQLContainer;
import org.testcontainers.containers.Network;
import org.testcontainers.containers.wait.strategy.Wait;
import org.testcontainers.lifecycle.Startables;
import org.testcontainers.utility.DockerImageName;

import com.zaxxer.hikari.HikariConfig;
import com.zaxxer.hikari.HikariDataSource;

import lombok.SneakyThrows;

@ActiveProfiles("mysql")
class MysqlJdbcRegistryTestCase extends JdbcRegistryTestCase {

    private static GenericContainer<?> mysqlContainer;

    @SneakyThrows
    @BeforeAll
    public static void setUpTestingServer() {
        mysqlContainer = new MySQLContainer(DockerImageName.parse("mysql:8.0"))
                .withUsername("root")
                .withPassword("root")
                .withDatabaseName("dolphinscheduler")
                .withNetwork(Network.newNetwork())
                .withExposedPorts(3306)
                .waitingFor(Wait.forHealthcheck().withStartupTimeout(Duration.ofSeconds(300)));

        Startables.deepStart(Stream.of(mysqlContainer)).join();

        String jdbcUrl = "jdbc:mysql://localhost:" + mysqlContainer.getMappedPort(3306)
                + "/dolphinscheduler?useSSL=false&serverTimezone=UTC";
        System.clearProperty("spring.datasource.url");
        System.setProperty("spring.datasource.url", jdbcUrl);

        HikariConfig config = new HikariConfig();
        config.setJdbcUrl(jdbcUrl);
        config.setUsername("root");
        config.setPassword("root");

        try (HikariDataSource dataSource = new HikariDataSource(config)) {
            SqlScriptRunner sqlScriptRunner = new SqlScriptRunner(dataSource, "mysql_registry_init.sql");
            sqlScriptRunner.execute();
        }
    }

    @SneakyThrows
    @AfterAll
    public static void tearDownTestingServer() {
        mysqlContainer.close();
    }
}
