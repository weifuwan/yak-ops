package io.yak.ops.plugin.task.shell;

import io.yak.ops.plugin.task.api.model.ResourceInfo;
import io.yak.ops.plugin.task.api.parameters.AbstractParameters;

import java.util.List;

import lombok.Data;

@Data
public class ShellParameters extends AbstractParameters {

    private String rawScript;

    private List<ResourceInfo> resourceList;

    @Override
    public boolean checkParameters() {
        return rawScript != null && !rawScript.isEmpty();
    }

    @Override
    public List<ResourceInfo> getResourceFilesList() {
        return resourceList;
    }
}
