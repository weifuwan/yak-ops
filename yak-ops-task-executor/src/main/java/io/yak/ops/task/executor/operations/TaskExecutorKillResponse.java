package io.yak.ops.task.executor.operations;

import lombok.AllArgsConstructor;
import lombok.Data;
import lombok.NoArgsConstructor;

@Data
@NoArgsConstructor
@AllArgsConstructor
public class TaskExecutorKillResponse {

    private boolean success;

    private String message;

    public static TaskExecutorKillResponse success() {
        return new TaskExecutorKillResponse(true, null);
    }

    public static TaskExecutorKillResponse fail(String message) {
        return new TaskExecutorKillResponse(false, message);
    }

}
