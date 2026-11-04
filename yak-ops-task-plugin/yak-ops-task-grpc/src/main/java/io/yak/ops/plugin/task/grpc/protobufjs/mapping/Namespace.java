package io.yak.ops.plugin.task.grpc.protobufjs.mapping;

import java.util.Map;

import com.fasterxml.jackson.annotation.JsonSubTypes;
import com.fasterxml.jackson.annotation.JsonTypeInfo;

import lombok.Data;

@Data
@JsonTypeInfo(use = JsonTypeInfo.Id.DEDUCTION, defaultImpl = Namespace.class)
@JsonSubTypes({
        @JsonSubTypes.Type(Type.class),
        @JsonSubTypes.Type(Service.class)
})
public class Namespace extends ReflectionObject {

    private Map<String, ? extends ReflectionObject> nested;
}
