package io.yak.ops.api.exceptions;

import io.yak.ops.api.enums.Status;

import java.text.MessageFormat;

import lombok.Data;

@Data
public class ServiceException extends RuntimeException {

    private int code;

    public ServiceException() {
        this(Status.INTERNAL_SERVER_ERROR_ARGS);
    }

    public ServiceException(Status status) {
        this(status.getCode(), status.getMsg());
    }

    public ServiceException(Status status, Object... formatter) {
        this(status.getCode(), MessageFormat.format(status.getMsg(), formatter));
    }

    public ServiceException(String message) {
        this(Status.INTERNAL_SERVER_ERROR_ARGS, message);
    }

    public ServiceException(int code, String message) {
        this(code, message, null);
    }

    public ServiceException(int code, String message, Exception cause) {
        super(message, cause);
        this.code = code;
    }

    public ServiceException(String message, Exception exception) {
        this(Status.INTERNAL_SERVER_ERROR_ARGS, message, exception);
    }

}
