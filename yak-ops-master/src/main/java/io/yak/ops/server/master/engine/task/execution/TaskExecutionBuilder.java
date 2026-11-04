package io.yak.ops.server.master.engine.task.execution;

import io.yak.ops.dao.entity.Project;
import io.yak.ops.dao.entity.TaskDefinition;
import io.yak.ops.dao.entity.TaskInstance;
import io.yak.ops.dao.entity.WorkflowDefinition;
import io.yak.ops.dao.entity.WorkflowInstance;
import io.yak.ops.server.master.engine.WorkflowEventBus;
import io.yak.ops.server.master.engine.graph.IWorkflowExecutionGraph;

import javax.annotation.Nullable;

import org.springframework.context.ApplicationContext;

import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Getter;

@Getter
@Builder
@AllArgsConstructor
public class TaskExecutionBuilder {

    private final IWorkflowExecutionGraph workflowExecutionGraph;
    private final WorkflowDefinition workflowDefinition;
    private final Project project;
    private final WorkflowInstance workflowInstance;
    private final TaskDefinition taskDefinition;
    private final @Nullable TaskInstance taskInstance;

    private final WorkflowEventBus workflowEventBus;
    private final ApplicationContext applicationContext;
}
