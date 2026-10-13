package io.yak.ops.plugin.task.datasync.pipeline;

import io.yak.ops.configuration.Configuration;
import io.yak.ops.configuration.ReadonlyConfig;
import io.yak.ops.connector.base.factory.FactoryUtil;
import io.yak.ops.connector.base.factory.TableSinkFactoryContext;
import io.yak.ops.connector.base.factory.TableSourceFactoryContext;
import io.yak.ops.core.api.connector.sink.Sink;
import io.yak.ops.core.api.connector.source.Source;
import io.yak.ops.core.data.TableRecord;
import io.yak.ops.flow.runtime.graph.StreamGraph;
import io.yak.ops.flow.runtime.graph.StreamGraphGenerator;
import io.yak.ops.flow.runtime.transformations.SinkTransformation;
import io.yak.ops.flow.runtime.transformations.SourceTransformation;
import io.yak.ops.plugin.datasource.api.lookup.DataSourceLookupService;
import io.yak.ops.plugin.task.api.execution.TaskExecutionIdentity;
import io.yak.ops.plugin.task.datasync.config.ExecutionConfigurationFactory;
import io.yak.ops.plugin.task.datasync.config.JobOptions;
import io.yak.ops.plugin.task.datasync.plan.JobPlan;
import java.nio.file.Path;
import java.util.LinkedHashMap;
import java.util.Map;
import java.util.Objects;

/** Compiles an authorized JobPlan to a physical single-table YakFlow pipeline. */
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
                JobOptions.defaults(),
                new ExecutionConfigurationFactory(JobOptions.defaults(), stateRoot));
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
    public CompiledPipeline build(JobPlan plan, TaskExecutionIdentity identity, boolean debug) {
        Objects.requireNonNull(plan, "plan");
        Objects.requireNonNull(identity, "identity");
        var sourceFactory = factories.source(plan.sourceParameters().get(JobOptions.TYPE));
        var sinkFactory = factories.sink(plan.sinkParameters().get(JobOptions.TYPE));
        int sourceParallelism = options.get(JobOptions.SOURCE_PARALLELISM);
        int sinkParallelism = options.get(JobOptions.SINK_PARALLELISM);
        if (sourceFactory.producesChangelog() && sinkParallelism != 1) {
            throw new UnsupportedOperationException(
                    "DATA_SYNC changelog requires a single Sink writer until keyed routing is configured");
        }
        Configuration configuration = configurationFactory.create(identity, sourceFactory.boundedness(), debug);
        String sourceId = plan.sourceParameters().get(JobOptions.DATASOURCE_ID);
        String sinkId = plan.sinkParameters().get(JobOptions.DATASOURCE_ID);
        ReadonlyConfig sourceConnection = resources.connectionOptions(
                identity.workspaceId(), sourceId, plan.source().revision());
        ReadonlyConfig targetConnection = resources.connectionOptions(
                identity.workspaceId(), sinkId, plan.target().revision());
        ReadonlyConfig sourceOptions = creationOptions(
                sourceConnectorOptions, sourceFactory.factoryIdentifier(), plan.sourceOptions(), sourceConnection);
        ReadonlyConfig sinkOptions = creationOptions(
                sinkConnectorOptions,
                sinkFactory.factoryIdentifier(),
                JobOptions.connectorOptions(plan.sinkParameters()),
                targetConnection);
        Source<TableRecord, ?, ?> source = sourceFactory.createSource(new TableSourceFactoryContext(
                sourceOptions, plan.source().table(), plan.source().schema()));
        if (source.getBoundedness() != sourceFactory.boundedness()) {
            throw new IllegalStateException("DATA_SYNC Source boundedness differs from its Factory contract");
        }
        Sink<TableRecord> sink = sinkFactory.createSink(new TableSinkFactoryContext(
                sinkOptions,
                plan.source().table(),
                plan.source().schema(),
                plan.target().table(),
                plan.target().schema()));
        SourceTransformation<TableRecord> input =
                new SourceTransformation<>("data-sync-source", source, TableRecord.class, sourceParallelism);
        SinkTransformation<TableRecord> output =
                new SinkTransformation<>(input, "data-sync-sink", sink, sinkParallelism);
        input.setUid("data-sync-source-" + identity.definitionId());
        output.setUid("data-sync-sink-" + identity.definitionId());
        StreamGraph graph = new StreamGraphGenerator(output, configuration).generate();
        return new CompiledPipeline(graph, configuration);
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
