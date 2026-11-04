package io.yak.ops.plugin.task.grpc.protobufjs.mapping;

import java.util.Map;

import lombok.Data;

@Data
public class Type extends Namespace {

    private Map<String, Field> fields;
    private Map<String, OneOf> oneofs;
}
