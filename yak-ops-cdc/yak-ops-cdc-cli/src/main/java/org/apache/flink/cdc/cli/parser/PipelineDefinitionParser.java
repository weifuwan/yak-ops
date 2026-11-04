package org.apache.flink.cdc.cli.parser;

import org.apache.flink.cdc.common.configuration.Configuration;
import org.apache.flink.cdc.composer.definition.PipelineDef;
import org.apache.flink.core.fs.Path;

/** Parsing pipeline definition files and generate {@link PipelineDef}. */
public interface PipelineDefinitionParser {

    /**
     * Parse the specified pipeline definition file path, merge global configurations, then generate
     * the {@link PipelineDef}.
     */
    PipelineDef parse(Path pipelineDefPath, Configuration globalPipelineConfig) throws Exception;

    /**
     * Parse the specified pipeline definition string, merge global configurations, then generate
     * the {@link PipelineDef}.
     */
    PipelineDef parse(String pipelineDefText, Configuration globalPipelineConfig) throws Exception;
}
