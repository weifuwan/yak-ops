package io.yak.ops.server.master.engine.task.execution;

import io.yak.ops.dao.entity.Project;
import io.yak.ops.dao.entity.TaskDefinition;
import io.yak.ops.dao.entity.TaskInstance;
import io.yak.ops.dao.entity.WorkflowDefinition;
import io.yak.ops.dao.entity.WorkflowInstance;
import io.yak.ops.server.master.engine.graph.IWorkflowExecutionGraph;

import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Getter;

@Getter
@Builder
@AllArgsConstructor
public class TaskExecutionContextCreateRequest {

    private IWorkflowExecutionGraph workflowExecutionGraph;
    private WorkflowDefinition workflowDefinition;
    private WorkflowInstance workflowInstance;
    private TaskDefinition taskDefinition;
    private TaskInstance taskInstance;
    private Project project;

}
