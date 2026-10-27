package io.yak.ops.connector.cdc.mysql.source;

import com.google.auto.service.AutoService;
import io.yak.ops.configuration.ReadonlyConfig;
import io.yak.ops.configuration.rule.Conditions;
import io.yak.ops.configuration.rule.OptionRule;
import io.yak.ops.connector.base.factory.FactoryUtil;
import io.yak.ops.connector.base.factory.TableSourceFactory;
import io.yak.ops.connector.base.factory.TableSourceFactoryContext;
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
    @Override
    public String factoryIdentifier() {
        return "MYSQL_CDC";
    }

    @Override
    public OptionRule optionRule() {
        return OptionRule.builder()
                .optional(
                        MySqlCdcSourceOptions.CHUNK_KEY_COLUMN,
                        Conditions.notBlank(MySqlCdcSourceOptions.CHUNK_KEY_COLUMN))
                .optional(MySqlCdcSourceOptions.STARTUP_MODE)
                .build();
    }

    @Override
    public OptionRule creationOptionRule() {
        return OptionRule.builder()
                .include(optionRule())
                .include(JdbcConnectionOptions.RULE)
                .include(MySqlCdcSourceOptions.PHYSICAL_RULE)
                .required(
                        MySqlCdcSourceOptions.SERVER_ID, Conditions.greaterOrEqual(MySqlCdcSourceOptions.SERVER_ID, 1))
                .required(MySqlCdcSourceOptions.TOPIC_PREFIX, Conditions.notBlank(MySqlCdcSourceOptions.TOPIC_PREFIX))
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
        StartupMode.from(options.get(MySqlCdcSourceOptions.STARTUP_MODE))
                .validateSchema(schema, options.get(MySqlCdcSourceOptions.CHUNK_KEY_COLUMN));
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
        StartupMode startupMode = StartupMode.from(options.get(MySqlCdcSourceOptions.STARTUP_MODE));
        if (options.get(JdbcConnectionOptions.SSH_ENABLED)) {
            throw new UnsupportedOperationException("MySQL CDC requires a direct MySQL connection");
        }
        Map<String, Object> common = new LinkedHashMap<>(options.getSourceMap());
        common.remove(MySqlCdcSourceOptions.CHUNK_KEY_COLUMN.key());
        for (var context : contexts) {
            FactoryUtil.validateCreation(context.options(), this);
            validateSchema(context.options(), context.schema());
            Map<String, Object> selected = new LinkedHashMap<>(context.options().getSourceMap());
            selected.remove(MySqlCdcSourceOptions.CHUNK_KEY_COLUMN.key());
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
                .serverId(options.get(MySqlCdcSourceOptions.SERVER_ID))
                .topicPrefix(options.get(MySqlCdcSourceOptions.TOPIC_PREFIX))
                .queueCapacity(options.get(MySqlCdcSourceOptions.QUEUE_CAPACITY));
        for (var context : contexts) {
            builder.table(
                    context.table(), context.schema(), context.options().get(MySqlCdcSourceOptions.CHUNK_KEY_COLUMN));
        }
        return MySqlSource.create(
                builder, startupMode, options.get(MySqlCdcSourceOptions.SNAPSHOT_CHUNK_SIZE), first.metrics());
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
