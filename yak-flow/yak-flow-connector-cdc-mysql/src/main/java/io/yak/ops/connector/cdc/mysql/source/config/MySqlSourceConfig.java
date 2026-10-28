/*
 * Licensed to the Apache Software Foundation (ASF) under one or more
 * contributor license agreements.  See the NOTICE file distributed with
 * this work for additional information regarding copyright ownership.
 * The ASF licenses this file to You under the Apache License, Version 2.0
 * (the "License"); you may not use this file except in compliance with
 * the License.  You may obtain a copy of the License at
 *
 *      http://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS,
 * WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 * See the License for the specific language governing permissions and
 * limitations under the License.
 */

package io.yak.ops.connector.cdc.mysql.source.config;

import com.github.shyiko.mysql.binlog.BinaryLogClient;
import io.debezium.config.Configuration;
import io.debezium.connector.mysql.MySqlConnectorConfig;
import io.debezium.relational.RelationalTableFilters;
import io.yak.ops.connector.cdc.mysql.debezium.CheckpointOffsetBackingStore;
import io.yak.ops.connector.cdc.mysql.debezium.DebeziumUtils;
import io.yak.ops.connector.cdc.mysql.source.utils.ChunkUtils;
import io.yak.ops.connector.cdc.mysql.table.StartupOptions;
import io.yak.ops.connector.jdbc.database.internal.convert.MySqlTypeConverter;
import io.yak.ops.core.api.connector.source.SourceReaderContext;
import io.yak.ops.core.configuration.SchemaEvolutionOptions;
import io.yak.ops.core.data.TableId;
import io.yak.ops.core.schema.Schema;
import io.yak.ops.core.types.Column;
import io.yak.ops.core.types.TableSchema;
import java.nio.file.Path;
import java.sql.Connection;
import java.sql.DatabaseMetaData;
import java.sql.DriverManager;
import java.sql.ResultSet;
import java.sql.SQLException;
import java.time.Duration;
import java.util.ArrayList;
import java.util.Collections;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.Properties;
import java.util.Set;
import java.util.TreeMap;
import java.util.TreeSet;
import java.util.function.Predicate;
import java.util.regex.Pattern;
import java.util.stream.Collectors;

/**
 * Immutable MySQL Binlog connection and schema contract independent of execution attempts.
 *
 * <p>Production credentials never appear in toString(), Split or Enumerator checkpoint state.
 * Exactly one Binlog reader is supported in the initial stream-only connector.
 */
public final class MySqlSourceConfig {

    private final String hostname;
    private final int port;
    private final String username;
    private final String password;
    private final int serverId;
    private final String topicPrefix;
    private final Map<TableId, TableSchema> schemas;
    private final Map<TableId, String> chunkKeyColumns;
    private final int queueCapacity;
    private final Map<String, String> connectionProperties;
    private final Map<TableId, Schema> physicalSchemas;
    // Runtime-discovered tables are kept separate from the immutable configured schema contract.
    private final Map<TableId, TableSchema> discoveredSchemas = new LinkedHashMap<>();
    private final Map<TableId, String> discoveredChunkKeyColumns = new LinkedHashMap<>();
    private final Map<TableId, Schema> discoveredPhysicalSchemas = new LinkedHashMap<>();
    private final boolean schemaEvolutionEnabled;
    private final MySqlSourceConfigFactory.SourceSettings sourceSettings;
    private final List<String> databaseList;
    private final List<String> tableList;
    private final String excludeTableList;
    private final ServerIdRange serverIdRange;
    private final StartupOptions startupOptions;
    private final int splitSize;
    private final int splitMetaGroupSize;
    private final int fetchSize;
    private final Duration connectTimeout;
    private final Duration heartbeatInterval;
    private final double distributionFactorUpper;
    private final double distributionFactorLower;
    private final boolean scanNewlyAddedTableEnabled;
    private final Properties jdbcProperties;
    private final boolean skipSnapshotBackfill;
    private final boolean releaseSnapshotMetadataEnabled;
    private final Properties dbzProperties;

    public MySqlSourceConfig(
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

    public MySqlSourceConfig(
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
                Map.of());
    }

    public MySqlSourceConfig(
            String hostname,
            int port,
            String username,
            String password,
            int serverId,
            String topicPrefix,
            Map<TableId, TableSchema> schemas,
            int queueCapacity,
            Map<String, String> connectionProperties,
            Map<TableId, String> chunkKeyColumns) {
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
                chunkKeyColumns,
                null,
                false);
    }

    MySqlSourceConfig(
            String hostname,
            int port,
            String username,
            String password,
            int serverId,
            String topicPrefix,
            Map<TableId, TableSchema> schemas,
            int queueCapacity,
            Map<String, String> connectionProperties,
            Map<TableId, String> chunkKeyColumns,
            Map<TableId, Schema> physicalSchemas,
            boolean schemaEvolutionEnabled) {
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
                chunkKeyColumns,
                physicalSchemas,
                schemaEvolutionEnabled,
                MySqlSourceConfigFactory.defaultSettings(serverId));
    }

    MySqlSourceConfig(
            String hostname,
            int port,
            String username,
            String password,
            int serverId,
            String topicPrefix,
            Map<TableId, TableSchema> schemas,
            int queueCapacity,
            Map<String, String> connectionProperties,
            Map<TableId, String> chunkKeyColumns,
            Map<TableId, Schema> physicalSchemas,
            boolean schemaEvolutionEnabled,
            MySqlSourceConfigFactory.SourceSettings settings) {
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
        this.connectionProperties = prepareConnectionProperties(connectionProperties);
        Map<TableId, TableSchema> resolved = new LinkedHashMap<>();
        Objects.requireNonNull(schemas, "schemas").forEach((table, schema) -> {
            Objects.requireNonNull(table, "table");
            Objects.requireNonNull(schema, "schema");
            if (table.catalog() == null || table.schema() != null) {
                throw new IllegalArgumentException("MySQL CDC requires database-qualified tables");
            }
            if (schema.primaryKeys().isEmpty()) {
                ChunkUtils.getChunkKeyColumn(schema, chunkKeyColumns.get(table));
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
        Map<TableId, String> requestedKeys = new LinkedHashMap<>();
        Objects.requireNonNull(chunkKeyColumns, "chunkKeyColumns").forEach((table, selected) -> {
            TableSchema schema = resolved.get(table);
            if (schema == null) {
                throw new IllegalArgumentException("Unknown MySQL CDC chunk-key table: " + table);
            }
            requestedKeys.put(
                    table, ChunkUtils.getChunkKeyColumn(schema, selected).name());
        });
        this.chunkKeyColumns = Collections.unmodifiableMap(requestedKeys);
        Map<TableId, Schema> shapes = new LinkedHashMap<>();
        resolved.forEach((table, tableSchema) -> shapes.put(
                table,
                physicalSchemas == null
                        ? Schema.from(tableSchema, null, Map.of(), Map.of())
                        : Objects.requireNonNull(physicalSchemas.get(table), "Physical source schema is missing")));
        this.physicalSchemas = Collections.unmodifiableMap(shapes);
        this.sourceSettings = Objects.requireNonNull(settings, "source settings");
        this.databaseList = settings.databaseList();
        this.tableList = settings.tableList();
        this.excludeTableList = settings.excludeTableList();
        this.serverIdRange = settings.serverIdRange();
        this.startupOptions = settings.startupOptions();
        this.splitSize = settings.splitSize();
        this.splitMetaGroupSize = settings.splitMetaGroupSize();
        this.fetchSize = settings.fetchSize();
        this.connectTimeout = settings.connectTimeout();
        this.heartbeatInterval = settings.heartbeatInterval();
        this.distributionFactorUpper = settings.distributionFactorUpper();
        this.distributionFactorLower = settings.distributionFactorLower();
        this.scanNewlyAddedTableEnabled = settings.scanNewlyAddedTableEnabled();
        this.jdbcProperties = settings.jdbcProperties();
        this.skipSnapshotBackfill = settings.skipSnapshotBackfill();
        this.releaseSnapshotMetadataEnabled = settings.releaseSnapshotMetadataEnabled();
        this.dbzProperties = settings.dbzProperties();
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
    private static Map<String, String> prepareConnectionProperties(Map<String, String> supplied) {
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

    public synchronized Map<TableId, TableSchema> schemas() {
        Map<TableId, TableSchema> result = new LinkedHashMap<>(schemas);
        result.putAll(discoveredSchemas);
        return Collections.unmodifiableMap(result);
    }

    /** Explicit table-level chunk columns; absent entries default to the first physical primary-key column. */
    public synchronized Map<TableId, String> chunkKeyColumns() {
        Map<TableId, String> result = new LinkedHashMap<>(chunkKeyColumns);
        result.putAll(discoveredChunkKeyColumns);
        return Collections.unmodifiableMap(result);
    }

    public synchronized Map<TableId, Schema> physicalSchemas() {
        Map<TableId, Schema> result = new LinkedHashMap<>(physicalSchemas);
        result.putAll(discoveredPhysicalSchemas);
        return Collections.unmodifiableMap(result);
    }

    /** Discovers a table's logical shape from live JDBC metadata. */
    public TableSchema discoverTable(TableId table, String requestedChunkKey) throws SQLException {
        try (Connection connection = openSnapshotConnection()) {
            return discoverTable(connection, table, requestedChunkKey);
        }
    }

    public synchronized TableSchema discoverTable(Connection connection, TableId table, String requestedChunkKey)
            throws SQLException {
        Objects.requireNonNull(connection, "connection");
        Objects.requireNonNull(table, "table");
        TableSchema existing = discoveredSchemas.get(table);
        if (existing == null) existing = schemas.get(table);
        if (existing != null) {
            if (requestedChunkKey != null) {
                try {
                    String selected = ChunkUtils.requireSupportedSnapshot(existing, requestedChunkKey)
                            .name();
                    if (!selected.equals(requestedChunkKey))
                        throw new SQLException("Configured Snapshot key differs from the requested key for " + table);
                    if (!chunkKeyColumns.containsKey(table)) discoveredChunkKeyColumns.putIfAbsent(table, selected);
                } catch (IllegalArgumentException invalidKey) {
                    throw new SQLException("Invalid Snapshot chunk key for " + table, invalidKey);
                }
            }
            return existing;
        }

        DatabaseMetaData metadata = connection.getMetaData();
        String catalog = table.catalog() == null ? connection.getCatalog() : table.catalog();
        TreeMap<Integer, Column> columns = new TreeMap<>();
        try (ResultSet result = metadata.getColumns(catalog, table.schema(), table.table(), null)) {
            while (result.next()) {
                if (!table.table().equals(result.getString("TABLE_NAME"))) continue;
                String name = result.getString("COLUMN_NAME");
                int jdbcType = result.getInt("DATA_TYPE");
                String nativeType = result.getString("TYPE_NAME");
                int precision = result.getInt("COLUMN_SIZE");
                int scale = result.getInt("DECIMAL_DIGITS");
                boolean nullable = result.getInt("NULLABLE") != DatabaseMetaData.columnNoNulls;
                var logicalType =
                        new MySqlTypeConverter().convertColumn(jdbcType, nativeType, precision, scale, nullable, name);
                columns.put(result.getInt("ORDINAL_POSITION"), new Column(name, logicalType));
            }
        }
        if (columns.isEmpty()) throw new SQLException("Cannot discover columns for newly added MySQL table " + table);

        TreeMap<Short, String> primaryKeys = new TreeMap<>();
        try (ResultSet result = metadata.getPrimaryKeys(catalog, table.schema(), table.table())) {
            while (result.next()) primaryKeys.put(result.getShort("KEY_SEQ"), result.getString("COLUMN_NAME"));
        }
        TableSchema discovered =
                new TableSchema(new ArrayList<>(columns.values()), new ArrayList<>(primaryKeys.values()));
        String selectedKey;
        try {
            selectedKey = ChunkUtils.requireSupportedSnapshot(discovered, requestedChunkKey)
                    .name();
        } catch (RuntimeException invalidKey) {
            throw new SQLException(
                    "Newly added table " + table + " needs a primary key or configured Snapshot chunk key", invalidKey);
        }
        discoveredSchemas.put(table, discovered);
        discoveredChunkKeyColumns.put(table, selectedKey);
        discoveredPhysicalSchemas.put(table, Schema.from(discovered, null, Map.of(), Map.of()));
        return discovered;
    }

    public boolean schemaEvolutionEnabled() {
        return schemaEvolutionEnabled;
    }

    /**
     * A checkpoint restores the same source schema and Debezium offset before the Reader starts.
     * A disabled evolution job retains its original schemas unchanged.
     */
    public MySqlSourceConfig withRuntimeState(SourceReaderContext context) {
        Objects.requireNonNull(context, "context");
        boolean enabled = context.getConfiguration().get(SchemaEvolutionOptions.ENABLED);
        Map<TableId, Schema> models = new LinkedHashMap<>(physicalSchemas());
        Map<TableId, TableSchema> shape = new LinkedHashMap<>(schemas());
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
        return new MySqlSourceConfig(
                hostname,
                port,
                username,
                password,
                serverId,
                topicPrefix,
                shape,
                queueCapacity,
                connectionProperties,
                chunkKeyColumns,
                models,
                enabled,
                sourceSettings);
    }

    /** Disables DDL adoption in snapshot-backfill readers, where schema changes cannot be replayed safely. */
    public MySqlSourceConfig withoutSchemaEvolution() {
        return new MySqlSourceConfig(
                hostname,
                port,
                username,
                password,
                serverId,
                topicPrefix,
                schemas,
                queueCapacity,
                connectionProperties,
                chunkKeyColumns,
                physicalSchemas,
                false,
                sourceSettings);
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

    /** Creates a one-shot binlog position discovery connection without exposing source credentials. */
    public BinaryLogClient newBinlogLookupClient() {
        return DebeziumUtils.createBinaryClient(this);
    }

    /** Returns the reserved replication ID for one of at most sixteen snapshot readers. */
    public int snapshotServerId(int reader) {
        if (reader < 0
                || reader >= 16
                || serverId > Integer.MAX_VALUE - 16
                || (serverIdRange != null && serverId + reader + 1 > serverIdRange.getEndServerId())) {
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
        properties.putIfAbsent("connectTimeout", Long.toString(connectTimeout.toMillis()));
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
        if (scanNewlyAddedTableEnabled) {
            if (!databaseList.isEmpty())
                properties.setProperty("database.include.list", String.join(",", databaseList));
            else properties.remove("database.include.list");
            if (!tableList.isEmpty()) {
                properties.setProperty(
                        "table.include.list",
                        String.join(",", MySqlSourceConfigFactory.includeTablesExcept(tableList, excludeTableList)));
                properties.remove("table.exclude.list");
            } else {
                properties.remove("table.include.list");
                if (excludeTableList != null && !excludeTableList.isBlank()) {
                    properties.setProperty("table.exclude.list", excludeTableList);
                } else {
                    properties.remove("table.exclude.list");
                }
            }
        } else {
            Set<String> databaseNames = new TreeSet<>();
            schemas.keySet().forEach(table -> databaseNames.add(table.catalog()));
            properties.setProperty(
                    "database.include.list",
                    databaseNames.stream().map(Pattern::quote).collect(Collectors.joining(",")));
            properties.setProperty(
                    "table.include.list",
                    String.join(
                            ",",
                            MySqlSourceConfigFactory.includeTablesExcept(
                                    tableList.isEmpty()
                                            ? schemas.keySet().stream()
                                                    .map(table -> Pattern.quote(table.catalog()) + "\\."
                                                            + Pattern.quote(table.table()))
                                                    .toList()
                                            : tableList,
                                    excludeTableList)));
        }
        properties.setProperty("snapshot.mode", "no_data");
        properties.setProperty("include.schema.changes", Boolean.toString(schemaEvolutionEnabled));
        // Debezium's row schemas normally omit VARCHAR capacity. Propagate the
        // original column length so same-name ALTER TYPE changes can be fenced
        // before any newly widened row reaches a JDBC Sink.
        if (schemaEvolutionEnabled) {
            properties.setProperty("column.propagate.source.type", ".*");
        }
        properties.setProperty("tombstones.on.delete", "false");
        properties.setProperty("heartbeat.interval.ms", Long.toString(heartbeatInterval.toMillis()));
        properties.setProperty("decimal.handling.mode", "precise");
        properties.setProperty("binary.handling.mode", "bytes");
        properties.setProperty("time.precision.mode", "adaptive_time_microseconds");
        // Debezium's queue and the YakFlow Binlog handover share one bounded
        // source queue capacity; do not hide a second fixed 512-event buffer.
        properties.setProperty("max.batch.size", Integer.toString(Math.min(128, Math.max(1, queueCapacity - 1))));
        properties.setProperty("max.queue.size", Integer.toString(Math.max(2, queueCapacity)));
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
        return "MySqlSourceConfig{credentials=redacted, tables=" + schemas().size() + "}";
    }

    public String getHostname() {
        return hostname;
    }

    public int getPort() {
        return port;
    }

    public String getUsername() {
        return username;
    }

    public String getPassword() {
        return password;
    }

    public List<String> getDatabaseList() {
        return databaseList;
    }

    public List<String> getTableList() {
        return tableList;
    }

    public ServerIdRange getServerIdRange() {
        return serverIdRange;
    }

    public StartupOptions getStartupOptions() {
        return startupOptions;
    }

    public int getSplitSize() {
        return splitSize;
    }

    public int getSplitMetaGroupSize() {
        return splitMetaGroupSize;
    }

    public double getDistributionFactorUpper() {
        return distributionFactorUpper;
    }

    public double getDistributionFactorLower() {
        return distributionFactorLower;
    }

    public int getFetchSize() {
        return fetchSize;
    }


    public Duration getConnectTimeout() {
        return connectTimeout;
    }

    public Duration getHeartbeatInterval() {
        return heartbeatInterval;
    }






    public boolean isScanNewlyAddedTableEnabled() {
        return scanNewlyAddedTableEnabled;
    }

    public boolean isReleaseSnapshotMetadataEnabled() {
        return releaseSnapshotMetadataEnabled;
    }




    public Properties getDbzProperties() {
        return dbzProperties;
    }

    public Configuration getDbzConfiguration() {
        return Configuration.from(dbzProperties);
    }

    public MySqlConnectorConfig getMySqlConnectorConfig() {
        return new MySqlConnectorConfig(getDbzConfiguration());
    }

    @Deprecated
    public RelationalTableFilters getTableFilters() {
        return getMySqlConnectorConfig().getTableFilters();
    }

    public Predicate<String> getDatabaseFilter() {
        RelationalTableFilters tableFilters = getMySqlConnectorConfig().getTableFilters();
        return (String databaseName) -> tableFilters.databaseFilter().test(databaseName);
    }

    public Predicate<io.debezium.relational.TableId> getTableFilter() {
        RelationalTableFilters tableFilters = getMySqlConnectorConfig().getTableFilters();
        List<Pattern> exclusions = excludeTableList == null || excludeTableList.isBlank()
                ? List.of()
                : java.util.Arrays.stream(excludeTableList.split(","))
                        .map(String::trim)
                        .filter(value -> !value.isEmpty())
                        .map(Pattern::compile)
                        .toList();
        return tableId -> tableFilters.dataCollectionFilter().isIncluded(tableId)
                && exclusions.stream()
                        .noneMatch(
                                pattern -> pattern.matcher(tableId.toString()).matches());
    }

    public Properties getJdbcProperties() {
        return jdbcProperties;
    }

    public Map<io.yak.ops.core.data.TableId, String> getChunkKeyColumns() {
        return chunkKeyColumns;
    }

    public boolean isSkipSnapshotBackfill() {
        return skipSnapshotBackfill;
    }


    /** Compatibility with Flink CDC's source configuration model; YakFlow owns the runtime schema. */
    public boolean isSchemaEvolutionEnabled() {
        return schemaEvolutionEnabled;
    }
}
