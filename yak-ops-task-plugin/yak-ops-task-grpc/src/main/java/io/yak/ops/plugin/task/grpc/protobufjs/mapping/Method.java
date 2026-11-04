package io.yak.ops.plugin.task.grpc.protobufjs.mapping;

import lombok.Data;

@Data
public class Method extends ReflectionObject {

    private String type;
    private String requestType;
    private String responseType;
    private String requestStream;
    private String responseStream;
}
