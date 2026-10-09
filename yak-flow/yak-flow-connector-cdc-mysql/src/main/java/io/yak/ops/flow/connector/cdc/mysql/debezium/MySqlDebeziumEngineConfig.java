package io.yak.ops.flow.connector.cdc.mysql.debezium;

import io.yak.ops.flow.connector.cdc.mysql.source.MySqlCdcSourceConfig;
import io.yak.ops.plugin.database.jdbc.JdbcEndpoint;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.Properties;
import java.util.regex.Pattern;

/**
 * 把 YakFlow MySQL CDC 配置收敛为 Debezium Engine Properties；该类型不暴露到 Connector 外。
 *
 * @author weifuwan
 * @since 2026-09-27
 */
final class MySqlDebeziumEngineConfig {

    private MySqlDebeziumEngineConfig() {}

    static Properties build(MySqlCdcSourceConfig config, JdbcEndpoint endpoint) throws Exception {
        Files.createDirectories(config.stateDirectory());
        Path offsets = config.stateDirectory().resolve("offsets.dat");
        Path schemaHistory = config.stateDirectory().resolve("schema-history.dat");

        Properties properties = new Properties();
        properties.setProperty("name", config.name());
        properties.setProperty("connector.class", "io.debezium.connector.mysql.MySqlConnector");
        properties.setProperty("offset.storage", "org.apache.kafka.connect.storage.FileOffsetBackingStore");
        properties.setProperty(
                "offset.storage.file.filename", offsets.toAbsolutePath().toString());
        properties.setProperty("offset.flush.interval.ms", "0");
        properties.setProperty("database.hostname", endpoint.host());
        properties.setProperty("database.port", String.valueOf(endpoint.port()));
        properties.setProperty("database.user", config.connection().username());
        properties.setProperty(
                "database.password",
                config.connection().password() == null
                        ? ""
                        : config.connection().password());
        config.connection().properties().forEach((key, value) -> properties.setProperty("driver." + key, value));
        properties.setProperty("database.server.id", String.valueOf(config.serverId()));
        properties.setProperty("topic.prefix", topicPrefix(config.name()));
        properties.setProperty(
                "table.include.list",
                Pattern.quote(config.databaseName()) + "\\."
                        + Pattern.quote(config.table().table()));
        properties.setProperty("snapshot.mode", "initial");
        properties.setProperty("include.schema.changes", "false");
        properties.setProperty("tombstones.on.delete", "false");
        properties.setProperty("time.precision.mode", "connect");
        properties.setProperty("decimal.handling.mode", "precise");
        properties.setProperty("heartbeat.interval.ms", "10000");
        properties.setProperty("schema.history.internal", "io.debezium.storage.file.history.FileSchemaHistory");
        properties.setProperty(
                "schema.history.internal.file.filename",
                schemaHistory.toAbsolutePath().toString());
        properties.setProperty("schema.history.internal.store.only.captured.tables.ddl", "true");
        return properties;
    }

    private static String topicPrefix(String name) {
        return name.replaceAll("[^A-Za-z0-9._-]", "_");
    }
}
