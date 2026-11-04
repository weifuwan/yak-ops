package io.yak.ops.plugin.task.grpc;

import io.yak.ops.plugin.task.api.TaskException;

public class GrpcTaskException extends TaskException {

    public GrpcTaskException(String msg) {
        super(msg);
    }

    public GrpcTaskException(String msg, Throwable cause) {
        super(msg, cause);
    }
}
