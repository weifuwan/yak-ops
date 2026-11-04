package io.yak.ops.api.dto.workflowInstance;

import io.yak.ops.dao.entity.AbstractTaskInstanceContext;
import io.yak.ops.dao.entity.TaskInstanceDependentDetails;

import java.util.List;

import lombok.AllArgsConstructor;
import lombok.Data;
import lombok.NoArgsConstructor;

@Data
@NoArgsConstructor
@AllArgsConstructor
public class WorkflowInstanceTaskListDTO {

    private String workflowInstanceState;

    private List<TaskInstanceDependentDetails<AbstractTaskInstanceContext>> taskList;
}
