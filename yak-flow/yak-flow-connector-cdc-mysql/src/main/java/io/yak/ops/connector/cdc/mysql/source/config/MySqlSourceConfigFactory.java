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

import io.yak.ops.connector.base.metrics.TableMetrics;
import io.yak.ops.connector.cdc.mysql.source.MySqlSource;
import io.yak.ops.connector.cdc.mysql.table.StartupOptions;
import io.yak.ops.core.data.TableId;
import io.yak.ops.core.types.TableSchema;
import java.io.Serializable;
import java.time.Duration;
import java.time.ZoneId;
import java.util.Arrays;
import java.util.HashMap;
import java.util.LinkedHashMap;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.Properties;
import java.util.Set;
import java.util.regex.Pattern;

/** A factory to construct {@link MySqlSourceConfig}. */
public class MySqlSourceConfigFactory implements Serializable {

    private static final long serialVersionUID = 1L;

    private int port = 3306; // default 3306 port
    private String hostname;
    private String username;
    private String password;
    private ServerIdRange serverIdRange;
    private List<String> databaseList;
    private List<String> tableList;
    private String excludeTableList;
    private String serverTimeZone = ZoneId.systemDefault().getId();
    private StartupOptions startupOptions = StartupOptions.initial();
    private boolean explicitStartupOptions;
    private int splitSize = MySqlSourceOptions.SCAN_INCREMENTAL_SNAPSHOT_CHUNK_SIZE.defaultValue();
    private int splitMetaGroupSize = MySqlSourceOptions.CHUNK_META_GROUP_SIZE.defaultValue();
    private int fetchSize = MySqlSourceOptions.SCAN_SNAPSHOT_FETCH_SIZE.defaultValue();
    private Duration connectTimeout = MySqlSourceOptions.CONNECT_TIMEOUT.defaultValue();
    private int connectMaxRetries = MySqlSourceOptions.CONNECT_MAX_RETRIES.defaultValue();
    private int connectionPoolSize = MySqlSourceOptions.CONNECTION_POOL_SIZE.defaultValue();
    private double distributionFactorUpper =
            MySqlSourceOptions.CHUNK_KEY_EVEN_DISTRIBUTION_FACTOR_UPPER_BOUND.defaultValue();
    private double distributionFactorLower =
            MySqlSourceOptions.CHUNK_KEY_EVEN_DISTRIBUTION_FACTOR_LOWER_BOUND.defaultValue();
    private boolean includeSchemaChanges = false;
    private boolean includeHeartbeatEvents = false;
    private boolean includeTransactionMetadataEvents = false;
    private boolean scanNewlyAddedTableEnabled = false;
    private boolean releaseSnapshotMetadataEnabled = false;
    private boolean closeIdleReaders = false;
    private Properties jdbcProperties;
    private Duration heartbeatInterval = MySqlSourceOptions.HEARTBEAT_INTERVAL.defaultValue();
    private Properties dbzProperties;
    private Map<TableId, String> chunkKeyColumns = new HashMap<>();
    private boolean skipSnapshotBackfill = false;
    private boolean parseOnLineSchemaChanges = false;
    private boolean treatTinyInt1AsBoolean = true;
    private boolean useLegacyJsonFormat = true;
    private boolean assignUnboundedChunkFirst = false;
    private final Map<TableId, MySqlTableOptions> tableOptions = new LinkedHashMap<>();
    private MySqlTableOptions defaultTableOptions = MySqlTableOptions.DEFAULT;
    private String defaultChunkKeyColumn;
    private String discoveryTablePattern;

    /** Matches unqualified table names independently of the database expression. */
    public MySqlSourceConfigFactory discoveryTablePattern(String pattern) {
        String value = Objects.requireNonNull(pattern, "pattern");
        if (value.isBlank() || value.length() > 256) {
            throw new IllegalArgumentException("Dynamic table pattern must contain 1 to 256 characters");
        }
        Pattern.compile(value);
        discoveryTablePattern = value;
        return this;
    }

    /** Configures snapshot scope and original mutation selection for one admitted table. */
    public MySqlSourceConfigFactory tableOptions(TableId table, MySqlTableOptions options) {
        tableOptions.put(Objects.requireNonNull(table, "table"), Objects.requireNonNull(options, "options"));
        return this;
    }

    /** Supplies the published default for subsequently discovered tables. */
    public MySqlSourceConfigFactory defaultTableOptions(MySqlTableOptions options) {
        defaultTableOptions = Objects.requireNonNull(options, "options");
        return this;
    }

    public MySqlSourceConfigFactory defaultChunkKeyColumn(String column) {
        defaultChunkKeyColumn = column;
        return this;
    }

    public MySqlSourceConfigFactory hostname(String hostname) {
        this.hostname = hostname;
        return this;
    }

    /** Integer port number of the MySQL database server. */
    public MySqlSourceConfigFactory port(int port) {
        this.port = port;
        return this;
    }

    /**
     * An optional list of regular expressions that match database names to be monitored; any
     * database name not included in the whitelist will be excluded from monitoring. By default all
     * databases will be monitored.
     */
    public MySqlSourceConfigFactory databaseList(String... databaseList) {
        this.databaseList = Arrays.asList(databaseList);
        return this;
    }

    /**
     * An optional list of regular expressions that match fully-qualified table identifiers for
     * tables to be monitored; any table not included in the list will be excluded from monitoring.
     * Each identifier is of the form databaseName.tableName. By default the connector will monitor
     * every non-system table in each monitored database.
     */
    public MySqlSourceConfigFactory tableList(String... tableList) {
        this.tableList = Arrays.asList(tableList);
        return this;
    }

    public MySqlSourceConfigFactory excludeTableList(String tableInclusions) {
        this.excludeTableList = tableInclusions;
        return this;
    }

    /** Name of the MySQL database to use when connecting to the MySQL database server. */
    public MySqlSourceConfigFactory username(String username) {
        this.username = username;
        return this;
    }

    /** Password to use when connecting to the MySQL database server. */
    public MySqlSourceConfigFactory password(String password) {
        this.password = password;
        return this;
    }

    /**
     * A numeric ID or a numeric ID range of this database client, The numeric ID syntax is like
     * '5400', the numeric ID range syntax is like '5400-5408', The numeric ID range syntax is
     * required when 'scan.incremental.snapshot.enabled' enabled. Every ID must be unique across all
     * currently-running database processes in the MySQL cluster. This connector joins the MySQL
     * cluster as another server (with this unique ID) so it can read the binlog. By default, a
     * random number is generated between 5400 and 6400, though we recommend setting an explicit
     * value."
     */
    public MySqlSourceConfigFactory serverId(String serverId) {
        ServerIdRange parsed = ServerIdRange.from(serverId);
        if (parsed != null && !serverId.contains("-")) {
            return serverId(parsed.getStartServerId());
        }
        this.serverIdRange = parsed;
        return this;
    }

    /**
     * The session time zone in database server, e.g. "America/Los_Angeles". It controls how the
     * TIMESTAMP type in MYSQL converted to STRING. See more
     * https://debezium.io/documentation/reference/1.9/connectors/mysql.html#mysql-temporal-types
     */
    public MySqlSourceConfigFactory serverTimeZone(String timeZone) {
        this.serverTimeZone = timeZone;
        return this;
    }

    /**
     * The chunk key of table snapshot, captured tables are split into multiple chunks by the chunk
     * key column when read the snapshot of table.
     */
    public MySqlSourceConfigFactory chunkKeyColumn(TableId objectPath, String chunkKeyColumn) {
        this.chunkKeyColumns.put(objectPath, chunkKeyColumn);
        return this;
    }

    /**
     * The chunk key of table snapshot, captured tables are split into multiple chunks by the chunk
     * key column when read the snapshot of table.
     */
    public MySqlSourceConfigFactory chunkKeyColumn(Map<TableId, String> chunkKeyColumns) {
        this.chunkKeyColumns.putAll(chunkKeyColumns);
        return this;
    }

    /**
     * The split size (number of rows) of table snapshot, captured tables are split into multiple
     * splits when read the snapshot of table.
     */
    public MySqlSourceConfigFactory splitSize(int splitSize) {
        this.splitSize = splitSize;
        return this;
    }

    /**
     * The group size of split meta, if the meta size exceeds the group size, the meta will be
     * divided into multiple groups.
     */
    public MySqlSourceConfigFactory splitMetaGroupSize(int splitMetaGroupSize) {
        this.splitMetaGroupSize = splitMetaGroupSize;
        return this;
    }

    /**
     * The upper bound of split key evenly distribution factor, the factor is used to determine
     * whether the table is evenly distribution or not.
     */
    public MySqlSourceConfigFactory distributionFactorUpper(double distributionFactorUpper) {
        this.distributionFactorUpper = distributionFactorUpper;
        return this;
    }

    /**
     * The lower bound of split key evenly distribution factor, the factor is used to determine
     * whether the table is evenly distribution or not.
     */
    public MySqlSourceConfigFactory distributionFactorLower(double distributionFactorLower) {
        this.distributionFactorLower = distributionFactorLower;
        return this;
    }

    /** The maximum fetch size for per poll when read table snapshot. */
    public MySqlSourceConfigFactory fetchSize(int fetchSize) {
        this.fetchSize = fetchSize;
        return this;
    }

    /**
     * The maximum time that the connector should wait after trying to connect to the MySQL database
     * server before timing out.
     */
    public MySqlSourceConfigFactory connectTimeout(Duration connectTimeout) {
        this.connectTimeout = connectTimeout;
        return this;
    }

    /** The connection pool size. */
    public MySqlSourceConfigFactory connectionPoolSize(int connectionPoolSize) {
        this.connectionPoolSize = connectionPoolSize;
        return this;
    }

    /** The max retry times to get connection. */
    public MySqlSourceConfigFactory connectMaxRetries(int connectMaxRetries) {
        this.connectMaxRetries = connectMaxRetries;
        return this;
    }

    /** Whether the {@link MySqlSource} should output the schema changes or not. */
    public MySqlSourceConfigFactory includeSchemaChanges(boolean includeSchemaChanges) {
        this.includeSchemaChanges = includeSchemaChanges;
        return this;
    }

    /** Whether the {@link MySqlSource} should output the heartbeat events or not. */
    public MySqlSourceConfigFactory includeHeartbeatEvents(boolean includeHeartbeatEvents) {
        this.includeHeartbeatEvents = includeHeartbeatEvents;
        return this;
    }

    /** Whether the {@link MySqlSource} should output the transaction metadata events or not. */
    public MySqlSourceConfigFactory includeTransactionMetadataEvents(boolean includeTransactionMetadataEvents) {
        this.includeTransactionMetadataEvents = includeTransactionMetadataEvents;
        return this;
    }

    /** Whether the {@link MySqlSource} should scan the newly added tables or not. */
    public MySqlSourceConfigFactory scanNewlyAddedTableEnabled(boolean scanNewlyAddedTableEnabled) {
        this.scanNewlyAddedTableEnabled = scanNewlyAddedTableEnabled;
        return this;
    }

    /**
     * Whether the source coordinator should release the snapshot split metadata after entering the
     * binlog phase to reduce JobManager memory. Incompatible with {@link
     * #scanNewlyAddedTableEnabled(boolean)}; disabled by default.
     */
    public MySqlSourceConfigFactory releaseSnapshotMetadataEnabled(boolean releaseSnapshotMetadataEnabled) {
        this.releaseSnapshotMetadataEnabled = releaseSnapshotMetadataEnabled;
        return this;
    }

    /** Custom properties that will overwrite the default JDBC connection URL. */
    public MySqlSourceConfigFactory jdbcProperties(Properties jdbcProperties) {
        this.jdbcProperties = jdbcProperties;
        return this;
    }

    /** Specifies the startup options. */
    public MySqlSourceConfigFactory startupOptions(StartupOptions startupOptions) {
        this.startupOptions = Objects.requireNonNull(startupOptions, "startupOptions");
        this.explicitStartupOptions = true;
        return this;
    }

    public MySqlSourceConfigFactory heartbeatInterval(Duration heartbeatInterval) {
        this.heartbeatInterval = heartbeatInterval;
        return this;
    }

    /** The Debezium MySQL connector properties. For example, "snapshot.mode". */
    public MySqlSourceConfigFactory debeziumProperties(Properties properties) {
        this.dbzProperties = properties;
        return this;
    }

    /**
     * Whether to skip backfill in snapshot reading phase.
     *
     * <p>If backfill is skipped, changes on captured tables during snapshot phase will be consumed
     * later in binlog reading phase instead of being merged into the snapshot.
     *
     * <p>WARNING: Skipping backfill might lead to data inconsistency because some binlog events
     * happened within the snapshot phase might be replayed (only at-least-once semantic is
     * promised). For example updating an already updated value in snapshot, or deleting an already
     * deleted entry in snapshot. These replayed binlog events should be handled specially.
     */
    public MySqlSourceConfigFactory skipSnapshotBackfill(boolean skipSnapshotBackfill) {
        this.skipSnapshotBackfill = skipSnapshotBackfill;
        return this;
    }

    /**
     * Whether to use legacy json format. The default value is true, which means there is no
     * whitespace before value and after comma in json format.
     */
    public MySqlSourceConfigFactory useLegacyJsonFormat(boolean useLegacyJsonFormat) {
        this.useLegacyJsonFormat = useLegacyJsonFormat;
        return this;
    }

    /**
     * Whether to close idle readers at the end of the snapshot phase. This feature depends on
     * FLIP-147: Support Checkpoints After Tasks Finished. The flink version is required to be
     * greater than or equal to 1.14, and the configuration <code>
     * 'execution.checkpointing.checkpoints-after-tasks-finish.enabled'</code> needs to be set to
     * true.
     *
     * <p>See more
     * https://cwiki.apache.org/confluence/display/FLINK/FLIP-147%3A+Support+Checkpoints+After+Tasks+Finished.
     */
    public MySqlSourceConfigFactory closeIdleReaders(boolean closeIdleReaders) {
        this.closeIdleReaders = closeIdleReaders;
        return this;
    }

    /** Whether to parse gh-ost/pt-osc utility generated schema change events. Defaults to false. */
    public MySqlSourceConfigFactory parseOnLineSchemaChanges(boolean parseOnLineSchemaChanges) {
        this.parseOnLineSchemaChanges = parseOnLineSchemaChanges;
        return this;
    }

    public MySqlSourceConfigFactory treatTinyInt1AsBoolean(boolean treatTinyInt1AsBoolean) {
        this.treatTinyInt1AsBoolean = treatTinyInt1AsBoolean;
        return this;
    }

    /**
     * Whether to assign the unbounded chunks first during snapshot reading phase. Defaults to
     * false.
     */
    public MySqlSourceConfigFactory assignUnboundedChunkFirst(boolean assignUnboundedChunkFirst) {
        this.assignUnboundedChunkFirst = assignUnboundedChunkFirst;
        return this;
    }

    // YakFlow-specific identity, prepared table schemas and bounded queue capacity.
    private final Map<TableId, TableSchema> schemas = new LinkedHashMap<>();
    private String topicPrefix;
    private int queueCapacity = MySqlSourceOptions.QUEUE_CAPACITY.defaultValue();
    private Map<String, String> connectionProperties = Map.of();

    public MySqlSourceConfigFactory serverId(int baseServerId) {
        if (baseServerId < 1 || baseServerId > Integer.MAX_VALUE - 16) {
            throw new IllegalArgumentException("MySQL replication server ID cannot reserve sixteen snapshot IDs");
        }
        this.serverIdRange = new ServerIdRange(baseServerId, baseServerId + 16);
        return this;
    }

    public MySqlSourceConfigFactory topicPrefix(String topicPrefix) {
        this.topicPrefix = topicPrefix;
        return this;
    }

    public MySqlSourceConfigFactory queueCapacity(int queueCapacity) {
        this.queueCapacity = queueCapacity;
        return this;
    }

    public MySqlSourceConfigFactory connectionProperties(Map<String, String> properties) {
        this.connectionProperties = new LinkedHashMap<>(Objects.requireNonNull(properties));
        return this;
    }

    /** Registers YakFlow's validated logical schema; no additional schema contract is created. */
    public MySqlSourceConfigFactory table(TableId table, TableSchema schema) {
        return table(table, schema, null);
    }

    public MySqlSourceConfigFactory table(TableId table, TableSchema schema, String chunkKeyColumn) {
        if (schemas.putIfAbsent(Objects.requireNonNull(table), Objects.requireNonNull(schema)) != null) {
            throw new IllegalArgumentException("Duplicate MySQL CDC table " + table);
        }
        if (chunkKeyColumn != null) chunkKeyColumn(table, chunkKeyColumn);
        return this;
    }

    public MySqlSourceConfigFactory tables(Map<TableId, TableSchema> tables) {
        Objects.requireNonNull(tables).forEach(this::table);
        return this;
    }

    public MySqlSourceConfig createConfig(int subtaskId) {
        return createConfig(subtaskId, topicPrefix);
    }

    /** Creates a config without opening a JDBC or Binlog connection. */
    public MySqlSourceConfig createConfig(int subtaskId, String serverName) {
        if (subtaskId < 0) throw new IllegalArgumentException("Subtask ID must not be negative");
        if (serverIdRange == null) throw new IllegalArgumentException("MySQL server-id is required");
        if (schemas.isEmpty()) throw new IllegalArgumentException("MySQL CDC requires at least one table");
        String topic = Objects.requireNonNull(serverName, "topicPrefix");
        Set<String> dbs = new LinkedHashSet<>();
        Set<String> tables = new LinkedHashSet<>();
        schemas.keySet().forEach(t -> {
            dbs.add(Pattern.quote(t.catalog()));
            tables.add(Pattern.quote(t.catalog()) + "\\." + Pattern.quote(t.table()));
        });
        boolean dynamicTableDiscovery = scanNewlyAddedTableEnabled;
        List<String> effectiveDatabases =
                databaseList != null ? databaseList : dynamicTableDiscovery ? List.of() : List.copyOf(dbs);
        List<String> effectiveTables =
                tableList != null ? tableList : dynamicTableDiscovery ? List.of() : List.copyOf(tables);
        Properties props = new Properties();
        props.setProperty("database.hostname", Objects.requireNonNull(hostname, "hostname"));
        props.setProperty("database.port", Integer.toString(port));
        props.setProperty("database.user", Objects.requireNonNull(username, "username"));
        props.setProperty("database.password", Objects.requireNonNull(password, "password"));
        props.setProperty("database.server.id", Integer.toString(serverIdRange.getServerId(subtaskId)));
        props.setProperty("topic.prefix", topic);
        if (!effectiveDatabases.isEmpty()) {
            props.setProperty("database.include.list", String.join(",", effectiveDatabases));
        }
        List<String> includedTables = includeTablesExcept(effectiveTables, excludeTableList);
        if (!includedTables.isEmpty()) {
            props.setProperty("table.include.list", String.join(",", includedTables));
        } else if (excludeTableList != null && !excludeTableList.isBlank()) {
            // Debezium permits an exclude-only filter when no include patterns were configured.
            props.setProperty("table.exclude.list", excludeTableList);
        }
        if (dbzProperties != null) props.putAll(dbzProperties);
        return new MySqlSourceConfig(
                hostname,
                port,
                username,
                password,
                serverIdRange.getStartServerId(),
                topic,
                schemas,
                queueCapacity,
                connectionProperties,
                chunkKeyColumns,
                null,
                false,
                sourceSettings(props, effectiveDatabases, effectiveTables));
    }

    /**
     * Debezium 2.7 does not permit table.include.list together with table.exclude.list.
     * Apply exclusions to the include expressions instead of sending both properties.
     * Each pattern is matched against the entire qualified table name.
     */
    static List<String> includeTablesExcept(List<String> inclusions, String exclusions) {
        if (exclusions == null || exclusions.isBlank()) {
            return List.copyOf(inclusions);
        }
        String excluded = Arrays.stream(exclusions.split(","))
                .map(String::trim)
                .filter(pattern -> !pattern.isEmpty())
                .collect(java.util.stream.Collectors.joining("|"));
        if (excluded.isEmpty()) {
            return List.copyOf(inclusions);
        }
        String notExcluded = "(?!(?:" + excluded + ")$)";
        return inclusions.stream()
                .map(inclusion -> notExcluded + "(?:" + inclusion + ")")
                .toList();
    }

    static SourceSettings defaultSettings(int serverId) {
        MySqlSourceConfigFactory factory = new MySqlSourceConfigFactory();
        factory.serverIdRange = serverId <= Integer.MAX_VALUE - 16
                ? new ServerIdRange(serverId, serverId + 16)
                : new ServerIdRange(serverId, serverId);
        return factory.sourceSettings(new Properties(), List.of(), List.of());
    }

    private SourceSettings sourceSettings(Properties props, List<String> databases, List<String> tables) {
        Properties jdbc = new Properties();
        if (jdbcProperties != null) jdbc.putAll(jdbcProperties);
        else jdbc.putAll(connectionProperties);
        Properties debezium = new Properties();
        debezium.putAll(props);
        return new SourceSettings(
                List.copyOf(databases),
                List.copyOf(tables),
                excludeTableList,
                serverIdRange,
                startupOptions,
                splitSize,
                splitMetaGroupSize,
                fetchSize,
                serverTimeZone,
                connectTimeout,
                connectMaxRetries,
                connectionPoolSize,
                distributionFactorUpper,
                distributionFactorLower,
                includeSchemaChanges,
                includeHeartbeatEvents,
                includeTransactionMetadataEvents,
                scanNewlyAddedTableEnabled,
                closeIdleReaders,
                jdbc,
                skipSnapshotBackfill,
                parseOnLineSchemaChanges,
                treatTinyInt1AsBoolean,
                useLegacyJsonFormat,
                assignUnboundedChunkFirst,
                releaseSnapshotMetadataEnabled,
                debezium,
                Map.copyOf(tableOptions),
                defaultTableOptions,
                defaultChunkKeyColumn,
                discoveryTablePattern);
    }

    /** Immutable data kept in MySqlSourceConfig across reader creation and checkpoint recovery. */
    static record SourceSettings(
            List<String> databaseList,
            List<String> tableList,
            String excludeTableList,
            ServerIdRange serverIdRange,
            StartupOptions startupOptions,
            int splitSize,
            int splitMetaGroupSize,
            int fetchSize,
            String serverTimeZone,
            Duration connectTimeout,
            int connectMaxRetries,
            int connectionPoolSize,
            double distributionFactorUpper,
            double distributionFactorLower,
            boolean includeSchemaChanges,
            boolean includeHeartbeatEvents,
            boolean includeTransactionMetadataEvents,
            boolean scanNewlyAddedTableEnabled,
            boolean closeIdleReaders,
            Properties jdbcProperties,
            boolean skipSnapshotBackfill,
            boolean parseOnLineSchemaChanges,
            boolean treatTinyInt1AsBoolean,
            boolean useLegacyJsonFormat,
            boolean assignUnboundedChunkFirst,
            boolean releaseSnapshotMetadataEnabled,
            Properties dbzProperties,
            Map<TableId, MySqlTableOptions> tableOptions,
            MySqlTableOptions defaultTableOptions,
            String defaultChunkKeyColumn,
            String discoveryTablePattern) {}

    /** Builds the snapshot and binlog Source with a single configuration contract. */
    public MySqlSource buildHybrid(int chunkSize) {
        return buildHybrid(chunkSize, new TableMetrics());
    }

    public MySqlSource buildHybrid(int chunkSize, TableMetrics metrics) {
        splitSize(chunkSize);
        if (!explicitStartupOptions) startupOptions(StartupOptions.initial());
        if (startupOptions.isStreamOnly()) {
            throw new IllegalArgumentException("Snapshot Source cannot run a Binlog-only startup mode");
        }
        return MySqlSource.initial(createConfig(0), chunkSize, metrics, startupOptions);
    }

    public MySqlSource build() {
        return build(new TableMetrics());
    }

    public MySqlSource build(TableMetrics metrics) {
        if (!explicitStartupOptions) startupOptions(StartupOptions.latest());
        if (!startupOptions.isStreamOnly()) {
            throw new IllegalArgumentException("Stream-only MySQL Source requires a Binlog startup mode");
        }
        return MySqlSource.latestOffset(createConfig(0), metrics, startupOptions);
    }
}
