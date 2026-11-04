package io.yak.ops.plugin.task.grpc.protobufjs.mapping;

import java.util.Map;

import com.fasterxml.jackson.annotation.JsonSubTypes;
import com.fasterxml.jackson.annotation.JsonTypeInfo;

import lombok.Data;

@Data
@JsonTypeInfo(use = JsonTypeInfo.Id.DEDUCTION, defaultImpl = Namespace.class)
@JsonSubTypes({
        @JsonSubTypes.Type(Namespace.class),
        @JsonSubTypes.Type(Enum.class),
        @JsonSubTypes.Type(Field.class),
        @JsonSubTypes.Type(OneOf.class),
        @JsonSubTypes.Type(Method.class)
})
public abstract class ReflectionObject {

    private Map<String, Object> options;
}
