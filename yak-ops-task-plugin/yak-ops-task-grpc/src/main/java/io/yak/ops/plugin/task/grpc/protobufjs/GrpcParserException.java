package io.yak.ops.plugin.task.grpc.protobufjs;

public class GrpcParserException extends RuntimeException {

    public GrpcParserException(String msg) {
        super(msg);
    }

    public GrpcParserException(String msg, Throwable cause) {
        super(msg, cause);
    }
}
