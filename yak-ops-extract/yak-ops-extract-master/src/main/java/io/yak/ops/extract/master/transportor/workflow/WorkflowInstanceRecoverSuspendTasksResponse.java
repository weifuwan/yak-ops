package io.yak.ops.extract.master.transportor.workflow;

import lombok.AllArgsConstructor;
import lombok.Data;
import lombok.NoArgsConstructor;

@Data
@AllArgsConstructor
@NoArgsConstructor
public class WorkflowInstanceRecoverSuspendTasksResponse {

    private boolean success;
    private String message;

    public static WorkflowInstanceRecoverSuspendTasksResponse success() {
        return new WorkflowInstanceRecoverSuspendTasksResponse(true, null);
    }

    public static WorkflowInstanceRecoverSuspendTasksResponse fail(String message) {
        return new WorkflowInstanceRecoverSuspendTasksResponse(false, message);
    }
}
