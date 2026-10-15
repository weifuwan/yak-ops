package io.yak.ops.connector.jdbc.sink;

import com.google.auto.service.AutoService;
import io.yak.ops.configuration.ReadonlyConfig;
import io.yak.ops.configuration.rule.OptionRule;
import io.yak.ops.connector.base.factory.FactoryUtil;
import io.yak.ops.connector.base.factory.TableSinkFactory;
import io.yak.ops.connector.base.factory.TableSinkFactoryContext;
import io.yak.ops.connector.jdbc.JdbcConnectionOptions;
import io.yak.ops.connector.jdbc.database.JdbcFactoryLoader;
import io.yak.ops.connector.jdbc.database.connection.DefaultJdbcConnectionProvider;
import io.yak.ops.core.api.connector.sink.Sink;
import io.yak.ops.core.data.TableRecord;
import io.yak.ops.core.types.TableSchema;
import java.util.List;
import java.util.Map;

/** Declares JDBC write options and builds schema-aware JDBC Sink definitions. */
@AutoService(TableSinkFactory.class)
public final class JdbcSinkFactory implements TableSinkFactory {

    @Override
    public String factoryIdentifier() {
        return "JDBC";
    }

    @Override
    public OptionRule optionRule() {
        return OptionRule.builder().required(JdbcSinkOptions.WRITE_MODE).build();
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
            throw new IllegalArgumentException("JDBC Sink supports APPEND or UPSERT only");
        }
        if (sourceProducesChangelog && mode != JdbcWriteMode.UPSERT) {
            throw new IllegalArgumentException("Changelog Source requires UPSERT");
        }
    }

    @Override
    public void validateSchema(
            ReadonlyConfig options, TableSchema source, TableSchema target, boolean sourceProducesChangelog) {
        validate(options, sourceProducesChangelog);
        JdbcSinkSchemaValidator.validate(
                source,
                target,
                sourceProducesChangelog || options.get(JdbcSinkOptions.WRITE_MODE) == JdbcWriteMode.UPSERT);
    }

    @Override
    public JdbcMetadataApplier createMetadataApplier(ReadonlyConfig options) {
        FactoryUtil.validateCreation(options, this);
        JdbcConnectionOptions connection = new JdbcConnectionOptions(options);
        String url = connection.url();
        if (!url.startsWith("jdbc:mysql:") && !url.startsWith("jdbc:postgresql:")) {
            throw new UnsupportedOperationException("JDBC schema preparation supports MySQL and PostgreSQL targets");
        }
        var connections = new DefaultJdbcConnectionProvider(connection);
        return new JdbcMetadataApplier(
                connections,
                JdbcFactoryLoader.loadDialect(url),
                JdbcFactoryLoader.loadCatalog(url, connections),
                JdbcMetadataApplier.DEFAULT_STATEMENT_TIMEOUT_SECONDS);
    }

    @Override
    public Sink<TableRecord> createSink(TableSinkFactoryContext context) {
        return createSink(List.of(context));
    }

    @Override
    public Sink<TableRecord> createSink(List<TableSinkFactoryContext> contexts) {
        if (contexts.isEmpty()) throw new IllegalArgumentException("Sink needs table routes");
        var first = contexts.getFirst();
        JdbcConnectionOptions connection = new JdbcConnectionOptions(first.options());
        List<JdbcTableWritePlan> plans = new java.util.ArrayList<>();
        var common = new java.util.LinkedHashMap<>(first.options().getSourceMap());
        common.remove(JdbcSinkOptions.WRITE_MODE.key());
        for (var context : contexts) {
            FactoryUtil.validateCreation(context.options(), this);
            var configured = new java.util.LinkedHashMap<>(context.options().getSourceMap());
            configured.remove(JdbcSinkOptions.WRITE_MODE.key());
            if (!common.equals(configured))
                throw new IllegalArgumentException("JDBC routes must share connection settings");
            validate(context.options(), false);
            plans.add(new JdbcTableWritePlan(
                    context.sourceTable(),
                    context.targetTable(),
                    context.targetSchema(),
                    context.options().get(JdbcSinkOptions.WRITE_MODE),
                    context.sourceSchema(),
                    Map.of()));
        }
        return JdbcSink.builder()
                .withConnectionProvider(new DefaultJdbcConnectionProvider(connection))
                .withDialect(JdbcFactoryLoader.loadDialect(connection.url()))
                .withTablePlans(plans)
                .withBatchOrder(first.options().get(JdbcSinkOptions.BATCH_ORDER))
                .withStatementCacheSize(first.options().get(JdbcSinkOptions.STATEMENT_CACHE_SIZE))
                .withTableMetrics(first.metrics())
                .build();
    }
}
