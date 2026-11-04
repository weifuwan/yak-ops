package io.yak.ops.server.master.exception.dispatch;

import io.yak.ops.server.master.exception.MasterException;

public class TaskDispatchException extends MasterException {

    public TaskDispatchException(String message) {
        super(message);
    }

    public TaskDispatchException(String message, Throwable cause) {
        super(message, cause);
    }
}
