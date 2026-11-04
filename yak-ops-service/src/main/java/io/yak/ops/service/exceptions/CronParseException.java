package io.yak.ops.service.exceptions;

public class CronParseException extends Exception {

    public CronParseException(String message, Throwable throwable) {
        super(message, throwable);
    }

}
