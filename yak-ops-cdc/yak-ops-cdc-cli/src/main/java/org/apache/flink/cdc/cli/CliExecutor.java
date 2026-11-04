package org.apache.flink.cdc.cli;

import org.apache.flink.cdc.cli.parser.PipelineDefinitionParser;
import org.apache.flink.cdc.cli.parser.YamlPipelineDefinitionParser;
import org.apache.flink.cdc.common.annotation.VisibleForTesting;
import org.apache.flink.cdc.common.configuration.Configuration;
import org.apache.flink.cdc.composer.PipelineComposer;
import org.apache.flink.cdc.composer.PipelineExecution;
import org.apache.flink.cdc.composer.definition.PipelineDef;
import org.apache.flink.cdc.composer.flink.FlinkPipelineComposer;
import org.apache.flink.core.fs.Path;


import java.util.List;

/** Executor for the local Flink pipeline. */
public class CliExecutor {

    private final Path pipelineDefPath;
    private final org.apache.flink.configuration.Configuration flinkConfig;
    private final Configuration globalPipelineConfig;
    private final List<Path> additionalJars;
    private PipelineComposer composer = null;

    public CliExecutor(
            Path pipelineDefPath,
            org.apache.flink.configuration.Configuration flinkConfig,
            Configuration globalPipelineConfig,
            List<Path> additionalJars) {
        this.pipelineDefPath = pipelineDefPath;
        this.flinkConfig = flinkConfig;
        this.globalPipelineConfig = globalPipelineConfig;
        this.additionalJars = additionalJars;
    }

    public PipelineExecution.ExecutionInfo run() throws Exception {
        return deployWithComposer(
                FlinkPipelineComposer.ofMiniCluster(flinkConfig, additionalJars));
    }

    private PipelineExecution.ExecutionInfo deployWithComposer(PipelineComposer composer)
            throws Exception {
        PipelineDefinitionParser pipelineDefinitionParser = new YamlPipelineDefinitionParser();
        PipelineDef pipelineDef =
                pipelineDefinitionParser.parse(pipelineDefPath, globalPipelineConfig);
        PipelineExecution execution = composer.compose(pipelineDef);
        return execution.execute();
    }

    @VisibleForTesting
    public PipelineExecution.ExecutionInfo deployWithNoOpComposer() throws Exception {
        return deployWithComposer(this.composer);
    }

    @VisibleForTesting
    void setComposer(PipelineComposer composer) {
        this.composer = composer;
    }

    @VisibleForTesting
    public org.apache.flink.configuration.Configuration getFlinkConfig() {
        return flinkConfig;
    }

    @VisibleForTesting
    public Configuration getGlobalPipelineConfig() {
        return globalPipelineConfig;
    }

    @VisibleForTesting
    public List<Path> getAdditionalJars() {
        return additionalJars;
    }

}
