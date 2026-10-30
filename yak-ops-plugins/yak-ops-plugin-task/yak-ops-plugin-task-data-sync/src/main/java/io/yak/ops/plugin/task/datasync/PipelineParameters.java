package io.yak.ops.plugin.task.datasync;

import io.yak.ops.configuration.ReadonlyConfig;
import io.yak.ops.plugin.task.api.parameters.AbstractParameters;
import java.util.Objects;

/** Task API JSON. YAML is an ephemeral engine input, never a persisted parameter. */
public final class PipelineParameters extends AbstractParameters {
    private final ReadonlyConfig json;

    public PipelineParameters(ReadonlyConfig json) {
        this.json = Objects.requireNonNull(json, "json");
    }

    public ReadonlyConfig getJson() {
        return json;
    }

    @Override
    public boolean checkParameters() {
        return json.getSourceMap().containsKey("source") && json.getSourceMap().containsKey("sink");
    }
}
