package io.yak.ops.plugin.task.api;

public class TaskException extends RuntimeException {

    private static final long serialVersionUID = 8155449302457294758L;

    public TaskException() {
        super();
    }

    public TaskException(String msg, Throwable cause) {
        super(msg, cause);
    }

    public TaskException(String msg) {
        super(msg);
    }
}
