package io.yak.ops.boot.config;

import io.yak.ops.composer.PipelineComposer;
import io.yak.ops.composer.execution.IncrementalBatchPreparation;
import io.yak.ops.composer.yakflow.EnvironmentOptions;
import io.yak.ops.composer.yakflow.ExecutionConfigurationFactory;
import io.yak.ops.composer.yakflow.YakFlowPipelineComposer;
import io.yak.ops.configuration.ReadonlyConfig;
import io.yak.ops.configuration.rule.ConfigValidator;
import io.yak.ops.connector.base.factory.FactoryUtil;
import io.yak.ops.connector.cdc.mysql.source.config.MySqlSourceOptions;
import io.yak.ops.core.execution.PipelineExecutor;
import io.yak.ops.flow.runtime.execution.EmbeddedPipelineExecutor;
import io.yak.ops.plugin.datasource.api.lookup.DataSourceLookupService;
import io.yak.ops.plugin.task.api.TaskPluginManager;
import io.yak.ops.plugin.task.datasync.PipelineConnectorCatalog;
import io.yak.ops.plugin.task.datasync.PipelineTaskChannelFactory;
import java.nio.file.Path;
import java.time.Duration;
import java.util.List;
import java.util.Map;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;

/** Constructs task plugins, physical options and Connector factories for the application. */
@Configuration(proxyBeanMethods = false)
public class TaskPluginConfiguration {

    @Bean
    PipelineExecutor taskPipelineExecutor() {
        return new EmbeddedPipelineExecutor();
    }

    @Bean
    FactoryUtil connectorFactoryUtil() {
        return new FactoryUtil();
    }

    @Bean
    ReadonlyConfig dataSyncRuntimeOptions(
            @Value("${yak.task.data-sync.source-parallelism:1}") int sourceParallelism,
            @Value("${yak.task.data-sync.sink-parallelism:1}") int sinkParallelism,
            @Value("${yak.task.data-sync.cdc.checkpoint-interval:PT10S}") String checkpointInterval,
            @Value("${yak.task.data-sync.cdc.max-restarts:1}") int maxRestarts) {
        return new io.yak.ops.configuration.Configuration()
                .set(EnvironmentOptions.SOURCE_PARALLELISM, sourceParallelism)
                .set(EnvironmentOptions.SINK_PARALLELISM, sinkParallelism)
                .set(EnvironmentOptions.CHECKPOINT_INTERVAL, Duration.parse(checkpointInterval))
                .set(EnvironmentOptions.MAX_RESTARTS, maxRestarts)
                .toReadonlyConfig();
    }

    @Bean
    io.yak.ops.configuration.Configuration mySqlCdcConnectorConfiguration(
            @Value("${yak.task.data-sync.cdc.snapshot-chunk-size:1024}") int snapshotChunkSize,
            @Value("${yak.task.data-sync.cdc.queue-capacity:256}") int queueCapacity) {
        io.yak.ops.configuration.Configuration options = new io.yak.ops.configuration.Configuration()
                .set(MySqlSourceOptions.SCAN_INCREMENTAL_SNAPSHOT_CHUNK_SIZE, snapshotChunkSize)
                .set(MySqlSourceOptions.QUEUE_CAPACITY, queueCapacity);
        ConfigValidator.of(options.toReadonlyConfig())
                .validate(io.yak.ops.configuration.rule.OptionRule.builder()
                        .optional(
                                MySqlSourceOptions.SCAN_INCREMENTAL_SNAPSHOT_CHUNK_SIZE,
                                io.yak.ops.configuration.rule.Conditions.greaterOrEqual(
                                        MySqlSourceOptions.SCAN_INCREMENTAL_SNAPSHOT_CHUNK_SIZE, 1))
                        .optional(
                                MySqlSourceOptions.QUEUE_CAPACITY,
                                io.yak.ops.configuration.rule.Conditions.greaterOrEqual(
                                        MySqlSourceOptions.QUEUE_CAPACITY, 1))
                        .build());
        return options;
    }

    @Bean
    ExecutionConfigurationFactory executionConfigurationFactory(
            ReadonlyConfig runtimeOptions,
            @Value("${yak.task.execution.state-directory:./data/task-state}") String stateDirectory) {
        return new ExecutionConfigurationFactory(runtimeOptions, Path.of(stateDirectory));
    }

    @Bean
    IncrementalBatchPreparation incrementalBatchPreparation(
            DataSourceLookupService resources, io.yak.ops.plugin.task.api.execution.TaskCursorStore cursors) {
        return new IncrementalBatchPreparation(resources, cursors);
    }

    @Bean
    PipelineComposer pipelineComposer(
            DataSourceLookupService resources,
            FactoryUtil factories,
            ReadonlyConfig runtimeOptions,
            ExecutionConfigurationFactory configurationFactory,
            io.yak.ops.configuration.Configuration connectorConfiguration,
            PipelineExecutor executor,
            IncrementalBatchPreparation incrementalPreparation) {
        return new YakFlowPipelineComposer(
                resources,
                factories,
                runtimeOptions,
                configurationFactory,
                Map.of("mysql", connectorConfiguration.toReadonlyConfig()),
                Map.of(),
                executor,
                incrementalPreparation);
    }

    @Bean
    PipelineConnectorCatalog pipelineConnectorCatalog(FactoryUtil factories) {
        return new PipelineConnectorCatalog(factories);
    }

    @Bean
    PipelineTaskChannelFactory pipelineTaskChannelFactory(FactoryUtil factories, PipelineComposer composer) {
        return new PipelineTaskChannelFactory(factories, composer);
    }

    @Bean
    TaskPluginManager taskPluginRegistry(PipelineTaskChannelFactory factory) {
        return TaskPluginManager.fromFactories(List.of(factory));
    }
}
