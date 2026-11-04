package io.yak.ops.extract.master.transportor.workflow;

import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class WorkflowManualTriggerResponse {

    private boolean success;

    private String message;

    private Integer workflowInstanceId;

    public static WorkflowManualTriggerResponse fail(String message) {
        return WorkflowManualTriggerResponse.builder()
                .success(false)
                .message(message)
                .build();
    }

    public static WorkflowManualTriggerResponse success(Integer workflowInstanceId) {
        return WorkflowManualTriggerResponse.builder()
                .success(true)
                .workflowInstanceId(workflowInstanceId)
                .build();
    }
}
