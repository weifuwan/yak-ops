package io.yak.ops.server.master.runner;

import io.yak.ops.dao.entity.Command;
import io.yak.ops.dao.entity.WorkflowDefinition;
import io.yak.ops.dao.entity.WorkflowInstance;
import io.yak.ops.server.master.engine.WorkflowEventBus;
import io.yak.ops.server.master.engine.graph.IWorkflowExecutionGraph;
import io.yak.ops.server.master.engine.graph.IWorkflowGraph;
import io.yak.ops.server.master.engine.workflow.listener.IWorkflowLifecycleListener;

import java.util.List;

public interface IWorkflowExecuteContext {

    Command getCommand();

    WorkflowDefinition getWorkflowDefinition();

    WorkflowInstance getWorkflowInstance();

    IWorkflowGraph getWorkflowGraph();

    IWorkflowExecutionGraph getWorkflowExecutionGraph();

    WorkflowEventBus getWorkflowEventBus();

    List<IWorkflowLifecycleListener> getWorkflowInstanceLifecycleListeners();

}
