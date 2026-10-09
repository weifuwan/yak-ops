package io.yak.ops.flow.connector.jdbc;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertThrows;

import io.yak.ops.flow.api.row.RowKind;
import io.yak.ops.flow.api.row.YakColumn;
import io.yak.ops.flow.api.row.YakTypes;
import io.yak.ops.flow.api.row.YakRow;
import io.yak.ops.flow.api.row.YakTableSchema;
import io.yak.ops.flow.connector.jdbc.sink.JdbcSink;
import io.yak.ops.plugin.database.jdbc.JdbcConnectionProvider;
import io.yak.ops.plugin.datasource.api.catalog.DataSourceTablePath;
import java.sql.DriverManager;
import java.sql.ResultSet;
import java.util.List;
import java.util.Properties;
import org.junit.jupiter.api.Test;

class JdbcChangelogSinkTest {

    private static final String DRIVER = "org.h2.Driver";
    private static final YakTableSchema SCHEMA = new YakTableSchema(
            List.of(
                    new YakColumn("id", YakTypes.BIGINT, false, null),
                    new YakColumn("name", YakTypes.STRING, true, 100)),
            List.of("id"));
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

    @Test
    void shouldRejectOverwriteWithChangelog() {
        TestDataSourceConnection connection = new TestDataSourceConnection(
                "MYSQL", "jdbc:h2:mem:cdc_invalid;MODE=MySQL;DB_CLOSE_DELAY=-1", DRIVER, "sa", "");

        assertThrows(
                IllegalArgumentException.class,
                () -> new JdbcSinkConfig(
                        connection,
                        new DataSourceTablePath(null, null, "target_table"),
                        10,
                        5,
                        JdbcSaveMode.OVERWRITE,
                        JdbcWriteMode.CHANGELOG));
    }

    @Test
    void shouldApplyChangelogToMysql() throws Exception {
        runChangelog("MYSQL", "MySQL");
    }

    @Test
    void shouldApplyChangelogToPostgresql() throws Exception {
        runChangelog("POSTGRE_SQL", "PostgreSQL");
    }

    @Test
    void shouldApplyChangelogToOracle() throws Exception {
        runChangelog("ORACLE", "Oracle");
    }

    private void runChangelog(String type, String h2Mode) throws Exception {
        String suffix = type.toLowerCase();
        TestDataSourceConnection connection = new TestDataSourceConnection(
                type,
                "jdbc:h2:mem:cdc_" + suffix + ";MODE=" + h2Mode + ";DATABASE_TO_LOWER=TRUE;DB_CLOSE_DELAY=-1",
                DRIVER,
                "sa",
                "");
        DataSourceTablePath table = new DataSourceTablePath(null, null, "target_table");
        createTable(connection);

        JdbcSink sink = new JdbcSink(
                new JdbcSinkConfig(connection, table, 10, 5, JdbcWriteMode.CHANGELOG),
                DIRECT_CONNECTION);
        try (var writer = sink.createWriter(SCHEMA)) {
            writer.open();
            writer.write(List.of(
                    row(RowKind.INSERT, 1L, "before"),
                    row(RowKind.UPDATE_BEFORE, 1L, "before"),
                    row(RowKind.UPDATE_AFTER, 1L, "after"),
                    row(RowKind.INSERT, 2L, "temporary"),
                    row(RowKind.DELETE, 2L, "temporary")));
            writer.flush();
        }

        try (var opened = DIRECT_CONNECTION.open(connection, 5);
                var statement = opened.createStatement();
                ResultSet resultSet = statement.executeQuery("SELECT id, name FROM target_table ORDER BY id")) {
            assertEquals(true, resultSet.next());
            assertEquals(1L, resultSet.getLong(1));
            assertEquals("after", resultSet.getString(2));
            assertFalse(resultSet.next());
        }
    }

    private YakRow row(RowKind kind, long id, String name) {
        return new YakRow(kind, List.of(id, name));
    }

    private void createTable(TestDataSourceConnection connection) throws Exception {
        try (var opened = DIRECT_CONNECTION.open(connection, 5);
                var statement = opened.createStatement()) {
            statement.execute("CREATE TABLE target_table (id BIGINT PRIMARY KEY, name VARCHAR(100))");
        }
    }
}
