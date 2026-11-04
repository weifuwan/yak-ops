package io.yak.ops.plugin.alert.email.exception;

public class AlertEmailException extends RuntimeException {

    public AlertEmailException(String errMsg) {
        super(errMsg);
    }

    public AlertEmailException(String errMsg, Throwable cause) {
        super(errMsg, cause);
    }
}
