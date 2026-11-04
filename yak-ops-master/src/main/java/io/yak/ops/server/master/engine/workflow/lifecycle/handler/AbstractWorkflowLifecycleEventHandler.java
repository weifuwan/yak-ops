package io.yak.ops.server.master.engine.workflow.lifecycle.handler;

import io.yak.ops.server.master.engine.ILifecycleEventHandler;
import io.yak.ops.server.master.engine.workflow.execution.IWorkflowExecution;
import io.yak.ops.server.master.engine.workflow.lifecycle.AbstractWorkflowLifecycleLifecycleEvent;
import io.yak.ops.server.master.engine.workflow.listener.IWorkflowLifecycleListener;
import io.yak.ops.server.master.engine.workflow.statemachine.IWorkflowStateAction;
import io.yak.ops.server.master.engine.workflow.statemachine.WorkflowStateActionFactory;

import org.apache.commons.collections4.CollectionUtils;

import java.util.List;

import org.springframework.beans.factory.annotation.Autowired;

import lombok.extern.slf4j.Slf4j;

@Slf4j
public abstract class AbstractWorkflowLifecycleEventHandler<T extends AbstractWorkflowLifecycleLifecycleEvent>
        implements
            ILifecycleEventHandler<T> {

    @Autowired
    private WorkflowStateActionFactory workflowStateActionFactory;

    @Override
    public void handle(final IWorkflowExecution workflowExecution, final T event) {
        final IWorkflowStateAction action = workflowStateActionFactory.getAction(workflowExecution.getState());

        log.info("Begin fire workflow {} LifecycleEvent[{}] with state: {}",
                workflowExecution.getName(),
                event,
                workflowExecution.getState().name());
        handle(action, workflowExecution, event);
        log.info("Fired workflow {} LifecycleEvent[{}] with state: {}",
                workflowExecution.getName(),
                event,
                workflowExecution.getState().name());
        doTriggerWorkflowLifecycleListener(workflowExecution, event);
    }

    public abstract void handle(
                                final IWorkflowStateAction workflowStateAction,
                                final IWorkflowExecution workflowExecution,
                                final T event);

    private void doTriggerWorkflowLifecycleListener(
                                                    final IWorkflowExecution workflowExecution,
                                                    final T event) {
        final List<IWorkflowLifecycleListener> listeners = workflowExecution.getWorkflowLifecycleListeners();
        if (CollectionUtils.isEmpty(listeners)) {
            return;
        }
        for (final IWorkflowLifecycleListener listener : listeners) {
            try {
                if (listener.match(event)) {
                    listener.notifyWorkflowLifecycleEvent(workflowExecution, event);
                }
            } catch (Exception e) {
                log.warn("Trigger WorkflowLifecycleListener on event: {} failed", event, e);
            }
        }
    }

}
