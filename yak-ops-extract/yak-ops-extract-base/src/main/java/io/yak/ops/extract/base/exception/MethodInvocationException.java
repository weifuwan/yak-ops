package io.yak.ops.extract.base.exception;

public class MethodInvocationException extends RuntimeException {

    public MethodInvocationException(String message) {
        super(message);
    }

    public static MethodInvocationException of(String message) {
        return new MethodInvocationException(message);
    }

}
