package io.yak.ops.api.vo;

import com.fasterxml.jackson.databind.JsonNode;

import lombok.AllArgsConstructor;
import lombok.Data;

@Data
@AllArgsConstructor
public class DataSyncDefinitionVO {

    private String definitionId;
    private int version;
    private String name;
    private String taskType;
    private JsonNode parameters;
}
