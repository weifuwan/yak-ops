package io.yak.ops.server.master.engine.task.lifecycle.handler;

import io.yak.ops.server.master.engine.ILifecycleEventType;
import io.yak.ops.server.master.engine.task.client.TaskExecutorClient;
import io.yak.ops.server.master.engine.task.execution.ITaskExecution;
import io.yak.ops.server.master.engine.task.lifecycle.TaskLifecycleEventType;
import io.yak.ops.server.master.engine.task.lifecycle.event.TaskKilledLifecycleEvent;
import io.yak.ops.server.master.engine.task.statemachine.ITaskStateAction;
import io.yak.ops.server.master.engine.workflow.execution.IWorkflowExecution;
import io.yak.ops.task.executor.eventbus.ITaskExecutorLifecycleEventReporter;
import io.yak.ops.task.executor.events.TaskExecutorLifecycleEventType;

import org.springframework.stereotype.Component;

@Component
public class TaskKilledLifecycleEventHandler extends AbstractTaskLifecycleEventHandler<TaskKilledLifecycleEvent> {

    private final TaskExecutorClient taskExecutorClient;

    public TaskKilledLifecycleEventHandler(final TaskExecutorClient taskExecutorClient) {
        this.taskExecutorClient = taskExecutorClient;
    }

    @Override
    public void handle(final ITaskStateAction taskStateAction,
                       final IWorkflowExecution workflowExecution,
                       final ITaskExecution taskExecution,
                       final TaskKilledLifecycleEvent taskKilledEvent) {
        taskStateAction.onKilledEvent(workflowExecution, taskExecution, taskKilledEvent);
        taskExecutorClient.ackTaskExecutorLifecycleEvent(
                taskExecution,
                new ITaskExecutorLifecycleEventReporter.TaskExecutorLifecycleEventAck(
                        taskExecution.getId(),
                        TaskExecutorLifecycleEventType.KILLED));
    }

    @Override
    public ILifecycleEventType matchEventType() {
        return TaskLifecycleEventType.KILLED;
    }
}
