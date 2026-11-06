package io.yak.ops.plugin.task.datasync;

import io.yak.ops.plugin.task.api.enums.ResourceType;
import io.yak.ops.plugin.task.api.parameters.AbstractParameters;
import io.yak.ops.plugin.task.api.parameters.resource.ResourceParametersHelper;

import com.fasterxml.jackson.databind.JsonNode;

import lombok.Data;

/**
 * The task parameter is the native Flink CDC pipeline YAML tree with Yak Ops
 * datasource references in the source and sink blocks. No second connector DTO.
 */
@Data
public class DataSyncParameters extends AbstractParameters {

    private JsonNode definition;

    public void validate() {
        new DataSyncPipelineValidator().validate(definition);
    }

    @Override
    public boolean checkParameters() {
        try {
            validate();
            return true;
        } catch (IllegalArgumentException e) {
            return false;
        }
    }

    @Override
    public ResourceParametersHelper getResources() {
        validate();
        ResourceParametersHelper resources = super.getResources();
        resources.put(ResourceType.DATASOURCE, datasourceId(definition.path("source")));
        resources.put(ResourceType.DATASOURCE, datasourceId(definition.path("sink")));
        return resources;
    }

    public static int datasourceId(JsonNode endpoint) {
        JsonNode value = endpoint.path("datasource-id");
        if (!value.isTextual() || !value.asText().matches("[1-9][0-9]*")) {
            throw new IllegalArgumentException("datasource-id must be a positive integer string");
        }
        try {
            return Integer.parseInt(value.asText());
        } catch (NumberFormatException e) {
            throw new IllegalArgumentException("datasource-id is out of range", e);
        }
    }
}
