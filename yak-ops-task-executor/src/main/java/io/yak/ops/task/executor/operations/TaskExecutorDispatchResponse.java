package io.yak.ops.task.executor.operations;

import lombok.AllArgsConstructor;
import lombok.Data;
import lombok.NoArgsConstructor;

@Data
@NoArgsConstructor
@AllArgsConstructor
public class TaskExecutorDispatchResponse {

    private boolean dispatchSuccess;

    private String message;

    public static TaskExecutorDispatchResponse success() {
        return new TaskExecutorDispatchResponse(true, null);
    }

    public static TaskExecutorDispatchResponse failed(String message) {
        return new TaskExecutorDispatchResponse(false, message);
    }
}
