package io.yak.ops.common.exception;

public class StorageOperateNoConfiguredException extends RuntimeException {

    public StorageOperateNoConfiguredException() {
    }

    public StorageOperateNoConfiguredException(String message) {
        super(message);
    }

    public StorageOperateNoConfiguredException(String message, Throwable cause) {
        super(message, cause);
    }

    public StorageOperateNoConfiguredException(Throwable cause) {
        super(cause);
    }

    public StorageOperateNoConfiguredException(String message, Throwable cause, boolean enableSuppression,
                                               boolean writableStackTrace) {
        super(message, cause, enableSuppression, writableStackTrace);
    }
}
