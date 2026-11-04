package io.yak.ops.extract.master.transportor.workflow;

import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class WorkflowBackfillTriggerResponse {

    private boolean success;

    private String message;

    private Integer workflowInstanceId;

    public static WorkflowBackfillTriggerResponse fail(String message) {
        return WorkflowBackfillTriggerResponse.builder()
                .success(false)
                .message(message)
                .build();
    }

    public static WorkflowBackfillTriggerResponse success(Integer workflowInstanceId) {
        return WorkflowBackfillTriggerResponse.builder()
                .success(true)
                .workflowInstanceId(workflowInstanceId)
                .build();
    }

}
