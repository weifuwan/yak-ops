package io.yak.ops.plugin.task.datasync;

import io.yak.ops.plugin.task.api.parameters.AbstractParameters;
import io.yak.ops.configuration.ReadonlyConfig;
import io.yak.ops.plugin.task.api.TaskPluginException;

/** Opaque Flink CDC Pipeline YAML carried by the DATA_SYNC Task. */
public final class PipelineParameters extends AbstractParameters {
    private final String pipelineYaml;

    public PipelineParameters(String pipelineYaml) {
        this.pipelineYaml = pipelineYaml;
    }

    public String getPipelineYaml() {
        return pipelineYaml;
    }

    public static String yaml(ReadonlyConfig parameters) {
        Object value = parameters.getSourceMap().get("pipelineYaml");
        if (!(value instanceof String text) || text.isBlank()) {
            throw new TaskPluginException("DATA_SYNC requires Pipeline YAML");
        }
        return text;
    }

    @Override
    public boolean checkParameters() {
        return pipelineYaml != null && !pipelineYaml.isBlank();
    }
}
