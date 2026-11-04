package io.yak.ops.extract.master.transportor.workflow;

import lombok.AllArgsConstructor;
import lombok.Data;
import lombok.NoArgsConstructor;

@Data
@AllArgsConstructor
@NoArgsConstructor
public class WorkflowInstanceRepeatRunningResponse {

    private boolean success;
    private String message;

    public static WorkflowInstanceRepeatRunningResponse success() {
        return new WorkflowInstanceRepeatRunningResponse(true, null);
    }

    public static WorkflowInstanceRepeatRunningResponse fail(String message) {
        return new WorkflowInstanceRepeatRunningResponse(false, message);
    }
}
