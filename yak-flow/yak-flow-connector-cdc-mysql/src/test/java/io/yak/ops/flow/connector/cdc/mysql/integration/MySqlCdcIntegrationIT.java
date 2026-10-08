package io.yak.ops.flow.connector.cdc.mysql.integration;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.fail;

import io.yak.ops.flow.api.row.YakColumn;
import io.yak.ops.flow.api.row.YakTypes;
import io.yak.ops.flow.api.row.YakTableSchema;
import io.yak.ops.flow.connector.cdc.mysql.source.MySqlCdcSource;
import io.yak.ops.flow.connector.cdc.mysql.source.MySqlCdcSourceConfig;
import io.yak.ops.flow.connector.jdbc.JdbcSinkConfig;
import io.yak.ops.flow.connector.jdbc.JdbcWriteMode;
import io.yak.ops.flow.connector.jdbc.sink.JdbcSink;
import io.yak.ops.plugin.database.jdbc.JdbcConnectionProperties;
import io.yak.ops.plugin.database.jdbc.JdbcConnectionProvider;
import io.yak.ops.plugin.database.jdbc.SshTunnelConfig;
import io.yak.ops.plugin.datasource.api.catalog.DataSourceTablePath;
import io.yak.ops.plugin.datasource.api.plugin.DataSourceConnection;
import java.nio.file.Files;
import java.nio.file.Path;
import java.sql.DriverManager;
import java.time.Duration;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Properties;
import java.util.concurrent.TimeUnit;
import java.util.stream.Stream;
import org.junit.jupiter.api.AfterAll;
import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;
import org.testcontainers.containers.GenericContainer;
import org.testcontainers.containers.MySQLContainer;
import org.testcontainers.containers.PostgreSQLContainer;
import org.testcontainers.containers.wait.strategy.Wait;
import org.testcontainers.lifecycle.Startables;
import org.testcontainers.utility.DockerImageName;

/**
 * 真实 MySQL Binlog CDC 跨库验收，覆盖 MySQL -> MySQL / PostgreSQL / Oracle 的 snapshot、增删改、
 * checkpoint、取消、持久化 offset 和重启续传。
 *
 * @author weifuwan
 * @since 2026-09-28
 */
class MySqlCdcIntegrationIT {

    private static final String MYSQL_DATABASE = "yakflow";
    private static final String ROOT_PASSWORD = "yak-root";
    private static final String ORACLE_PASSWORD = "yakoracle";
    private static final MySQLContainer<?> MYSQL = new MySQLContainer<>("mysql:8.4")
            .withDatabaseName(MYSQL_DATABASE)
            .withUsername("root")
            .withPassword(ROOT_PASSWORD)
            .withEnv("MYSQL_ROOT_HOST", "%")
            .withCommand(
                    "--server-id=223344",
                    "--log-bin=mysql-bin",
                    "--binlog-format=ROW",
                    "--binlog-row-image=FULL");
    private static final PostgreSQLContainer<?> POSTGRES = new PostgreSQLContainer<>("postgres:16-alpine")
            .withDatabaseName("yakflow")
            .withUsername("yak")
            .withPassword("yakpass");
    private static final GenericContainer<?> ORACLE = new GenericContainer<>(
                    DockerImageName.parse("gvenzl/oracle-free:23-slim-faststart"))
            .withEnv("ORACLE_PASSWORD", ORACLE_PASSWORD)
            .withExposedPorts(1521)
            .waitingFor(Wait.forLogMessage(".*DATABASE IS READY TO USE!.*\\n", 1))
            .withStartupTimeout(Duration.ofMinutes(6));
    private static final YakTableSchema SCHEMA = new YakTableSchema(
            List.of(
                    new YakColumn("id", YakTypes.BIGINT, false, null),
                    new YakColumn("name", YakTypes.STRING, true, 100)),
            List.of("id"));

    private static final YakTableSchema MAPPED_SOURCE_SCHEMA = new YakTableSchema(
            List.of(
                    new YakColumn("name", YakTypes.STRING, true, 100),
                    new YakColumn("id", YakTypes.BIGINT, false, null)),
            List.of("id"));

    private static final YakTableSchema MAPPED_TARGET_SCHEMA = new YakTableSchema(
            List.of(
                    new YakColumn("display_name", YakTypes.STRING, true, 100),
                    new YakColumn("user_id", YakTypes.BIGINT, false, null)),
            List.of("user_id"));
    private static final JdbcConnectionProvider DIRECT_CONNECTION = (connection, timeoutSeconds) -> {
        Class.forName(connection.driverClassName());
        Properties properties = new Properties();
        properties.putAll(connection.properties());
        properties.setProperty("user", connection.username());
        if (connection.password() != null) {
            properties.setProperty("password", connection.password());
        }
        return DriverManager.getConnection(connection.jdbcUrl(), properties);
    };

    @TempDir
    Path stateDirectory;

    @BeforeAll
    static void startDatabases() {
        Startables.deepStart(Stream.of(MYSQL, POSTGRES, ORACLE)).join();
    }

    @AfterAll
    static void stopDatabases() {
        ORACLE.stop();
        POSTGRES.stop();
        MYSQL.stop();
    }

    @Test
    void shouldSyncSnapshotBinlogAndResumeOffsetToMysql() throws Exception {
        executeAcceptance(mysqlTarget(), 54021L);
    }

    @Test
    void shouldSyncSnapshotBinlogAndResumeOffsetToPostgresql() throws Exception {
        executeAcceptance(postgresTarget(), 54022L);
    }

    @Test
    void shouldSyncSnapshotBinlogAndResumeOffsetToOracle() throws Exception {
        executeAcceptance(oracleTarget(), 54023L);
    }

    @Test
    void shouldSyncMappedPrimaryKeyAndChangelogToMysql() throws Exception {
        executeMappedAcceptance(mysqlMappedTarget(), 54121L);
    }

    @Test
    void shouldSyncMappedPrimaryKeyAndChangelogToPostgresql() throws Exception {
        executeMappedAcceptance(postgresMappedTarget(), 54122L);
    }

    @Test
    void shouldSyncMappedPrimaryKeyAndChangelogToOracle() throws Exception {
        executeMappedAcceptance(oracleMappedTarget(), 54123L);
    }

    private void executeMappedAcceptance(Target target, long serverId) throws Exception {
        resetMappedSource();
        resetTarget(target);

        Path targetStateDirectory = stateDirectory.resolve(target.name());
        MySqlCdcSourceConfig sourceConfig = MySqlCdcSourceConfig.defaults(
                sourceConnection(),
                new DataSourceTablePath(MYSQL_DATABASE, null, "source_user_mapping"),
                MAPPED_SOURCE_SCHEMA,
                targetStateDirectory,
                "source-user-mapping-cdc-" + target.name(),
                serverId);

        LocalExecution<?> execution = startMappedExecution(sourceConfig, target);
        try {
            awaitTarget(target, Map.of(1L, "alpha", 2L, "beta"));

            executeSource(
                    "INSERT INTO source_user_mapping(id, name, ignored_note) VALUES (3, 'gamma', 'ignored-3')");
            executeSource(
                    "UPDATE source_user_mapping SET name = 'alpha-v2', ignored_note = 'ignored-v2' WHERE id = 1");
            executeSource("DELETE FROM source_user_mapping WHERE id = 2");

            awaitTarget(target, Map.of(1L, "alpha-v2", 3L, "gamma"));
            execution.checkpoint().get(20, TimeUnit.SECONDS);
            awaitOffsetFile(targetStateDirectory);
        } finally {
            stopExecution(execution);
        }
    }

    private void executeAcceptance(Target target, long serverId) throws Exception {
        resetSource();
        resetTarget(target);

        Path targetStateDirectory = stateDirectory.resolve(target.name());
        JdbcConnectionProperties sourceConnection = sourceConnection();
        MySqlCdcSourceConfig sourceConfig = MySqlCdcSourceConfig.defaults(
                sourceConnection,
                new DataSourceTablePath(MYSQL_DATABASE, null, "source_user"),
                SCHEMA,
                targetStateDirectory,
                "source-user-cdc-" + target.name(),
                serverId);

        LocalExecution<?> firstExecution = startExecution(sourceConfig, target);
        try {
            awaitTarget(target, Map.of(1L, "alpha", 2L, "beta"));

            executeSource("INSERT INTO source_user(id, name) VALUES (3, 'gamma')");
            executeSource("UPDATE source_user SET name = 'alpha-v2' WHERE id = 1");
            executeSource("DELETE FROM source_user WHERE id = 2");

            awaitTarget(target, Map.of(1L, "alpha-v2", 3L, "gamma"));
            firstExecution.checkpoint().get(20, TimeUnit.SECONDS);
            awaitOffsetFile(targetStateDirectory);
        } finally {
            stopExecution(firstExecution);
        }

        executeSource("INSERT INTO source_user(id, name) VALUES (4, 'delta')");

        LocalExecution<?> secondExecution = startExecution(sourceConfig, target);
        try {
            awaitTarget(target, Map.of(1L, "alpha-v2", 3L, "gamma", 4L, "delta"));
            assertEquals(new ExecutionMetrics(1, 1), secondExecution.metrics());
            secondExecution.checkpoint().get(20, TimeUnit.SECONDS);
            awaitOffsetFile(targetStateDirectory);
        } finally {
            stopExecution(secondExecution);
        }
    }

    private LocalExecution<?> startMappedExecution(MySqlCdcSourceConfig sourceConfig, Target target) {
        JdbcSink sink = new JdbcSink(
                new JdbcSinkConfig(
                        target.connection(),
                        target.table(),
                        100,
                        15,
                        JdbcWriteMode.CHANGELOG),
                DIRECT_CONNECTION);
        return new LocalExecutionEngine(Duration.ofMillis(250))
                .start(new MySqlCdcSource(sourceConfig), sink, MAPPED_TARGET_SCHEMA);
    }

    private LocalExecution<?> startExecution(MySqlCdcSourceConfig sourceConfig, Target target) {
        JdbcSink sink = new JdbcSink(
                new JdbcSinkConfig(
                        target.connection(),
                        target.table(),
                        100,
                        15,
                        JdbcWriteMode.CHANGELOG),
                DIRECT_CONNECTION);
        return new LocalExecutionEngine(Duration.ofMillis(250))
                .start(new MySqlCdcSource(sourceConfig), sink, SCHEMA);
    }

    private void stopExecution(LocalExecution<?> execution) throws Exception {
        if (execution.status() == ExecutionStatus.RUNNING) {
            execution.cancel();
        }
        assertEquals(ExecutionStatus.CANCELED, execution.await(Duration.ofSeconds(20)));
    }

    private void resetSource() throws Exception {
        try (var connection = DIRECT_CONNECTION.open(sourceConnection(), 15);
                var statement = connection.createStatement()) {
            statement.execute("DROP TABLE IF EXISTS source_user");
            statement.execute("CREATE TABLE source_user (id BIGINT PRIMARY KEY, name VARCHAR(100))");
            statement.execute("INSERT INTO source_user(id, name) VALUES (1, 'alpha'), (2, 'beta')");
        }
    }

    private void resetMappedSource() throws Exception {
        try (var connection = DIRECT_CONNECTION.open(sourceConnection(), 15);
                var statement = connection.createStatement()) {
            statement.execute("DROP TABLE IF EXISTS source_user_mapping");
            statement.execute(
                    "CREATE TABLE source_user_mapping (id BIGINT PRIMARY KEY, name VARCHAR(100), ignored_note VARCHAR(100))");
            statement.execute(
                    "INSERT INTO source_user_mapping(id, name, ignored_note) VALUES "
                            + "(1, 'alpha', 'ignored-1'), (2, 'beta', 'ignored-2')");
        }
    }

    private void resetTarget(Target target) throws Exception {
        try (var connection = DIRECT_CONNECTION.open(target.connection(), 15);
                var statement = connection.createStatement()) {
            try {
                statement.execute(target.dropSql());
            } catch (Exception exception) {
                if (!target.ignoreDropFailure()) throw exception;
            }
            statement.execute(target.createSql());
        }
    }

    private void executeSource(String sql) throws Exception {
        try (var connection = DIRECT_CONNECTION.open(sourceConnection(), 15);
                var statement = connection.createStatement()) {
            statement.executeUpdate(sql);
        }
    }

    private void awaitTarget(Target target, Map<Long, String> expected) throws Exception {
        long deadline = System.nanoTime() + Duration.ofSeconds(45).toNanos();
        Map<Long, String> actual = Map.of();
        while (System.nanoTime() < deadline) {
            actual = readTarget(target);
            if (actual.equals(expected)) {
                return;
            }
            Thread.sleep(100);
        }
        fail(target.name() + " target rows did not converge, expected=" + expected + ", actual=" + actual);
    }

    private Map<Long, String> readTarget(Target target) throws Exception {
        Map<Long, String> values = new LinkedHashMap<>();
        try (var connection = DIRECT_CONNECTION.open(target.connection(), 15);
                var statement = connection.createStatement();
                var resultSet = statement.executeQuery(target.selectSql())) {
            while (resultSet.next()) {
                values.put(resultSet.getLong(1), resultSet.getString(2));
            }
        }
        return values;
    }

    private void awaitOffsetFile(Path directory) throws Exception {
        Path offsetFile = directory.resolve("offsets.dat");
        long deadline = System.nanoTime() + Duration.ofSeconds(15).toNanos();
        while (System.nanoTime() < deadline) {
            if (Files.isRegularFile(offsetFile) && Files.size(offsetFile) > 0) {
                return;
            }
            Thread.sleep(100);
        }
        fail("Debezium offset file was not persisted after checkpoint: " + offsetFile);
    }

    private JdbcConnectionProperties sourceConnection() {
        return new JdbcConnectionProperties(
                "MYSQL",
                MYSQL.getHost(),
                MYSQL.getMappedPort(MySQLContainer.MYSQL_PORT),
                MYSQL.getJdbcUrl(),
                "com.mysql.cj.jdbc.Driver",
                "MYSQL_8",
                MYSQL.getUsername(),
                MYSQL.getPassword(),
                MYSQL_DATABASE,
                null,
                Map.of("useSSL", "false", "allowPublicKeyRetrieval", "true"),
                SshTunnelConfig.disabled(),
                "{}");
    }

    private Target mysqlTarget() {
        return new Target(
                "mysql",
                sourceConnection(),
                new DataSourceTablePath(MYSQL_DATABASE, null, "target_mysql"),
                "DROP TABLE IF EXISTS target_mysql",
                "CREATE TABLE target_mysql (id BIGINT PRIMARY KEY, name VARCHAR(100))",
                "SELECT id, name FROM target_mysql ORDER BY id",
                false);
    }

    private Target mysqlMappedTarget() {
        return new Target(
                "mapped-mysql",
                sourceConnection(),
                new DataSourceTablePath(MYSQL_DATABASE, null, "target_mapping_mysql"),
                "DROP TABLE IF EXISTS target_mapping_mysql",
                "CREATE TABLE target_mapping_mysql (user_id BIGINT PRIMARY KEY, display_name VARCHAR(100))",
                "SELECT user_id, display_name FROM target_mapping_mysql ORDER BY user_id",
                false);
    }

    private Target postgresTarget() {
        return new Target(
                "postgresql",
                new TestConnection(
                        "POSTGRE_SQL",
                        POSTGRES.getJdbcUrl(),
                        "org.postgresql.Driver",
                        POSTGRES.getUsername(),
                        POSTGRES.getPassword(),
                        "yakflow"),
                new DataSourceTablePath("yakflow", "public", "target_pg"),
                "DROP TABLE IF EXISTS target_pg",
                "CREATE TABLE target_pg (id BIGINT PRIMARY KEY, name VARCHAR(100))",
                "SELECT id, name FROM target_pg ORDER BY id",
                false);
    }

    private Target postgresMappedTarget() {
        return new Target(
                "mapped-postgresql",
                new TestConnection(
                        "POSTGRE_SQL",
                        POSTGRES.getJdbcUrl(),
                        "org.postgresql.Driver",
                        POSTGRES.getUsername(),
                        POSTGRES.getPassword(),
                        "yakflow"),
                new DataSourceTablePath("yakflow", "public", "target_mapping_pg"),
                "DROP TABLE IF EXISTS target_mapping_pg",
                "CREATE TABLE target_mapping_pg (user_id BIGINT PRIMARY KEY, display_name VARCHAR(100))",
                "SELECT user_id, display_name FROM target_mapping_pg ORDER BY user_id",
                false);
    }

    private Target oracleTarget() {
        String jdbcUrl = "jdbc:oracle:thin:@//" + ORACLE.getHost() + ":" + ORACLE.getMappedPort(1521) + "/FREEPDB1";
        return new Target(
                "oracle",
                new TestConnection("ORACLE", jdbcUrl, "oracle.jdbc.OracleDriver", "system", ORACLE_PASSWORD, null),
                new DataSourceTablePath(null, null, "target_oracle"),
                "DROP TABLE \"target_oracle\" PURGE",
                "CREATE TABLE \"target_oracle\" (\"id\" NUMBER(19) PRIMARY KEY, \"name\" VARCHAR2(100))",
                "SELECT \"id\", \"name\" FROM \"target_oracle\" ORDER BY \"id\"",
                true);
    }

    private Target oracleMappedTarget() {
        String jdbcUrl = "jdbc:oracle:thin:@//" + ORACLE.getHost() + ":" + ORACLE.getMappedPort(1521) + "/FREEPDB1";
        return new Target(
                "mapped-oracle",
                new TestConnection("ORACLE", jdbcUrl, "oracle.jdbc.OracleDriver", "system", ORACLE_PASSWORD, null),
                new DataSourceTablePath(null, null, "target_mapping_oracle"),
                "DROP TABLE \"target_mapping_oracle\" PURGE",
                "CREATE TABLE \"target_mapping_oracle\" "
                        + "(\"user_id\" NUMBER(19) PRIMARY KEY, \"display_name\" VARCHAR2(100))",
                "SELECT \"user_id\", \"display_name\" FROM \"target_mapping_oracle\" ORDER BY \"user_id\"",
                true);
    }

    private record Target(
            String name,
            DataSourceConnection connection,
            DataSourceTablePath table,
            String dropSql,
            String createSql,
            String selectSql,
            boolean ignoreDropFailure) {}

    private record TestConnection(
            String type,
            String jdbcUrl,
            String driverClassName,
            String username,
            String password,
            String database)
            implements DataSourceConnection {

        @Override
        public String schema() {
            return null;
        }

        @Override
        public Map<String, String> properties() {
            return Map.of();
        }

        @Override
        public String normalizedJson() {
            return "{}";
        }
    }
}
