package io.yak.ops.server.master.engine.exceptions;

public class TaskKillException extends RuntimeException {

    public TaskKillException(String message) {
        super(message);
    }

    public TaskKillException(String message, Throwable cause) {
        super(message, cause);
    }

}
