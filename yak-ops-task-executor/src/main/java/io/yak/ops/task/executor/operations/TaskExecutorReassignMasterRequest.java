package io.yak.ops.task.executor.operations;

import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class TaskExecutorReassignMasterRequest {

    private int taskInstanceId;

    private String workflowHost;

}
