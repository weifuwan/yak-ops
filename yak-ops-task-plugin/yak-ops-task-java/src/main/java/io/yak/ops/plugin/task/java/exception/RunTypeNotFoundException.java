package io.yak.ops.plugin.task.java.exception;

public class RunTypeNotFoundException extends RuntimeException {

    public RunTypeNotFoundException() {
        super();
    }

    public RunTypeNotFoundException(String s) {
        super(s);
    }

    public RunTypeNotFoundException(String s, Throwable throwable) {
        super(s, throwable);
    }

    public RunTypeNotFoundException(Throwable throwable) {
        super(throwable);
    }

    protected RunTypeNotFoundException(String s, Throwable throwable, boolean b, boolean b1) {
        super(s, throwable, b, b1);
    }
}
