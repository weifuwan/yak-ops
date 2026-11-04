package io.yak.ops.extract.master.transportor.workflow;

import lombok.AllArgsConstructor;
import lombok.Data;
import lombok.NoArgsConstructor;

@Data
@AllArgsConstructor
@NoArgsConstructor
public class WorkflowInstanceStopResponse {

    private boolean success;
    private String message;

    public static WorkflowInstanceStopResponse success() {
        return new WorkflowInstanceStopResponse(true, null);
    }

    public static WorkflowInstanceStopResponse fail(String message) {
        return new WorkflowInstanceStopResponse(false, message);
    }
}
