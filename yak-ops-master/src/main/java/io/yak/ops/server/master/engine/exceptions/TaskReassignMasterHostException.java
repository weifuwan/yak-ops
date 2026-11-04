package io.yak.ops.server.master.engine.exceptions;

public class TaskReassignMasterHostException extends RuntimeException {

    public TaskReassignMasterHostException(String message) {
        super(message);
    }

    public TaskReassignMasterHostException(String message, Throwable cause) {
        super(message, cause);
    }

}
