package io.yak.ops.extract.master.transportor.workflow;

import lombok.AllArgsConstructor;
import lombok.Data;
import lombok.NoArgsConstructor;

@Data
@AllArgsConstructor
@NoArgsConstructor
public class WorkflowInstancePauseResponse {

    private boolean success;
    private String message;

    public static WorkflowInstancePauseResponse success() {
        return new WorkflowInstancePauseResponse(true, null);
    }

    public static WorkflowInstancePauseResponse fail(String message) {
        return new WorkflowInstancePauseResponse(false, message);
    }
}
