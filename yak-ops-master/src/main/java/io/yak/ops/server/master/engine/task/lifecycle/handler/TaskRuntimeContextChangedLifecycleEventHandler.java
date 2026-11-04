package io.yak.ops.server.master.engine.task.lifecycle.handler;

import io.yak.ops.server.master.engine.ILifecycleEventType;
import io.yak.ops.server.master.engine.task.client.ITaskExecutorClient;
import io.yak.ops.server.master.engine.task.execution.ITaskExecution;
import io.yak.ops.server.master.engine.task.lifecycle.TaskLifecycleEventType;
import io.yak.ops.server.master.engine.task.lifecycle.event.TaskRuntimeContextChangedEvent;
import io.yak.ops.server.master.engine.task.statemachine.ITaskStateAction;
import io.yak.ops.server.master.engine.workflow.execution.IWorkflowExecution;
import io.yak.ops.task.executor.eventbus.ITaskExecutorLifecycleEventReporter;
import io.yak.ops.task.executor.events.TaskExecutorLifecycleEventType;

import org.springframework.stereotype.Component;

import lombok.extern.slf4j.Slf4j;

@Slf4j
@Component
public class TaskRuntimeContextChangedLifecycleEventHandler
        extends
            AbstractTaskLifecycleEventHandler<TaskRuntimeContextChangedEvent> {

    private final ITaskExecutorClient taskExecutorClient;

    public TaskRuntimeContextChangedLifecycleEventHandler(final ITaskExecutorClient taskExecutorClient) {
        this.taskExecutorClient = taskExecutorClient;
    }

    @Override
    public void handle(final ITaskStateAction taskStateAction,
                       final IWorkflowExecution workflowExecution,
                       final ITaskExecution taskExecution,
                       final TaskRuntimeContextChangedEvent event) {
        taskStateAction.onRuntimeContextChangedEvent(workflowExecution, taskExecution, event);

        taskExecutorClient.ackTaskExecutorLifecycleEvent(
                taskExecution,
                new ITaskExecutorLifecycleEventReporter.TaskExecutorLifecycleEventAck(
                        taskExecution.getId(),
                        TaskExecutorLifecycleEventType.RUNTIME_CONTEXT_CHANGE));
    }

    @Override
    public ILifecycleEventType matchEventType() {
        return TaskLifecycleEventType.RUNTIME_CONTEXT_CHANGED;
    }

}
