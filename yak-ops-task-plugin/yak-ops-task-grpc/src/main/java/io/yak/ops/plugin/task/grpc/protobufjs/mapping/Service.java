package io.yak.ops.plugin.task.grpc.protobufjs.mapping;

import java.util.Map;

import lombok.Data;

@Data
public class Service extends Namespace {

    private Map<String, Method> methods;
}
