package io.yak.ops.extract.master.transportor.workflow;

import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class WorkflowScheduleTriggerResponse {

    private boolean success;

    private String message;

    private Integer workflowInstanceId;

    public static WorkflowScheduleTriggerResponse fail(String message) {
        return WorkflowScheduleTriggerResponse.builder()
                .success(false)
                .message(message)
                .build();
    }

    public static WorkflowScheduleTriggerResponse success(Integer workflowInstanceId) {
        return WorkflowScheduleTriggerResponse.builder()
                .success(true)
                .workflowInstanceId(workflowInstanceId)
                .build();
    }

}
