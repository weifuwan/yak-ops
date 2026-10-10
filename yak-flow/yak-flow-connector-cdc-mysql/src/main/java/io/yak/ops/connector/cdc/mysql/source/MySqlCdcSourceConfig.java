package io.yak.ops.connector.cdc.mysql.source;

import io.yak.ops.connector.cdc.mysql.source.debezium.CheckpointOffsetBackingStore;
import io.yak.ops.core.data.TableId;
import io.yak.ops.core.types.TableSchema;
import java.nio.file.Path;
import java.util.Collections;
import java.util.LinkedHashMap;
import java.util.Map;
import java.util.Objects;
import java.util.Properties;
import java.util.Set;
import java.util.TreeSet;
import java.util.regex.Pattern;
import java.util.stream.Collectors;

/**
 * Immutable MySQL Binlog connection and schema contract independent of execution attempts.
 *
 * <p>Production credentials never appear in toString(), Split or Enumerator checkpoint state.
 * Exactly one Binlog reader is supported in the initial stream-only connector.
 */
public final class MySqlCdcSourceConfig {

    private final String hostname;
    private final int port;
    private final String username;
    private final String password;
    private final int serverId;
    private final String topicPrefix;
    private final Map<TableId, TableSchema> schemas;
    private final int queueCapacity;

    public MySqlCdcSourceConfig(
            String hostname,
            int port,
            String username,
            String password,
            int serverId,
            String topicPrefix,
            Map<TableId, TableSchema> schemas,
            int queueCapacity) {
        this.hostname = requireText(hostname, "hostname");
        this.username = requireText(username, "username");
        this.password = Objects.requireNonNull(password, "password");
        this.topicPrefix = requireText(topicPrefix, "topicPrefix");
        if (!topicPrefix.matches("[A-Za-z0-9_.-]+")) {
            throw new IllegalArgumentException("MySQL CDC topic prefix contains unsupported characters");
        }
        if (port < 1 || port > 65535 || serverId < 1 || queueCapacity < 1 || queueCapacity > 10_000) {
            throw new IllegalArgumentException("Invalid MySQL CDC port, replication server ID or queue capacity");
        }
        this.port = port;
        this.serverId = serverId;
        this.queueCapacity = queueCapacity;
        Map<TableId, TableSchema> resolved = new LinkedHashMap<>();
        Objects.requireNonNull(schemas, "schemas").forEach((table, schema) -> {
            Objects.requireNonNull(table, "table");
            Objects.requireNonNull(schema, "schema");
            if (table.catalog() == null
                    || table.schema() != null
                    || schema.primaryKeys().isEmpty()) {
                throw new IllegalArgumentException("MySQL CDC requires database-qualified tables with primary keys");
            }
            schema.columns().forEach(column -> {
                if (!column.dataType().isResolved()) {
                    throw new IllegalArgumentException("MySQL CDC requires resolved logical types");
                }
            });
            if (resolved.putIfAbsent(table, schema) != null) {
                throw new IllegalArgumentException("Duplicate MySQL CDC table");
            }
        });
        if (resolved.isEmpty()) {
            throw new IllegalArgumentException("MySQL CDC requires at least one table");
        }
        this.schemas = Collections.unmodifiableMap(resolved);
    }

    private static String requireText(String value, String label) {
        if (value == null || value.isBlank()) {
            throw new IllegalArgumentException("MySQL CDC " + label + " must not be blank");
        }
        return value;
    }

    public Map<TableId, TableSchema> schemas() {
        return schemas;
    }

    public int queueCapacity() {
        return queueCapacity;
    }

    public String hostname() {
        return hostname;
    }

    public int port() {
        return port;
    }

    public String username() {
        return username;
    }

    public int serverId() {
        return serverId;
    }

    public String topicPrefix() {
        return topicPrefix;
    }

    /**
     * Opens an independent, caller-owned JDBC connection for a hybrid Snapshot scan.
     *
     * <p>Never stores the connection in a Source definition or Checkpoint. This path uses
     * the MySQL JDBC driver provided by the Connector module.
     */
    public java.sql.Connection openSnapshotConnection() throws java.sql.SQLException {
        String url = "jdbc:mysql://" + hostname + ":" + port + "/";
        return java.sql.DriverManager.getConnection(url, username, password);
    }

    /**
     * Constructs a Debezium engine that snapshots schemas only, then streams Binlog events.
     *
     * <p>A bootstrap run intentionally starts at the current offset. Older arbitrary offsets
     * require schema-history reconstruction and are not allowed without a completed checkpoint.
     */
    public Properties debeziumProperties(String session, Path schemaHistoryFile) {
        Properties properties = new Properties();
        properties.setProperty("name", "yakflow-cdc-" + topicPrefix);
        properties.setProperty("connector.class", "io.debezium.connector.mysql.MySqlConnector");
        properties.setProperty("database.hostname", hostname);
        properties.setProperty("database.port", Integer.toString(port));
        properties.setProperty("database.user", username);
        properties.setProperty("database.password", password);
        properties.setProperty("database.server.id", Integer.toString(serverId));
        properties.setProperty("topic.prefix", topicPrefix);
        Set<String> databaseNames = new TreeSet<>();
        schemas.keySet().forEach(table -> databaseNames.add(table.catalog()));
        properties.setProperty(
                "database.include.list",
                databaseNames.stream().map(Pattern::quote).collect(Collectors.joining(",")));
        properties.setProperty(
                "table.include.list",
                schemas.keySet().stream()
                        .map(table -> Pattern.quote(table.catalog()) + "\\." + Pattern.quote(table.table()))
                        .collect(Collectors.joining(",")));
        properties.setProperty("snapshot.mode", "no_data");
        properties.setProperty("include.schema.changes", "false");
        properties.setProperty("tombstones.on.delete", "false");
        properties.setProperty("heartbeat.interval.ms", "1000");
        properties.setProperty("decimal.handling.mode", "precise");
        properties.setProperty("binary.handling.mode", "bytes");
        properties.setProperty("time.precision.mode", "adaptive_time_microseconds");
        properties.setProperty("max.batch.size", "128");
        properties.setProperty("max.queue.size", "512");
        properties.setProperty("offset.storage", CheckpointOffsetBackingStore.class.getName());
        properties.setProperty("yakflow.cdc.session.id", session);
        properties.setProperty("offset.flush.interval.ms", "0");
        properties.setProperty("schema.history.internal", "io.debezium.storage.file.history.FileSchemaHistory");
        properties.setProperty(
                "schema.history.internal.file.filename",
                schemaHistoryFile.toAbsolutePath().toString());
        return properties;
    }

    @Override
    public String toString() {
        return "MySqlCdcSourceConfig{credentials=redacted, tables=" + schemas.size() + "}";
    }
}
