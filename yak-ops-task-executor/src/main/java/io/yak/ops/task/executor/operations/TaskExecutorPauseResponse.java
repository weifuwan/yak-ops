package io.yak.ops.task.executor.operations;

import lombok.AllArgsConstructor;
import lombok.Data;
import lombok.NoArgsConstructor;

@Data
@NoArgsConstructor
@AllArgsConstructor
public class TaskExecutorPauseResponse {

    private boolean success;

    private String message;

    public static TaskExecutorPauseResponse success() {
        return new TaskExecutorPauseResponse(true, null);
    }

    public static TaskExecutorPauseResponse fail(String message) {
        return new TaskExecutorPauseResponse(false, message);
    }

}
