package io.yak.ops.server.master.exception;

public class MasterException extends Exception {

    public MasterException(String message) {
        super(message);
    }

    public MasterException(String message, Throwable throwable) {
        super(message, throwable);
    }
}
