package io.yak.ops.server.master.runner;

import io.yak.ops.dao.entity.Command;
import io.yak.ops.dao.entity.Project;
import io.yak.ops.dao.entity.WorkflowDefinition;
import io.yak.ops.dao.entity.WorkflowInstance;
import io.yak.ops.server.master.engine.WorkflowEventBus;
import io.yak.ops.server.master.engine.graph.IWorkflowExecutionGraph;
import io.yak.ops.server.master.engine.graph.IWorkflowGraph;
import io.yak.ops.server.master.engine.workflow.listener.IWorkflowLifecycleListener;

import java.util.Collections;
import java.util.List;
import java.util.Optional;

import lombok.AllArgsConstructor;
import lombok.Data;
import lombok.Getter;
import lombok.NoArgsConstructor;

@Getter
@AllArgsConstructor
public class WorkflowExecuteContext implements IWorkflowExecuteContext {

    private final Command command;

    private final WorkflowDefinition workflowDefinition;

    private final Project project;

    private final WorkflowInstance workflowInstance;

    private final IWorkflowGraph workflowGraph;

    private final IWorkflowExecutionGraph workflowExecutionGraph;

    private final WorkflowEventBus workflowEventBus;

    private final List<IWorkflowLifecycleListener> workflowInstanceLifecycleListeners;

    public static WorkflowExecuteContextBuilder builder() {
        return new WorkflowExecuteContextBuilder();
    }

    @Data
    @NoArgsConstructor
    public static class WorkflowExecuteContextBuilder {

        private Command command;

        private WorkflowDefinition workflowDefinition;

        private WorkflowInstance workflowInstance;

        private IWorkflowGraph workflowGraph;

        private IWorkflowExecutionGraph workflowExecutionGraph;

        private WorkflowEventBus workflowEventBus;

        private List<IWorkflowLifecycleListener> workflowInstanceLifecycleListeners;

        private Project project;

        public WorkflowExecuteContextBuilder withCommand(Command command) {
            this.command = command;
            return this;
        }

        public WorkflowExecuteContext build() {
            return new WorkflowExecuteContext(
                    command,
                    workflowDefinition,
                    project,
                    workflowInstance,
                    workflowGraph,
                    workflowExecutionGraph,
                    workflowEventBus,
                    Optional.ofNullable(workflowInstanceLifecycleListeners).orElse(Collections.emptyList()));
        }
    }

}
