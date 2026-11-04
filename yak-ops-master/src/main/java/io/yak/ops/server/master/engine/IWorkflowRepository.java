package io.yak.ops.server.master.engine;

import io.yak.ops.server.master.engine.workflow.execution.IWorkflowExecution;

import java.util.Collection;

import lombok.NonNull;

public interface IWorkflowRepository {

    IWorkflowExecution get(int workflowInstanceId);

    Collection<IWorkflowExecution> getAll();

    void put(@NonNull IWorkflowExecution workflowExecuteThread);

    boolean contains(int workflowInstanceId);

    void remove(int workflowInstanceId);

}
