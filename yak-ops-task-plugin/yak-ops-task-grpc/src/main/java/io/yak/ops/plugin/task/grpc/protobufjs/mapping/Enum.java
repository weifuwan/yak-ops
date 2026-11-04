package io.yak.ops.plugin.task.grpc.protobufjs.mapping;

import java.util.Map;

import lombok.Data;

@Data
public class Enum extends ReflectionObject {

    private Map<String, Integer> values;
}
