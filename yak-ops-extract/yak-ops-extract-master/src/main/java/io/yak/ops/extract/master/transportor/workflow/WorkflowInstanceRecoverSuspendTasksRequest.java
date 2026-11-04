package io.yak.ops.extract.master.transportor.workflow;

import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class WorkflowInstanceRecoverSuspendTasksRequest {

    private Integer workflowInstanceId;

    private Integer userId;

}
