package io.yak.ops.task.executor.operations;

import lombok.AllArgsConstructor;
import lombok.Data;
import lombok.NoArgsConstructor;

@Data
@NoArgsConstructor
@AllArgsConstructor
public class TaskExecutorReassignMasterResponse {

    private boolean success;
    private String message;

    public static TaskExecutorReassignMasterResponse success() {
        return new TaskExecutorReassignMasterResponse(true, null);
    }

    public static TaskExecutorReassignMasterResponse failed(String message) {
        return new TaskExecutorReassignMasterResponse(false, message);
    }

}
