package io.yak.ops.server.master.exception;

public class MasterTaskExecuteException extends MasterException {

    public MasterTaskExecuteException(String message) {
        super(message);
    }

    public MasterTaskExecuteException(String message, Throwable cause) {
        super(message, cause);
    }

}
