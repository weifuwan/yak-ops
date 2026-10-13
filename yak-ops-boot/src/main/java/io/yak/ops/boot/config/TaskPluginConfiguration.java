package io.yak.ops.boot.config;

import io.yak.ops.configuration.ReadonlyConfig;
import io.yak.ops.configuration.rule.ConfigValidator;
import io.yak.ops.connector.base.factory.FactoryUtil;
import io.yak.ops.connector.cdc.mysql.source.MySqlCdcSourceOptions;
import io.yak.ops.core.execution.PipelineExecutor;
import io.yak.ops.flow.runtime.execution.EmbeddedPipelineExecutor;
import io.yak.ops.plugin.datasource.api.lookup.DataSourceLookupService;
import io.yak.ops.plugin.task.api.TaskPluginRegistry;
import io.yak.ops.plugin.task.datasync.DataSyncTaskPlugin;
import io.yak.ops.plugin.task.datasync.config.ExecutionConfigurationFactory;
import io.yak.ops.plugin.task.datasync.config.JobOptions;
import io.yak.ops.plugin.task.datasync.execution.DataSyncTaskExecutor;
import io.yak.ops.plugin.task.datasync.pipeline.PipelineBuilder;
import io.yak.ops.plugin.task.datasync.plan.JobPlanner;
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
    JobPlanner jobPlanner(DataSourceLookupService resources, FactoryUtil factories) {
        return new JobPlanner(resources, factories);
    }

    @Bean
    ReadonlyConfig dataSyncRuntimeOptions(
            @Value("${yak.task.data-sync.source-parallelism:1}") int sourceParallelism,
            @Value("${yak.task.data-sync.sink-parallelism:1}") int sinkParallelism,
            @Value("${yak.task.data-sync.cdc.checkpoint-interval:PT10S}") String checkpointInterval,
            @Value("${yak.task.data-sync.cdc.max-restarts:1}") int maxRestarts) {
        return new io.yak.ops.configuration.Configuration()
                .set(JobOptions.SOURCE_PARALLELISM, sourceParallelism)
                .set(JobOptions.SINK_PARALLELISM, sinkParallelism)
                .set(JobOptions.CHECKPOINT_INTERVAL, Duration.parse(checkpointInterval))
                .set(JobOptions.MAX_RESTARTS, maxRestarts)
                .toReadonlyConfig();
    }

    @Bean
    io.yak.ops.configuration.Configuration mySqlCdcConnectorConfiguration(
            @Value("${yak.task.data-sync.cdc.snapshot-chunk-size:1024}") int snapshotChunkSize,
            @Value("${yak.task.data-sync.cdc.queue-capacity:256}") int queueCapacity) {
        io.yak.ops.configuration.Configuration options = new io.yak.ops.configuration.Configuration()
                .set(MySqlCdcSourceOptions.SNAPSHOT_CHUNK_SIZE, snapshotChunkSize)
                .set(MySqlCdcSourceOptions.QUEUE_CAPACITY, queueCapacity);
        ConfigValidator.of(options.toReadonlyConfig()).validate(MySqlCdcSourceOptions.PHYSICAL_RULE);
        return options;
    }

    @Bean
    ExecutionConfigurationFactory executionConfigurationFactory(
            ReadonlyConfig runtimeOptions,
            @Value("${yak.task.execution.state-directory:./data/task-state}") String stateDirectory) {
        return new ExecutionConfigurationFactory(runtimeOptions, Path.of(stateDirectory));
    }

    @Bean
    PipelineBuilder pipelineBuilder(
            DataSourceLookupService resources,
            FactoryUtil factories,
            ReadonlyConfig runtimeOptions,
            ExecutionConfigurationFactory configurationFactory,
            io.yak.ops.configuration.Configuration connectorConfiguration) {
        return new PipelineBuilder(
                resources,
                factories,
                runtimeOptions,
                configurationFactory,
                Map.of("MYSQL_CDC", connectorConfiguration.toReadonlyConfig()));
    }

    @Bean
    DataSyncTaskExecutor dataSyncTaskExecutor(JobPlanner planner, PipelineBuilder builder, PipelineExecutor executor) {
        return new DataSyncTaskExecutor(planner, builder, executor);
    }

    @Bean
    DataSyncTaskPlugin dataSyncTaskPlugin(JobPlanner planner, DataSyncTaskExecutor executor, FactoryUtil factories) {
        return new DataSyncTaskPlugin(planner, executor, factories);
    }

    @Bean
    TaskPluginRegistry taskPluginRegistry(DataSyncTaskPlugin taskPlugin) {
        return new TaskPluginRegistry(List.of(taskPlugin));
    }
}
