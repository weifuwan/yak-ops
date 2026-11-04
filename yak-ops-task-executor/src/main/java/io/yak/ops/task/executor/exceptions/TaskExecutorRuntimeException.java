package io.yak.ops.task.executor.exceptions;

public class TaskExecutorRuntimeException extends RuntimeException {

    public TaskExecutorRuntimeException(String message) {
        super(message);
    }

    public TaskExecutorRuntimeException(String message, Throwable cause) {
        super(message, cause);
    }

}
