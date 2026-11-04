package io.yak.ops.plugin.task.python;

import io.yak.ops.plugin.task.api.model.ResourceInfo;
import io.yak.ops.plugin.task.api.parameters.AbstractParameters;

import java.util.List;

import lombok.Data;

@Data
public class PythonParameters extends AbstractParameters {

    /**
     * origin python script
     */
    private String rawScript;

    private List<ResourceInfo> resourceList;

    @Override
    public boolean checkParameters() {
        return rawScript != null && !rawScript.isEmpty();
    }

    @Override
    public List<ResourceInfo> getResourceFilesList() {
        return this.resourceList;
    }
}
