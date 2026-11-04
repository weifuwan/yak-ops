package io.yak.ops.plugin.task.grpc.protobufjs.mapping;

import com.fasterxml.jackson.annotation.JsonSubTypes;
import com.fasterxml.jackson.annotation.JsonTypeInfo;
import com.fasterxml.jackson.databind.JsonNode;

import lombok.Data;

@Data
@JsonTypeInfo(use = JsonTypeInfo.Id.DEDUCTION, defaultImpl = Field.class)
@JsonSubTypes({
        @JsonSubTypes.Type(MapField.class)
})
public class Field extends ReflectionObject {

    private JsonNode rule;

    private String type;
    private int id;
}
