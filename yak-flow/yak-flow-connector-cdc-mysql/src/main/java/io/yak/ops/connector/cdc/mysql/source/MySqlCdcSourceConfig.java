package io.yak.ops.connector.cdc.mysql.source;

import io.yak.ops.connector.cdc.mysql.source.debezium.CheckpointOffsetBackingStore;
import io.yak.ops.core.api.connector.source.SourceReaderContext;
import io.yak.ops.core.configuration.SchemaEvolutionOptions;
import io.yak.ops.core.data.TableId;
import io.yak.ops.core.schema.Schema;
import io.yak.ops.core.types.TableSchema;
import java.nio.file.Path;
import java.sql.Connection;
import java.sql.DriverManager;
import java.sql.SQLException;
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
    private final Map<String, String> connectionProperties;
    private final Map<TableId, Schema> physicalSchemas;
    private final boolean schemaEvolutionEnabled;

    public MySqlCdcSourceConfig(
            String hostname,
            int port,
            String username,
            String password,
            int serverId,
            String topicPrefix,
            Map<TableId, TableSchema> schemas,
            int queueCapacity) {
        this(hostname, port, username, password, serverId, topicPrefix, schemas, queueCapacity, Map.of());
    }

    public MySqlCdcSourceConfig(
            String hostname,
            int port,
            String username,
            String password,
            int serverId,
            String topicPrefix,
            Map<TableId, TableSchema> schemas,
            int queueCapacity,
            Map<String, String> connectionProperties) {
        this(
                hostname,
                port,
                username,
                password,
                serverId,
                topicPrefix,
                schemas,
                queueCapacity,
                connectionProperties,
                null,
                false);
    }

    private MySqlCdcSourceConfig(
            String hostname,
            int port,
            String username,
            String password,
            int serverId,
            String topicPrefix,
            Map<TableId, TableSchema> schemas,
            int queueCapacity,
            Map<String, String> connectionProperties,
            Map<TableId, Schema> physicalSchemas,
            boolean schemaEvolutionEnabled) {
        this.schemaEvolutionEnabled = schemaEvolutionEnabled;
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
        this.connectionProperties = normalizeConnectionProperties(connectionProperties);
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
        Map<TableId, Schema> shapes = new LinkedHashMap<>();
        resolved.forEach((table, tableSchema) -> shapes.put(
                table,
                physicalSchemas == null
                        ? Schema.from(tableSchema, null, Map.of(), Map.of())
                        : Objects.requireNonNull(physicalSchemas.get(table), "Physical source schema is missing")));
        this.physicalSchemas = Collections.unmodifiableMap(shapes);
    }

    private static String requireText(String value, String label) {
        if (value == null || value.isBlank()) {
            throw new IllegalArgumentException("MySQL CDC " + label + " must not be blank");
        }
        return value;
    }

    /**
     * Use a numeric UTC offset for new JDBC sessions when no time zone was explicitly configured.
     * This avoids ambiguous OS names and does not require MySQL named time zone tables.
     */
    private static Map<String, String> normalizeConnectionProperties(Map<String, String> supplied) {
        Map<String, String> values = new LinkedHashMap<>();
        Objects.requireNonNull(supplied, "connectionProperties").forEach((key, value) -> {
            if (key == null || !key.matches("[A-Za-z][A-Za-z0-9_.-]*") || value == null) {
                throw new IllegalArgumentException("Invalid MySQL JDBC connection property");
            }
            if (Set.of(
                            "user",
                            "username",
                            "password",
                            "host",
                            "hostname",
                            "port",
                            "database",
                            "database.hostname",
                            "database.user",
                            "database.password",
                            "database.port")
                    .contains(key.toLowerCase(java.util.Locale.ROOT))) {
                throw new IllegalArgumentException(
                        "MySQL JDBC connection property must not override connection identity: " + key);
            }
            values.put(key, value);
        });
        String zone = getIgnoreCase(values, "connectionTimeZone");
        if (zone == null) {
            String forced = getIgnoreCase(values, "forceConnectionTimeZoneToSession");
            if (forced != null && !Boolean.parseBoolean(forced)) {
                throw new IllegalArgumentException(
                        "forceConnectionTimeZoneToSession=false requires an explicit connectionTimeZone");
            }
            values.put("connectionTimeZone", "+00:00");
            if (forced == null) {
                values.put("forceConnectionTimeZoneToSession", "true");
            }
        }
        return Collections.unmodifiableMap(values);
    }

    private static String getIgnoreCase(Map<String, String> values, String key) {
        for (var entry : values.entrySet()) {
            if (entry.getKey().equalsIgnoreCase(key)) return entry.getValue();
        }
        return null;
    }

    /** Effective driver configuration, including the connection-local UTC compatibility default. */
    public Map<String, String> connectionProperties() {
        return connectionProperties;
    }

    public Map<TableId, TableSchema> schemas() {
        return schemas;
    }

    public Map<TableId, Schema> physicalSchemas() {
        return physicalSchemas;
    }

    public boolean schemaEvolutionEnabled() {
        return schemaEvolutionEnabled;
    }

    /**
     * A checkpoint restores the same source schema and Debezium offset before the Reader starts.
     * A disabled evolution job retains its original schemas unchanged.
     */
    public MySqlCdcSourceConfig withRuntimeState(SourceReaderContext context) {
        Objects.requireNonNull(context, "context");
        boolean enabled = context.getConfiguration().get(SchemaEvolutionOptions.ENABLED);
        Map<TableId, Schema> models = new LinkedHashMap<>(physicalSchemas);
        Map<TableId, TableSchema> shape = new LinkedHashMap<>(schemas);
        for (TableId table : schemas.keySet()) {
            var restored = context.restoredSourceSchema(table);
            if (restored.isPresent()) {
                if (!enabled) {
                    throw new IllegalStateException("Cannot restore a CDC schema version with evolution disabled");
                }
                models.put(table, restored.get());
                shape.put(table, restored.get().toTableSchema());
            }
        }
        return new MySqlCdcSourceConfig(
                hostname,
                port,
                username,
                password,
                serverId,
                topicPrefix,
                shape,
                queueCapacity,
                connectionProperties,
                models,
                enabled);
    }

    /** Disables DDL adoption in snapshot-backfill readers, where schema changes cannot be replayed safely. */
    public MySqlCdcSourceConfig withoutSchemaEvolution() {
        return new MySqlCdcSourceConfig(
                hostname,
                port,
                username,
                password,
                serverId,
                topicPrefix,
                schemas,
                queueCapacity,
                connectionProperties,
                physicalSchemas,
                false);
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

    /** Returns the reserved replication ID for one of at most sixteen snapshot readers. */
    public int snapshotServerId(int reader) {
        if (reader < 0 || reader >= 16 || serverId > Integer.MAX_VALUE - 16) {
            throw new IllegalArgumentException("Hybrid source requires a reserved range of seventeen replication IDs");
        }
        return serverId + reader + 1;
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
    public Connection openSnapshotConnection() throws SQLException {
        String url = "jdbc:mysql://" + hostname + ":" + port + "/";
        Properties properties = new Properties();
        properties.putAll(connectionProperties);
        properties.putIfAbsent("useCursorFetch", "true");
        properties.putIfAbsent("useServerPrepStmts", "true");
        properties.putIfAbsent("connectTimeout", "10000");
        properties.putIfAbsent("socketTimeout", "60000");
        properties.setProperty("user", username);
        properties.setProperty("password", password);
        return DriverManager.getConnection(url, properties);
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
        // Debezium 2.7 passes driver.* values to its own JDBC connections.
        connectionProperties.forEach((key, value) -> properties.setProperty("driver." + key, value));
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
        properties.setProperty("include.schema.changes", Boolean.toString(schemaEvolutionEnabled));
        // Debezium's row schemas normally omit VARCHAR capacity. Propagate the
        // original column length so same-name ALTER TYPE changes can be fenced
        // before any newly widened row reaches a JDBC Sink.
        if (schemaEvolutionEnabled) {
            properties.setProperty("column.propagate.source.type", ".*");
        }
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
