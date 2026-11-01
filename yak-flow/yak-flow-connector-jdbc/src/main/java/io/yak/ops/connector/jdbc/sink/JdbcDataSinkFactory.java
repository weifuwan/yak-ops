package io.yak.ops.connector.jdbc.sink;

import io.yak.ops.configuration.ReadonlyConfig;
import io.yak.ops.configuration.rule.OptionRule;
import io.yak.ops.connector.base.factory.FactoryUtil;
import io.yak.ops.connector.base.factory.SinkFactoryContext;
import io.yak.ops.connector.base.factory.TableSinkFactoryContext;
import io.yak.ops.connector.base.sink.TableDataSink;
import io.yak.ops.connector.jdbc.JdbcConnectionOptions;
import io.yak.ops.connector.jdbc.database.JdbcFactoryLoader;
import io.yak.ops.connector.jdbc.database.connection.DefaultJdbcConnectionProvider;
import io.yak.ops.connector.jdbc.database.dialect.JdbcDialect;
import io.yak.ops.connector.jdbc.xa.MySqlXaSink;
import io.yak.ops.core.api.connector.sink.Sink;
import io.yak.ops.core.data.TableRecord;
import io.yak.ops.core.factories.ConnectorFeature;
import io.yak.ops.core.factories.DataSinkFactory;
import io.yak.ops.core.sink.DataSink;
import io.yak.ops.core.types.TableSchema;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Set;

/** Declares JDBC write options and builds schema-aware JDBC Sink definitions. */
public abstract class JdbcDataSinkFactory implements DataSinkFactory {
    private final String identifier;
    private final String datasourceType;
    private final String urlPrefix;

    protected JdbcDataSinkFactory(String identifier, String datasourceType, String urlPrefix) {
        this.identifier = identifier;
        this.datasourceType = datasourceType;
        this.urlPrefix = urlPrefix;
    }

    @Override
    public String datasourceType() {
        return datasourceType;
    }

    @Override
    public boolean supportsSchemaChanges() {
        return !"ORACLE".equals(datasourceType);
    }

    @Override
    public boolean supportsRetract() {
        return "MYSQL".equals(datasourceType);
    }

    @Override
    public String identifier() {
        return identifier;
    }

    @Override
    public OptionRule optionRule() {
        return OptionRule.builder()
                .required(JdbcSinkOptions.WRITE_MODE)
                .optional(JdbcSinkOptions.XA_ENABLED, JdbcSinkOptions.XA_INSTANCE_ID)
                .optional(JdbcSinkOptions.PRE_SQL, JdbcSinkOptions.POST_SQL, JdbcSinkOptions.BATCH_SIZE)
                .build();
    }

    @Override
    public Set<ConnectorFeature> supportedFeatures() {
        return "MYSQL".equals(datasourceType)
                ? Set.of(ConnectorFeature.SQL_HOOKS, ConnectorFeature.REPLACE_WRITE, ConnectorFeature.TYPE_MAPPING)
                : Set.of(ConnectorFeature.SQL_HOOKS, ConnectorFeature.TYPE_MAPPING);
    }

    @Override
    public OptionRule creationOptionRule() {
        return OptionRule.builder()
                .include(optionRule())
                .include(JdbcConnectionOptions.RULE)
                .optional(JdbcSinkOptions.BATCH_ORDER, JdbcSinkOptions.STATEMENT_CACHE_SIZE)
                .build();
    }

    @Override
    public void validate(ReadonlyConfig options, boolean sourceProducesChangelog) {
        JdbcWriteMode mode = options.get(JdbcSinkOptions.WRITE_MODE);
        if (mode == null) {
            throw new IllegalArgumentException("JDBC Sink requires a write mode");
        }
        JdbcWriteOptions write = JdbcWriteOptions.from(options);
        if (mode == JdbcWriteMode.REPLACE && (!"MYSQL".equals(datasourceType) || sourceProducesChangelog)) {
            throw new IllegalArgumentException("Native REPLACE requires bounded MySQL writes");
        }
        if (write.hasSqlHooks() && (sourceProducesChangelog || options.get(JdbcSinkOptions.XA_ENABLED))) {
            throw new IllegalArgumentException("SQL hooks require bounded non-XA writes");
        }
        if (mode == JdbcWriteMode.RETRACT && options.getSourceMap().containsKey(JdbcSinkOptions.BATCH_SIZE.key())) {
            throw new IllegalArgumentException("Per-table batch sizing is unavailable for checkpoint XA writes");
        }
        if (mode == JdbcWriteMode.RETRACT && !supportsRetract()) {
            throw new UnsupportedOperationException("This JDBC sink does not support exact-row RETRACT");
        }
        if (mode == JdbcWriteMode.RETRACT && !options.get(JdbcSinkOptions.XA_ENABLED)) {
            throw new UnsupportedOperationException("Exact-row RETRACT requires checkpoint-coordinated MySQL XA");
        }
        if (options.get(JdbcSinkOptions.XA_ENABLED) && mode != JdbcWriteMode.RETRACT) {
            throw new IllegalArgumentException("JDBC XA product admission currently requires RETRACT");
        }
        if (sourceProducesChangelog && mode != JdbcWriteMode.UPSERT && mode != JdbcWriteMode.RETRACT) {
            throw new IllegalArgumentException("Changelog Source requires UPSERT or exact-row RETRACT");
        }
    }

    @Override
    public void validateSchema(
            ReadonlyConfig options, TableSchema source, TableSchema target, boolean sourceProducesChangelog) {
        validate(options, sourceProducesChangelog);
        JdbcSinkSchemaValidator.validate(source, target, options.get(JdbcSinkOptions.WRITE_MODE));
    }

    @Override
    public JdbcMetadataApplier createMetadataApplier(ReadonlyConfig options) {
        FactoryUtil.validateCreation(options, this);
        JdbcConnectionOptions connection = new JdbcConnectionOptions(options);
        String url = connection.url();
        if (!url.startsWith(urlPrefix)) {
            throw new IllegalArgumentException("JDBC sink URL does not match its connector identifier");
        }
        if (!url.startsWith("jdbc:mysql:") && !url.startsWith("jdbc:postgresql:")) {
            throw new UnsupportedOperationException("JDBC schema preparation supports MySQL and PostgreSQL targets");
        }
        DefaultJdbcConnectionProvider connections = new DefaultJdbcConnectionProvider(connection);
        return new JdbcMetadataApplier(
                connections,
                JdbcFactoryLoader.loadDialect(url),
                JdbcFactoryLoader.loadCatalog(url, connections),
                JdbcMetadataApplier.DEFAULT_STATEMENT_TIMEOUT_SECONDS);
    }

    @Override
    public DataSink createDataSink(Context context) {
        if (!(context instanceof SinkFactoryContext resolved)) {
            throw new IllegalArgumentException("JDBC sink requires resolved, authorized table metadata");
        }
        return new TableDataSink(
                createSink(
                        resolved.tables(),
                        resolved.sourceProducesChangelog(),
                        resolved.isSourceMutationCapabilityKnown()),
                resolved.tables(),
                () -> createMetadataApplier(context.getFactoryConfiguration()),
                resolved.sourceSchemaTransform(),
                resolved.sourceSchemas(),
                new JdbcDataChangeEventHashFunctionProvider(resolved.sourceProducesChangelog()));
    }

    private Sink<TableRecord> createSink(
            List<TableSinkFactoryContext> contexts, boolean sourceProducesChangelog, boolean sourceCapabilityKnown) {
        if (contexts.isEmpty()) throw new IllegalArgumentException("Sink needs table routes");
        TableSinkFactoryContext first = contexts.getFirst();
        JdbcConnectionOptions connection = new JdbcConnectionOptions(first.options());
        List<JdbcTableWritePlan> plans = new java.util.ArrayList<>();
        Map<io.yak.ops.core.data.TableId, JdbcWriteOptions> writes = new LinkedHashMap<>();
        Map<String, Object> common =
                new java.util.LinkedHashMap<>(first.options().getSourceMap());
        common.remove(JdbcSinkOptions.WRITE_MODE.key());
        common.remove(JdbcSinkOptions.PRE_SQL.key());
        common.remove(JdbcSinkOptions.POST_SQL.key());
        common.remove(JdbcSinkOptions.BATCH_SIZE.key());
        for (TableSinkFactoryContext context : contexts) {
            FactoryUtil.validateCreation(context.options(), this);
            Map<String, Object> configured =
                    new java.util.LinkedHashMap<>(context.options().getSourceMap());
            configured.remove(JdbcSinkOptions.WRITE_MODE.key());
            configured.remove(JdbcSinkOptions.PRE_SQL.key());
            configured.remove(JdbcSinkOptions.POST_SQL.key());
            configured.remove(JdbcSinkOptions.BATCH_SIZE.key());
            if (!common.equals(configured))
                throw new IllegalArgumentException("JDBC routes must share connection settings");
            validateSchema(
                    context.options(),
                    context.sourceSchema(),
                    context.targetSchema(),
                    sourceCapabilityKnown && sourceProducesChangelog);
            JdbcWriteOptions write = JdbcWriteOptions.from(context.options());
            if (write.hasSqlHooks() && !sourceCapabilityKnown) {
                throw new IllegalArgumentException("SQL hooks require an explicitly append-only source capability");
            }
            writes.put(context.sourceTable(), write);
            plans.add(new JdbcTableWritePlan(
                    context.sourceTable(),
                    context.targetTable(),
                    context.targetSchema(),
                    context.options().get(JdbcSinkOptions.WRITE_MODE),
                    context.sourceSchema(),
                    Map.of()));
        }
        String url = connection.url();
        if (!url.startsWith(urlPrefix)) {
            throw new IllegalArgumentException("JDBC sink URL does not match its connector identifier");
        }
        DefaultJdbcConnectionProvider connections = new DefaultJdbcConnectionProvider(connection);
        JdbcDialect dialect = JdbcFactoryLoader.loadDialect(url);
        if (first.options().get(JdbcSinkOptions.XA_ENABLED)) {
            if (contexts.size() != 1) {
                throw new UnsupportedOperationException("MySQL XA RETRACT execution requires one table route");
            }
            String instanceId = first.options().get(JdbcSinkOptions.XA_INSTANCE_ID);
            if (instanceId == null) {
                throw new IllegalArgumentException("MySQL XA requires a trusted task instance identity");
            }
            return new MySqlXaSink(connection, dialect, plans.getFirst(), instanceId);
        }
        return JdbcSink.builder()
                .withConnectionProvider(connections)
                .withDialect(dialect)
                .withMetadataApplierFactory(() -> new JdbcMetadataApplier(
                        connections,
                        dialect,
                        JdbcFactoryLoader.loadCatalog(url, connections),
                        JdbcMetadataApplier.DEFAULT_STATEMENT_TIMEOUT_SECONDS))
                .withTablePlans(plans)
                .withTableWriteOptions(writes)
                .withBatchFlushPolicy(new io.yak.ops.connector.base.sink.writer.BatchFlushPolicy(
                        writes.values().stream()
                                .mapToInt(JdbcWriteOptions::batchSize)
                                .max()
                                .orElse(500),
                        java.time.Duration.ZERO))
                .withBatchOrder(first.options().get(JdbcSinkOptions.BATCH_ORDER))
                .withStatementCacheSize(first.options().get(JdbcSinkOptions.STATEMENT_CACHE_SIZE))
                .withTableMetrics(first.metrics())
                .build();
    }
}
