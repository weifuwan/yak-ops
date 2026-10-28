package io.yak.ops.connector.cdc.mysql.table;

import com.google.auto.service.AutoService;
import io.yak.ops.configuration.ReadonlyConfig;
import io.yak.ops.configuration.rule.Conditions;
import io.yak.ops.configuration.rule.OptionRule;
import io.yak.ops.connector.base.factory.FactoryUtil;
import io.yak.ops.connector.base.factory.TableSourceFactory;
import io.yak.ops.connector.base.factory.TableSourceFactoryContext;
import io.yak.ops.connector.cdc.mysql.source.MySqlSource;
import io.yak.ops.connector.cdc.mysql.source.config.MySqlSourceOptions;
import io.yak.ops.connector.cdc.mysql.source.utils.ChunkUtils;
import io.yak.ops.connector.jdbc.JdbcConnectionOptions;
import io.yak.ops.core.api.connector.source.Boundedness;
import io.yak.ops.core.api.connector.source.Source;
import io.yak.ops.core.data.TableRecord;
import io.yak.ops.core.types.TableSchema;
import java.net.URI;
import java.net.URLDecoder;
import java.nio.charset.StandardCharsets;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

/** Creates an initial Snapshot + Binlog Source or a latest-position Binlog Source. */
@AutoService(TableSourceFactory.class)
public final class MySqlCdcSourceFactory implements TableSourceFactory {

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
    public String factoryIdentifier() {
        return "MYSQL_CDC";
    }

    @Override
    public OptionRule optionRule() {
        return OptionRule.builder()
                .optional(
                        MySqlSourceOptions.SCAN_INCREMENTAL_SNAPSHOT_CHUNK_KEY_COLUMN,
                        Conditions.notBlank(MySqlSourceOptions.SCAN_INCREMENTAL_SNAPSHOT_CHUNK_KEY_COLUMN))
                .optional(MySqlSourceOptions.SCAN_STARTUP_MODE)
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
                .optional(
                        MySqlSourceOptions.SCAN_SNAPSHOT_FETCH_SIZE,
                        MySqlSourceOptions.CONNECT_TIMEOUT,
                        MySqlSourceOptions.HEARTBEAT_INTERVAL,
                        MySqlSourceOptions.CHUNK_META_GROUP_SIZE,
                        MySqlSourceOptions.CHUNK_KEY_EVEN_DISTRIBUTION_FACTOR_LOWER_BOUND,
                        MySqlSourceOptions.CHUNK_KEY_EVEN_DISTRIBUTION_FACTOR_UPPER_BOUND,
                        MySqlSourceOptions.SCAN_NEWLY_ADDED_TABLE_ENABLED,
                        MySqlSourceOptions.SCAN_INCREMENTAL_SNAPSHOT_METADATA_RELEASE_ENABLED,
                        MySqlSourceOptions.SCAN_INCREMENTAL_SNAPSHOT_BACKFILL_SKIP)
                .required(MySqlSourceOptions.SERVER_ID, Conditions.notBlank(MySqlSourceOptions.SERVER_ID))
                .required(MySqlSourceOptions.TOPIC_PREFIX, Conditions.notBlank(MySqlSourceOptions.TOPIC_PREFIX))
                .build();
    }

    @Override
    public Boundedness boundedness() {
        return Boundedness.CONTINUOUS_UNBOUNDED;
    }

    @Override
    public boolean producesChangelog() {
        return true;
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
        for (var column : schema.columns()) {
            if (!column.dataType().isResolved()) {
                throw new IllegalArgumentException("MySQL CDC requires resolved source column types");
            }
        }
    }

    @Override
    public Source<TableRecord, ?, ?> createSource(TableSourceFactoryContext context) {
        return createSource(List.of(context));
    }

    @Override
    public Source<TableRecord, ?, ?> createSource(List<TableSourceFactoryContext> contexts) {
        if (contexts.isEmpty()) {
            throw new IllegalArgumentException("MySQL CDC requires table mappings");
        }
        var first = contexts.getFirst();
        ReadonlyConfig options = first.options();
        StartupOptions startupOptions = startupOptions(options);
        startupOptions.requireSupported();
        if (startupOptions.isStreamOnly()) {
            for (var snapshotSetting : List.of(
                    MySqlSourceOptions.SCAN_INCREMENTAL_SNAPSHOT_CHUNK_KEY_COLUMN,
                    MySqlSourceOptions.SCAN_INCREMENTAL_SNAPSHOT_CHUNK_SIZE,
                    MySqlSourceOptions.SCAN_SNAPSHOT_FETCH_SIZE,
                    MySqlSourceOptions.CHUNK_META_GROUP_SIZE,
                    MySqlSourceOptions.CHUNK_KEY_EVEN_DISTRIBUTION_FACTOR_LOWER_BOUND,
                    MySqlSourceOptions.CHUNK_KEY_EVEN_DISTRIBUTION_FACTOR_UPPER_BOUND,
                    MySqlSourceOptions.SCAN_NEWLY_ADDED_TABLE_ENABLED,
                    MySqlSourceOptions.SCAN_INCREMENTAL_SNAPSHOT_METADATA_RELEASE_ENABLED,
                    MySqlSourceOptions.SCAN_INCREMENTAL_SNAPSHOT_BACKFILL_SKIP)) {
                if (options.getSourceMap().containsKey(snapshotSetting.key())) {
                    throw new IllegalArgumentException(
                            "MySQL stream-only startup cannot use snapshot setting: " + snapshotSetting.key());
                }
            }
        }
        if (options.get(JdbcConnectionOptions.SSH_ENABLED)) {
            throw new UnsupportedOperationException("MySQL CDC requires a direct MySQL connection");
        }
        Map<String, Object> common = new LinkedHashMap<>(options.getSourceMap());
        common.remove(MySqlSourceOptions.SCAN_INCREMENTAL_SNAPSHOT_CHUNK_KEY_COLUMN.key());
        for (var context : contexts) {
            FactoryUtil.validateCreation(context.options(), this);
            validateSchema(context.options(), context.schema());
            Map<String, Object> selected = new LinkedHashMap<>(context.options().getSourceMap());
            selected.remove(MySqlSourceOptions.SCAN_INCREMENTAL_SNAPSHOT_CHUNK_KEY_COLUMN.key());
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
        var builder = MySqlSource.builder()
                .hostname(connection.host())
                .port(connection.port())
                .username(connection.username())
                .password(connection.password())
                .connectionProperties(mergedConnectionProperties(connection))
                .serverId(options.get(MySqlSourceOptions.SERVER_ID))
                .topicPrefix(options.get(MySqlSourceOptions.TOPIC_PREFIX))
                .queueCapacity(options.get(MySqlSourceOptions.QUEUE_CAPACITY))
                .fetchSize(options.get(MySqlSourceOptions.SCAN_SNAPSHOT_FETCH_SIZE))
                .connectTimeout(options.get(MySqlSourceOptions.CONNECT_TIMEOUT))
                .heartbeatInterval(options.get(MySqlSourceOptions.HEARTBEAT_INTERVAL))
                .splitMetaGroupSize(options.get(MySqlSourceOptions.CHUNK_META_GROUP_SIZE))
                .distributionFactorLower(options.get(MySqlSourceOptions.CHUNK_KEY_EVEN_DISTRIBUTION_FACTOR_LOWER_BOUND))
                .distributionFactorUpper(options.get(MySqlSourceOptions.CHUNK_KEY_EVEN_DISTRIBUTION_FACTOR_UPPER_BOUND))
                .scanNewlyAddedTableEnabled(options.get(MySqlSourceOptions.SCAN_NEWLY_ADDED_TABLE_ENABLED))
                .releaseSnapshotMetadataEnabled(
                        options.get(MySqlSourceOptions.SCAN_INCREMENTAL_SNAPSHOT_METADATA_RELEASE_ENABLED))
                .skipSnapshotBackfill(options.get(MySqlSourceOptions.SCAN_INCREMENTAL_SNAPSHOT_BACKFILL_SKIP));
        for (var context : contexts) {
            builder.table(
                    context.table(),
                    context.schema(),
                    context.options().get(MySqlSourceOptions.SCAN_INCREMENTAL_SNAPSHOT_CHUNK_KEY_COLUMN));
        }
        return MySqlSource.create(
                builder,
                startupOptions,
                options.get(MySqlSourceOptions.SCAN_INCREMENTAL_SNAPSHOT_CHUNK_SIZE),
                first.metrics());
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
