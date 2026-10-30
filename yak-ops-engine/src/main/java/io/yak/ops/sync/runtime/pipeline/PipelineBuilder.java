package io.yak.ops.sync.runtime.pipeline;

import io.yak.ops.configuration.Configuration;
import io.yak.ops.configuration.ReadonlyConfig;
import io.yak.ops.connector.base.factory.FactoryUtil;
import io.yak.ops.connector.base.factory.TableSinkFactoryContext;
import io.yak.ops.connector.base.factory.TableSourceFactoryContext;
import io.yak.ops.connector.base.metrics.TableMetrics;
import io.yak.ops.connector.cdc.mysql.source.MySqlSource;
import io.yak.ops.core.api.connector.sink.MetadataApplier;
import io.yak.ops.core.api.connector.sink.Sink;
import io.yak.ops.core.api.connector.source.Boundedness;
import io.yak.ops.core.api.connector.source.Source;
import io.yak.ops.core.data.TableRecord;
import io.yak.ops.flow.runtime.graph.StreamGraph;
import io.yak.ops.flow.runtime.graph.StreamGraphGenerator;
import io.yak.ops.flow.runtime.transformations.SinkTransformation;
import io.yak.ops.flow.runtime.transformations.SourceTransformation;
import io.yak.ops.plugin.datasource.api.lookup.DataSourceLookupService;
import io.yak.ops.plugin.task.api.execution.TaskExecutionIdentity;
import io.yak.ops.sync.runtime.config.JobOptions;
import io.yak.ops.composer.yakflow.translator.DataSourceTranslator;
import io.yak.ops.composer.yakflow.translator.DataSinkTranslator;
import io.yak.ops.sync.runtime.execution.TableExecutionProgress;
import io.yak.ops.sync.runtime.plan.JobPlan;
import io.yak.ops.sync.runtime.plan.TablePlan;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Objects;

/** Compiles all admitted table routes into one physical YakFlow pipeline. */
public final class PipelineBuilder {

    private final DataSourceLookupService resources;
    private final FactoryUtil factories;
    private final ReadonlyConfig options;
    private final ExecutionConfigurationFactory configurationFactory;
    private final Map<String, ReadonlyConfig> sourceConnectorOptions;
    private final Map<String, ReadonlyConfig> sinkConnectorOptions;

    /** Creates a builder with system defaults for embedded execution. */
    public PipelineBuilder(DataSourceLookupService resources, FactoryUtil factories, Path stateRoot) {
        this(
                resources,
                factories,
                EnvironmentOptions.defaults(),
                new ExecutionConfigurationFactory(EnvironmentOptions.defaults(), stateRoot));
    }

    /** Constructs a builder using Connector defaults. */
    public PipelineBuilder(
            DataSourceLookupService resources,
            FactoryUtil factories,
            ReadonlyConfig options,
            ExecutionConfigurationFactory configurationFactory) {
        this(resources, factories, options, configurationFactory, Map.of(), Map.of());
    }

    /**
     * Constructs a builder with physical settings scoped by endpoint role and factory identifier.
     *
     * <p>Source and Sink factories may share an identifier without accepting the same options.
     * Each endpoint retains its own strict creation rules.
     *
     * @param resources Workspace-scoped datasource lookup
     * @param factories registered Source and Sink factories
     * @param options engine execution settings
     * @param configurationFactory engine configuration builder
     * @param sourceConnectorOptions physical settings for Source factories
     * @param sinkConnectorOptions physical settings for Sink factories
     */
    public PipelineBuilder(
            DataSourceLookupService resources,
            FactoryUtil factories,
            ReadonlyConfig options,
            ExecutionConfigurationFactory configurationFactory,
            Map<String, ReadonlyConfig> sourceConnectorOptions,
            Map<String, ReadonlyConfig> sinkConnectorOptions) {
        this.resources = Objects.requireNonNull(resources, "resources");
        this.factories = Objects.requireNonNull(factories, "factories");
        this.options = Objects.requireNonNull(options, "options");
        this.configurationFactory = Objects.requireNonNull(configurationFactory, "configurationFactory");
        this.sourceConnectorOptions = Map.copyOf(sourceConnectorOptions);
        this.sinkConnectorOptions = Map.copyOf(sinkConnectorOptions);
    }

    /**
     * Compiles one physical job with stable operator UIDs and validated resource revisions.
     *
     * @param plan authorized logical Job plan
     * @param identity trusted Task Instance identity
     * @param debug whether to enable detailed execution diagnostics
     * @return compiled topology and execution configuration
     */
    /** Compiles a plan without an existing preparation observation. */
    public CompiledPipeline build(JobPlan plan, TaskExecutionIdentity identity, boolean debug) {
        return build(plan, identity, debug, null, false, false);
    }

    /** Compiles one fully prepared physical Job with its Task-owned observations. */
    public CompiledPipeline build(
            JobPlan plan,
            TaskExecutionIdentity identity,
            boolean debug,
            TableExecutionProgress progress,
            boolean schemaChangesEnabled,
            boolean allowDropColumns) {
        Objects.requireNonNull(plan, "plan");
        Objects.requireNonNull(identity, "identity");
        if (plan.tables().stream().anyMatch(table -> table.targetSchema() == null)) {
            throw new IllegalStateException("Every target must be prepared and read back before pipeline compilation");
        }
        TablePlan first = plan.tables().getFirst();
        io.yak.ops.connector.base.factory.TableSourceFactory sourceFactory =
                factories.source(first.sourceParameters().get(JobOptions.TYPE));
        io.yak.ops.connector.base.factory.TableSinkFactory sinkFactory =
                factories.sink(first.sinkParameters().get(JobOptions.TYPE));
        int sourceParallelism = options.get(EnvironmentOptions.SOURCE_PARALLELISM);
        int sinkParallelism = options.get(EnvironmentOptions.SINK_PARALLELISM);
        if (sourceFactory.producesChangelog() && sinkParallelism != 1) {
            throw new UnsupportedOperationException(
                    "DATA_SYNC changelog requires a single Sink writer until keyed routing is configured");
        }
        String sourceId = first.sourceParameters().get(JobOptions.DATASOURCE_ID);
        String sinkId = first.sinkParameters().get(JobOptions.DATASOURCE_ID);
        ReadonlyConfig sourceConnection = resources.connectionOptions(
                identity.workspaceId(), sourceId, first.source().revision());
        ReadonlyConfig targetConnection = resources.connectionOptions(
                identity.workspaceId(), sinkId, first.targetDatasource().revision());
        TableMetrics metrics = progress == null ? new TableMetrics() : progress.metrics();
        List<TableSourceFactoryContext> sourceContexts = new ArrayList<>();
        List<TableSinkFactoryContext> sinkContexts = new ArrayList<>();
        for (TablePlan table : plan.tables()) {
            metrics.register(table.source().table());
            ReadonlyConfig sourceOptions = creationOptions(
                    sourceConnectorOptions, sourceFactory.factoryIdentifier(), table.sourceOptions(), sourceConnection);
            ReadonlyConfig sinkOptions = creationOptions(
                    sinkConnectorOptions,
                    sinkFactory.factoryIdentifier(),
                    JobOptions.connectorOptions(table.sinkParameters()),
                    targetConnection);
            sourceContexts.add(new TableSourceFactoryContext(
                    sourceOptions, table.source().table(), table.source().schema(), metrics));
            sinkContexts.add(new TableSinkFactoryContext(
                    sinkOptions,
                    table.source().table(),
                    table.writeSourceSchema(),
                    table.targetTable(),
                    table.targetSchema(),
                    metrics));
        }
        Source<TableRecord, ?, ?> source = sourceFactory.createSource(sourceContexts);
        Configuration configuration = configurationFactory.create(
                identity, source.getBoundedness(), debug, schemaChangesEnabled, allowDropColumns);
        Sink<TableRecord> sink = sinkFactory.createSink(sinkContexts);
        SourceTransformation<TableRecord> input =
                new DataSourceTranslator().translate(source, sourceParallelism, identity.definitionId());
        SinkTransformation<TableRecord> output =
                new DataSinkTranslator().translate(input, sink, sinkParallelism, identity.definitionId());
        StreamGraph graph = new StreamGraphGenerator(output, configuration).generate();
        TableExecutionProgress observations = progress;
        if (observations == null) {
            boolean continuous = source.getBoundedness() != Boundedness.BOUNDED;
            String dataStep = source instanceof MySqlSource mysql
                            && mysql.startupOptions().isStreamOnly()
                    ? "INCREMENTAL"
                    : "FULL";
            observations = new TableExecutionProgress(plan.tables(), metrics, continuous, dataStep);
        }
        return new CompiledPipeline(graph, configuration, observations);
    }

    /**
     * Constructs an independently owned metadata applier using the same authorized Sink options.
     *
     * @param table admitted target binding
     * @param identity trusted Task identity
     * @return unstarted metadata applier; its execution owner must close it
     */
    public MetadataApplier createMetadataApplier(TablePlan table, TaskExecutionIdentity identity) {
        io.yak.ops.connector.base.factory.TableSinkFactory factory =
                factories.sink(table.sinkParameters().get(JobOptions.TYPE));
        ReadonlyConfig connection = resources.connectionOptions(
                identity.workspaceId(),
                table.sinkParameters().get(JobOptions.DATASOURCE_ID),
                table.targetDatasource().revision());
        ReadonlyConfig configured = creationOptions(
                sinkConnectorOptions,
                factory.factoryIdentifier(),
                JobOptions.connectorOptions(table.sinkParameters()),
                connection);
        return factory.createMetadataApplier(configured);
    }

    private static ReadonlyConfig creationOptions(
            Map<String, ReadonlyConfig> connectorOptions,
            String identifier,
            ReadonlyConfig operation,
            ReadonlyConfig connection) {
        Map<String, Object> values = new LinkedHashMap<>();
        ReadonlyConfig configured = connectorOptions.get(identifier);
        if (configured != null) {
            values.putAll(configured.getSourceMap());
        }
        values.putAll(operation.getSourceMap());
        values.putAll(connection.getSourceMap());
        return ReadonlyConfig.fromMap(values);
    }
}
