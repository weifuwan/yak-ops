package org.apache.flink.cdc.composer;

import org.apache.flink.cdc.composer.definition.PipelineDef;

/** Composer for translating a pipeline definition to an execution. */
public interface PipelineComposer {

    /**
     * Composing pipeline execution from the specified pipeline definition.
     *
     * @param pipelineDef definition of the pipeline
     */
    PipelineExecution compose(PipelineDef pipelineDef);
}
