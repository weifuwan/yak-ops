package io.yak.ops.flow.connector.cdc.mysql.debezium;

import static org.junit.jupiter.api.Assertions.assertEquals;

import io.yak.ops.flow.api.row.YakColumn;
import io.yak.ops.flow.api.row.YakTypes;
import io.yak.ops.flow.api.row.YakTableSchema;
import io.yak.ops.flow.connector.cdc.mysql.source.MySqlCdcSourceConfig;
import io.yak.ops.plugin.database.jdbc.JdbcConnectionProperties;
import io.yak.ops.plugin.database.jdbc.JdbcEndpoint;
import io.yak.ops.plugin.database.jdbc.SshTunnelConfig;
import io.yak.ops.plugin.datasource.api.catalog.DataSourceTablePath;
import java.nio.file.Path;
import java.util.List;
import java.util.Map;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

class MySqlDebeziumEngineConfigTest {

    @TempDir
    Path tempDirectory;

    @Test
    void shouldBuildInitialSnapshotAndFileBackedState() throws Exception {
        JdbcConnectionProperties connection = new JdbcConnectionProperties(
                "MYSQL",
                "127.0.0.1",
                3306,
                "jdbc:mysql://127.0.0.1:3306/app",
                "com.mysql.cj.jdbc.Driver",
                "MYSQL_8",
                "root",
                "secret",
                "app",
                null,
                Map.of("serverTimezone", "Asia/Shanghai", "useSSL", "false", "connectTimeout", "30000"),
                SshTunnelConfig.disabled(),
                "{}");
        YakTableSchema schema = new YakTableSchema(
                List.of(new YakColumn("id", YakTypes.BIGINT, false, null)),
                List.of("id"));
        MySqlCdcSourceConfig config = MySqlCdcSourceConfig.defaults(
                connection,
                new DataSourceTablePath("app", null, "user"),
                schema,
                tempDirectory,
                "user-cdc",
                54001L);

        var properties = MySqlDebeziumEngineConfig.build(config, JdbcEndpoint.direct("127.0.0.1", 3306));

        assertEquals("initial", properties.getProperty("snapshot.mode"));
        assertEquals("127.0.0.1", properties.getProperty("database.hostname"));
        assertEquals("3306", properties.getProperty("database.port"));
        assertEquals("54001", properties.getProperty("database.server.id"));
        assertEquals("Asia/Shanghai", properties.getProperty("driver.serverTimezone"));
        assertEquals("false", properties.getProperty("driver.useSSL"));
        assertEquals("30000", properties.getProperty("driver.connectTimeout"));
        assertEquals("0", properties.getProperty("offset.flush.interval.ms"));
        assertEquals(
                "org.apache.kafka.connect.storage.FileOffsetBackingStore",
                properties.getProperty("offset.storage"));
        assertEquals(
                "io.debezium.storage.file.history.FileSchemaHistory",
                properties.getProperty("schema.history.internal"));
    }
}
