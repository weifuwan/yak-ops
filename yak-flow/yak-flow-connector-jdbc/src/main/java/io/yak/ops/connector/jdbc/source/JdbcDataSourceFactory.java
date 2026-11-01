package io.yak.ops.connector.jdbc.source;

import io.yak.ops.configuration.ReadonlyConfig;
import io.yak.ops.configuration.rule.OptionRule;
import io.yak.ops.connector.base.factory.FactoryUtil;
import io.yak.ops.connector.base.factory.SourceFactoryContext;
import io.yak.ops.connector.base.factory.TableSourceFactoryContext;
import io.yak.ops.connector.base.source.TableDataSource;
import io.yak.ops.connector.jdbc.JdbcConnectionOptions;
import io.yak.ops.connector.jdbc.JdbcSourceOptions;
import io.yak.ops.core.api.connector.source.Boundedness;
import io.yak.ops.core.api.connector.source.Source;
import io.yak.ops.core.data.TableId;
import io.yak.ops.core.data.TableRecord;
import io.yak.ops.core.factories.ConnectorFeature;
import io.yak.ops.core.factories.DataSourceFactory;
import io.yak.ops.core.source.DataSource;
import io.yak.ops.core.types.Column;
import io.yak.ops.core.types.DecimalType;
import io.yak.ops.core.types.TableSchema;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Set;

/** Creates bounded JDBC Sources from effective connection and reader settings. */
public abstract class JdbcDataSourceFactory implements DataSourceFactory {
    private final String identifier;
    private final String datasourceType;
    private final String urlPrefix;

    protected JdbcDataSourceFactory(String identifier, String datasourceType, String urlPrefix) {
        this.identifier = identifier;
        this.datasourceType = datasourceType;
        this.urlPrefix = urlPrefix;
    }

    @Override
    public String datasourceType() {
        return datasourceType;
    }

    @Override
    public boolean supportsIncrementalBatch() {
        return !"ORACLE".equals(datasourceType);
    }

    @Override
    public boolean supportsSchemaMetadata() {
        return !"ORACLE".equals(datasourceType);
    }

    @Override
    public String identifier() {
        return identifier;
    }

    @Override
    public OptionRule optionRule() {
        return OptionRule.builder()
                .optional(
                        JdbcSourceOptions.READ_WHERE,
                        JdbcSourceOptions.SPLIT_KEY,
                        JdbcSourceOptions.RESULT_SET_FETCH_SIZE,
                        JdbcSourceOptions.QUERY_TIMEOUT_SECONDS)
                .build();
    }

    @Override
    public Set<ConnectorFeature> supportedFeatures() {
        return Set.of(ConnectorFeature.READ_PREDICATE, ConnectorFeature.EXPLICIT_SPLIT_KEY, ConnectorFeature.PREVIEW);
    }

    @Override
    public void validateSchema(ReadonlyConfig options, TableSchema schema) {
        JdbcReadOptions read = JdbcReadOptions.from(options);
        if (read.splitKey() == null) return;
        Column column = schema.columns().stream()
                .filter(candidate -> candidate.name().equals(read.splitKey()))
                .findFirst()
                .orElseThrow(() -> new IllegalArgumentException("JDBC split key column does not exist"));
        boolean supported =
                switch (column.dataType().getTypeRoot()) {
                    case TINYINT, SMALLINT, INTEGER, BIGINT -> true;
                    case DECIMAL -> ((DecimalType) column.dataType()).scale() == 0;
                    default -> false;
                };
        if (column.nullable() || !supported) {
            throw new IllegalArgumentException("JDBC split keys require a non-null integral numeric column");
        }
    }

    @Override
    public OptionRule creationOptionRule() {
        return OptionRule.builder()
                .include(optionRule())
                .include(JdbcConnectionOptions.RULE)
                .include(JdbcSourceOptions.RULE)
                .build();
    }

    @Override
    public Boundedness boundedness(ReadonlyConfig options) {
        return Boundedness.BOUNDED;
    }

    @Override
    public boolean producesChangelog() {
        return false;
    }

    @Override
    public DataSource createDataSource(Context context) {
        if (!(context instanceof SourceFactoryContext resolved)) {
            throw new IllegalArgumentException("JDBC source requires resolved, authorized table metadata");
        }
        return new TableDataSource(createSource(resolved.tables()), resolved.schemas());
    }

    private Source<TableRecord, ?, ?> createSource(List<TableSourceFactoryContext> contexts) {
        if (contexts.isEmpty()) throw new IllegalArgumentException("Source needs tables");
        TableSourceFactoryContext first = contexts.getFirst();
        ReadonlyConfig shared = sharedOptions(first.options());
        Map<TableId, JdbcIncrementalRange> ranges = new LinkedHashMap<>();
        Map<TableId, JdbcReadOptions> reads = new LinkedHashMap<>();
        for (TableSourceFactoryContext context : contexts) {
            FactoryUtil.validateCreation(context.options(), this);
            validateSchema(context.options(), context.schema());
            if (!sharedOptions(context.options()).equals(shared)) {
                throw new IllegalArgumentException("JDBC tables must share effective connection and fetch settings");
            }
            JdbcIncrementalRange range = JdbcSourceOptions.range(context.options());
            if (range != null && ranges.putIfAbsent(context.table(), range) != null) {
                throw new IllegalArgumentException("Duplicate incremental table range");
            }
            if (reads.putIfAbsent(context.table(), JdbcReadOptions.from(context.options())) != null) {
                throw new IllegalArgumentException("Duplicate JDBC source table");
            }
        }
        JdbcConnectionOptions connection = new JdbcConnectionOptions(first.options());
        if (!connection.url().startsWith(urlPrefix)) {
            throw new IllegalArgumentException("JDBC source URL does not match its connector identifier");
        }
        return new JdbcSource(
                new io.yak.ops.connector.jdbc.database.connection.DefaultJdbcConnectionProvider(connection),
                connection.url(),
                contexts.stream().map(TableSourceFactoryContext::table).toList(),
                JdbcSourceOptions.configuration(first.options()),
                java.util.Map.of(),
                first.metrics(),
                ranges,
                reads);
    }

    private static io.yak.ops.configuration.ReadonlyConfig sharedOptions(
            io.yak.ops.configuration.ReadonlyConfig options) {
        Map<String, Object> values = new java.util.LinkedHashMap<>(options.getSourceMap());
        values.remove(JdbcSourceOptions.RANGE_MODE.key());
        values.remove(JdbcSourceOptions.RANGE_COLUMN.key());
        values.remove(JdbcSourceOptions.RANGE_LOWER.key());
        values.remove(JdbcSourceOptions.RANGE_UPPER.key());
        values.remove(JdbcSourceOptions.READ_WHERE.key());
        values.remove(JdbcSourceOptions.SPLIT_KEY.key());
        values.remove(JdbcSourceOptions.RESULT_SET_FETCH_SIZE.key());
        values.remove(JdbcSourceOptions.QUERY_TIMEOUT_SECONDS.key());
        return io.yak.ops.configuration.ReadonlyConfig.fromMap(values);
    }
}
