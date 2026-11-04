package io.yak.ops.common.exception;

public class FileOperateException extends BaseException {

    public FileOperateException(String message) {
        super(message);
    }

    public FileOperateException(String message, Throwable cause) {
        super(message, cause);
    }
}
