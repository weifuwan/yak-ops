package io.yak.ops.connector.cdc.mysql.factory;

import com.google.auto.service.AutoService;
import io.yak.ops.configuration.ReadonlyConfig;
import io.yak.ops.configuration.rule.Conditions;
import io.yak.ops.configuration.rule.OptionRule;
import io.yak.ops.connector.base.factory.FactoryUtil;
import io.yak.ops.connector.base.factory.SourceFactoryContext;
import io.yak.ops.connector.base.factory.TableSourceFactoryContext;
import io.yak.ops.connector.base.source.TableDataSource;
import io.yak.ops.connector.cdc.mysql.source.MySqlSource;
import io.yak.ops.connector.cdc.mysql.source.config.MySqlSourceConfigFactory;
import io.yak.ops.connector.cdc.mysql.source.config.MySqlSourceOptions;
import io.yak.ops.connector.cdc.mysql.source.utils.ChunkUtils;
import io.yak.ops.connector.cdc.mysql.table.StartupOptions;
import io.yak.ops.connector.jdbc.JdbcConnectionOptions;
import io.yak.ops.core.api.connector.source.Boundedness;
import io.yak.ops.core.api.connector.source.Source;
import io.yak.ops.core.data.TableRecord;
import io.yak.ops.core.factories.DataSourceFactory;
import io.yak.ops.core.source.DataSource;
import io.yak.ops.core.types.Column;
import io.yak.ops.core.types.TableSchema;
import java.net.URI;
import java.net.URLDecoder;
import java.nio.charset.StandardCharsets;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Objects;

/** Creates an initial Snapshot + Binlog Source or a latest-position Binlog Source. */
@AutoService(DataSourceFactory.class)
public final class MySqlDataSourceFactory implements DataSourceFactory {

    private static final OptionRule PHYSICAL_RULE = OptionRule.builder()
            .optional(
                    MySqlSourceOptions.SCAN_INCREMENTAL_SNAPSHOT_CHUNK_SIZE,
                    Conditions.greaterOrEqual(MySqlSourceOptions.SCAN_INCREMENTAL_SNAPSHOT_CHUNK_SIZE, 1)
                            .and(Conditions.lessOrEqual(
                                    MySqlSourceOptions.SCAN_INCREMENTAL_SNAPSHOT_CHUNK_SIZE, 100_000)))
            .optional(
                    MySqlSourceOptions.QUEUE_CAPACITY,
                    Conditions.greaterOrEqual(MySqlSourceOptions.QUEUE_CAPACITY, 1)
                            .and(Conditions.lessOrEqual(MySqlSourceOptions.QUEUE_CAPACITY, 10_000)))
            .build();

    @Override
    public boolean supportsSchemaMetadata() {
        return true;
    }

    @Override
    public String identifier() {
        return "mysql";
    }

    @Override
    public OptionRule optionRule() {
        return OptionRule.builder()
                .optional(
                        MySqlSourceOptions.SCAN_INCREMENTAL_SNAPSHOT_CHUNK_KEY_COLUMN,
                        Conditions.notBlank(MySqlSourceOptions.SCAN_INCREMENTAL_SNAPSHOT_CHUNK_KEY_COLUMN))
                .optional(MySqlSourceOptions.SCAN_STARTUP_MODE)
                .optional(
                        MySqlSourceOptions.SCAN_NEWLY_ADDED_TABLE_ENABLED,
                        MySqlSourceOptions.DATABASE_NAME,
                        MySqlSourceOptions.TABLE_NAME,
                        MySqlSourceOptions.DISCOVERY_DEFAULTS)
                .optional(
                        MySqlSourceOptions.SNAPSHOT_SKIP,
                        MySqlSourceOptions.SNAPSHOT_WHERE,
                        MySqlSourceOptions.DML_INSERT,
                        MySqlSourceOptions.DML_UPDATE,
                        MySqlSourceOptions.DML_DELETE)
                .optional(
                        MySqlSourceOptions.SCAN_STARTUP_SPECIFIC_OFFSET_FILE,
                        MySqlSourceOptions.SCAN_STARTUP_SPECIFIC_OFFSET_POS,
                        MySqlSourceOptions.SCAN_STARTUP_SPECIFIC_OFFSET_GTID_SET,
                        MySqlSourceOptions.SCAN_STARTUP_SPECIFIC_OFFSET_SKIP_EVENTS,
                        MySqlSourceOptions.SCAN_STARTUP_SPECIFIC_OFFSET_SKIP_ROWS,
                        MySqlSourceOptions.SCAN_STARTUP_TIMESTAMP_MILLIS)
                .build();
    }

    @Override
    public OptionRule creationOptionRule() {
        return OptionRule.builder()
                .include(optionRule())
                .include(JdbcConnectionOptions.RULE)
                .include(PHYSICAL_RULE)
                .required(MySqlSourceOptions.SERVER_ID, Conditions.notBlank(MySqlSourceOptions.SERVER_ID))
                .required(MySqlSourceOptions.TOPIC_PREFIX, Conditions.notBlank(MySqlSourceOptions.TOPIC_PREFIX))
                .build();
    }

    @Override
    public Boundedness boundedness(ReadonlyConfig options) {
        StartupOptions startup = startupOptions(options);
        startup.requireSupported();
        return startup.isSnapshotOnly() ? Boundedness.BOUNDED : Boundedness.CONTINUOUS_UNBOUNDED;
    }

    @Override
    public String datasourceType() {
        return "MYSQL";
    }

    @Override
    public ReadonlyConfig prepareExecutionOptions(ReadonlyConfig options, String executionId) {
        Objects.requireNonNull(executionId, "executionId");
        Map<String, Object> prepared = new LinkedHashMap<>(options.getSourceMap());
        prepared.put(MySqlSourceOptions.SERVER_ID.key(), Integer.toString(serverId(executionId)));
        prepared.put(MySqlSourceOptions.TOPIC_PREFIX.key(), "yakflow_" + executionId);
        return ReadonlyConfig.fromMap(prepared);
    }

    @Override
    public Map<String, String> exclusiveResources(String executionId) {
        return Map.of("MYSQL_CDC_SERVER_ID", Integer.toString(serverId(executionId)));
    }

    private static int serverId(String executionId) {
        Objects.requireNonNull(executionId, "executionId");
        return Math.floorMod(executionId.hashCode(), 62_500_000) * 32 + 1;
    }

    @Override
    public boolean producesChangelog() {
        return true;
    }

    @Override
    public boolean producesChangelog(ReadonlyConfig options) {
        StartupOptions startup = startupOptions(options);
        startup.requireSupported();
        return !startup.isSnapshotOnly();
    }

    @Override
    public void validateDatasource(String datasourceType) {
        if (!"MYSQL".equals(datasourceType)) {
            throw new IllegalArgumentException("MySQL CDC requires a MySQL datasource");
        }
    }

    @Override
    public void validateSchema(TableSchema schema) {
        ChunkUtils.requireSupportedSnapshot(schema, null);
    }

    @Override
    public void validateSchema(ReadonlyConfig options, TableSchema schema) {
        StartupOptions startup = startupOptions(options);
        startup.requireSupported();
        io.yak.ops.connector.cdc.mysql.source.config.MySqlTableOptions tableOptions =
                io.yak.ops.connector.cdc.mysql.source.config.MySqlTableOptions.from(options);
        java.util.Set<String> columns =
                schema.columns().stream().map(Column::name).collect(java.util.stream.Collectors.toSet());
        if (!columns.containsAll(io.yak.ops.connector.jdbc.database.query.JdbcReadQuery.referencedColumns(
                tableOptions.snapshotWhere()))) {
            throw new IllegalArgumentException("Snapshot condition refers to an unknown source column");
        }
        if (startup.isStreamOnly() && (tableOptions.skipSnapshot() || tableOptions.snapshotWhere() != null)) {
            throw new IllegalArgumentException("Snapshot table options require an initial snapshot phase");
        }
        if (options.get(MySqlSourceOptions.SCAN_NEWLY_ADDED_TABLE_ENABLED)
                && (startup.isStreamOnly() || startup.isSnapshotOnly())) {
            throw new IllegalArgumentException("Dynamic table discovery requires INITIAL startup");
        }
        String chunkKey = options.get(MySqlSourceOptions.SCAN_INCREMENTAL_SNAPSHOT_CHUNK_KEY_COLUMN);
        if (!startup.isStreamOnly()) {
            ChunkUtils.requireSupportedSnapshot(schema, chunkKey);
            return;
        }
        if (chunkKey != null) {
            throw new IllegalArgumentException("Snapshot Chunk Key is not supported for stream-only startup");
        }
        if (schema.primaryKeys().isEmpty()) {
            throw new IllegalArgumentException("Stream-only MySQL CDC requires a physical primary key");
        }
        for (Column column : schema.columns()) {
            if (!column.dataType().isResolved()) {
                throw new IllegalArgumentException("MySQL CDC requires resolved source column types");
            }
        }
    }

    @Override
    public java.util.Set<io.yak.ops.core.factories.ConnectorFeature> supportedFeatures() {
        return java.util.Set.of(
                io.yak.ops.core.factories.ConnectorFeature.SNAPSHOT_FILTER,
                io.yak.ops.core.factories.ConnectorFeature.SNAPSHOT_SKIP,
                io.yak.ops.core.factories.ConnectorFeature.DML_FILTER,
                io.yak.ops.core.factories.ConnectorFeature.DYNAMIC_TABLES);
    }

    @Override
    public DataSource createDataSource(Context context) {
        if (!(context instanceof SourceFactoryContext resolved)) {
            throw new IllegalArgumentException("MySQL source requires resolved, authorized table metadata");
        }
        return new TableDataSource(createSource(resolved.tables()), resolved.schemas());
    }

    private Source<TableRecord, ?, ?> createSource(List<TableSourceFactoryContext> contexts) {
        if (contexts.isEmpty()) {
            throw new IllegalArgumentException("MySQL CDC requires table mappings");
        }
        TableSourceFactoryContext first = contexts.getFirst();
        ReadonlyConfig options = first.options();
        StartupOptions startupOptions = startupOptions(options);
        startupOptions.requireSupported();
        if (options.get(JdbcConnectionOptions.SSH_ENABLED)) {
            throw new UnsupportedOperationException("MySQL CDC requires a direct MySQL connection");
        }
        Map<String, Object> common = new LinkedHashMap<>(options.getSourceMap());
        common.remove(MySqlSourceOptions.SCAN_INCREMENTAL_SNAPSHOT_CHUNK_KEY_COLUMN.key());
        removeTableOptions(common);
        for (TableSourceFactoryContext context : contexts) {
            FactoryUtil.validateCreation(context.options(), this);
            validateSchema(context.options(), context.schema());
            Map<String, Object> selected = new LinkedHashMap<>(context.options().getSourceMap());
            selected.remove(MySqlSourceOptions.SCAN_INCREMENTAL_SNAPSHOT_CHUNK_KEY_COLUMN.key());
            removeTableOptions(selected);
            if (!common.equals(selected) || context.metrics() != first.metrics()) {
                throw new IllegalArgumentException(
                        "MySQL CDC tables must share connection, replication and observation settings");
            }
        }
        JdbcConnectionOptions connection = new JdbcConnectionOptions(options);
        if (!connection.url().startsWith("jdbc:mysql://")) {
            throw new IllegalArgumentException("MySQL CDC requires a MySQL connection URL");
        }
        if (connection.host() == null) {
            throw new UnsupportedOperationException("MySQL CDC requires a direct MySQL connection");
        }
        MySqlSourceConfigFactory builder = MySqlSource.builder()
                .hostname(connection.host())
                .port(connection.port())
                .username(connection.username())
                .password(connection.password())
                .connectionProperties(mergedConnectionProperties(connection))
                .serverId(options.get(MySqlSourceOptions.SERVER_ID))
                .topicPrefix(options.get(MySqlSourceOptions.TOPIC_PREFIX))
                .queueCapacity(options.get(MySqlSourceOptions.QUEUE_CAPACITY));
        if (options.get(MySqlSourceOptions.SCAN_NEWLY_ADDED_TABLE_ENABLED)) {
            builder.scanNewlyAddedTableEnabled(true)
                    .databaseList(options.get(MySqlSourceOptions.DATABASE_NAME))
                    .discoveryTablePattern(options.get(MySqlSourceOptions.TABLE_NAME));
            String defaults = options.get(MySqlSourceOptions.DISCOVERY_DEFAULTS);
            if (defaults == null)
                throw new IllegalArgumentException("Dynamic discovery requires explicit table defaults");
            try {
                Map<String, Object> values = new com.fasterxml.jackson.databind.ObjectMapper()
                        .readValue(
                                defaults, new com.fasterxml.jackson.core.type.TypeReference<Map<String, Object>>() {});
                java.util.Set<String> supported = java.util.Set.of(
                        "snapshot.skip",
                        "snapshot.where",
                        "dml.insert",
                        "dml.update",
                        "dml.delete",
                        MySqlSourceOptions.SCAN_INCREMENTAL_SNAPSHOT_CHUNK_KEY_COLUMN.key());
                if (!supported.containsAll(values.keySet()))
                    throw new IllegalArgumentException("Unsupported discovery defaults");
                ReadonlyConfig config = ReadonlyConfig.fromMap(values);
                builder.defaultTableOptions(io.yak.ops.connector.cdc.mysql.source.config.MySqlTableOptions.from(config))
                        .defaultChunkKeyColumn(
                                config.get(MySqlSourceOptions.SCAN_INCREMENTAL_SNAPSHOT_CHUNK_KEY_COLUMN));
            } catch (com.fasterxml.jackson.core.JsonProcessingException failure) {
                throw new IllegalArgumentException("Invalid discovery defaults", failure);
            }
        }
        for (TableSourceFactoryContext context : contexts) {
            builder.table(
                    context.table(),
                    context.schema(),
                    context.options().get(MySqlSourceOptions.SCAN_INCREMENTAL_SNAPSHOT_CHUNK_KEY_COLUMN));
            builder.tableOptions(
                    context.table(),
                    io.yak.ops.connector.cdc.mysql.source.config.MySqlTableOptions.from(context.options()));
        }
        return MySqlSource.create(
                builder,
                startupOptions,
                options.get(MySqlSourceOptions.SCAN_INCREMENTAL_SNAPSHOT_CHUNK_SIZE),
                first.metrics());
    }

    private static void removeTableOptions(Map<String, Object> options) {
        for (String key :
                java.util.List.of("snapshot.skip", "snapshot.where", "dml.insert", "dml.update", "dml.delete")) {
            options.remove(key);
        }
    }

    private static StartupOptions startupOptions(ReadonlyConfig options) {
        return StartupOptions.from(
                options.get(MySqlSourceOptions.SCAN_STARTUP_MODE),
                options.get(MySqlSourceOptions.SCAN_STARTUP_SPECIFIC_OFFSET_FILE),
                options.get(MySqlSourceOptions.SCAN_STARTUP_SPECIFIC_OFFSET_POS),
                options.get(MySqlSourceOptions.SCAN_STARTUP_SPECIFIC_OFFSET_GTID_SET),
                options.get(MySqlSourceOptions.SCAN_STARTUP_TIMESTAMP_MILLIS),
                options.get(MySqlSourceOptions.SCAN_STARTUP_SPECIFIC_OFFSET_SKIP_EVENTS),
                options.get(MySqlSourceOptions.SCAN_STARTUP_SPECIFIC_OFFSET_SKIP_ROWS));
    }

    /**
     * A MySQL URL can contain driver settings as well as connection.properties.
     * The URL takes precedence over the same property in connection.properties.
     */
    private static Map<String, String> mergedConnectionProperties(JdbcConnectionOptions connection) {
        Map<String, String> properties =
                new LinkedHashMap<>(connection.configuration().get(JdbcConnectionOptions.PROPERTIES));
        String query = URI.create(connection.url().substring("jdbc:".length())).getRawQuery();
        if (query == null || query.isBlank()) return properties;
        for (String part : query.split("&")) {
            if (part.isBlank()) continue;
            int equal = part.indexOf('=');
            String key = URLDecoder.decode(equal < 0 ? part : part.substring(0, equal), StandardCharsets.UTF_8);
            String value = URLDecoder.decode(equal < 0 ? "" : part.substring(equal + 1), StandardCharsets.UTF_8);
            if (key.isBlank()) throw new IllegalArgumentException("MySQL JDBC URL contains a blank property name");
            properties.keySet().removeIf(existing -> existing.equalsIgnoreCase(key));
            properties.put(key, value);
        }
        return properties;
    }
}
