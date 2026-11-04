package io.yak.ops.server.master.engine.exceptions;

public class TaskPauseException extends RuntimeException {

    public TaskPauseException(String message) {
        super(message);
    }

    public TaskPauseException(String message, Throwable cause) {
        super(message, cause);
    }

}
