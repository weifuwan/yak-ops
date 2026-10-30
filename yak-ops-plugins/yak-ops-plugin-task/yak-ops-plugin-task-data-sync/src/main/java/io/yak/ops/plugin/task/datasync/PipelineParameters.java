package io.yak.ops.plugin.task.datasync;

import io.yak.ops.plugin.task.api.parameters.AbstractParameters;

/** Opaque V5 Pipeline JSON carried by the DATA_SYNC Task. Runtime owns its schema and options. */
public final class PipelineParameters extends AbstractParameters {
    private final String pipelineJson;

    public PipelineParameters(String pipelineJson) {
        this.pipelineJson = pipelineJson;
    }

    public String getPipelineJson() {
        return pipelineJson;
    }

    @Override
    public boolean checkParameters() {
        return pipelineJson != null && !pipelineJson.isBlank();
    }
}
