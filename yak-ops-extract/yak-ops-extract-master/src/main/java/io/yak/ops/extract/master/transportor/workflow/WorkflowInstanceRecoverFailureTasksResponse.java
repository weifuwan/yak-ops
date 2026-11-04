package io.yak.ops.extract.master.transportor.workflow;

import lombok.AllArgsConstructor;
import lombok.Data;
import lombok.NoArgsConstructor;

@Data
@AllArgsConstructor
@NoArgsConstructor
public class WorkflowInstanceRecoverFailureTasksResponse {

    private boolean success;
    private String message;

    public static WorkflowInstanceRecoverFailureTasksResponse success() {
        return new WorkflowInstanceRecoverFailureTasksResponse(true, null);
    }

    public static WorkflowInstanceRecoverFailureTasksResponse fail(String message) {
        return new WorkflowInstanceRecoverFailureTasksResponse(false, message);
    }
}
