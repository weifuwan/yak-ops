package io.yak.ops.task.executor.exceptions;

public class TaskExecutorNotFoundException extends TaskExecutorRuntimeException {

    public TaskExecutorNotFoundException(int taskExecutorId) {
        super("Cannot find the TaskExecutor(id=" + taskExecutorId + ")");
    }

}
