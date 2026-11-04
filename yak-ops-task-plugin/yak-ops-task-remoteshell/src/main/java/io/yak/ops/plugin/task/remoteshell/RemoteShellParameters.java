package io.yak.ops.plugin.task.remoteshell;

import io.yak.ops.plugin.task.api.enums.ResourceType;
import io.yak.ops.plugin.task.api.parameters.AbstractParameters;
import io.yak.ops.plugin.task.api.parameters.resource.ResourceParametersHelper;

import lombok.Data;

@Data
public class RemoteShellParameters extends AbstractParameters {

    private String rawScript;

    private String type;

    /**
     * datasource id
     */
    private int datasource;

    @Override
    public boolean checkParameters() {
        return rawScript != null && !rawScript.isEmpty();
    }

    @Override
    public ResourceParametersHelper getResources() {
        ResourceParametersHelper resources = super.getResources();
        resources.put(ResourceType.DATASOURCE, datasource);
        return resources;
    }

}
