package io.yak.ops.plugin.task.datasync;

import io.yak.ops.plugin.task.api.parameters.AbstractParameters;

/** Opaque V6 Pipeline YAML carried by the DATA_SYNC Task. */
public final class PipelineParameters extends AbstractParameters {
    private final String pipelineYaml;

    public PipelineParameters(String pipelineYaml) {
        this.pipelineYaml = pipelineYaml;
    }

    public String getPipelineYaml() {
        return pipelineYaml;
    }

    @Override
    public boolean checkParameters() {
        return pipelineYaml != null && !pipelineYaml.isBlank();
    }
}
