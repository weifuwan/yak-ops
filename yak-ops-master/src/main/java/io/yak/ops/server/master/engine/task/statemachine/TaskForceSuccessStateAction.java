package io.yak.ops.server.master.engine.task.statemachine;

import io.yak.ops.plugin.task.api.enums.TaskExecutionStatus;
import io.yak.ops.server.master.engine.task.execution.ITaskExecution;
import io.yak.ops.server.master.engine.task.lifecycle.event.TaskSuccessLifecycleEvent;

import org.springframework.stereotype.Component;

import lombok.extern.slf4j.Slf4j;

@Slf4j
@Component
public class TaskForceSuccessStateAction extends TaskSuccessStateAction {

    @Override
    protected void persistentTaskInstanceSuccessEventToDB(final ITaskExecution taskExecution,
                                                          final TaskSuccessLifecycleEvent taskSuccessEvent) {
        // FORCED_SUCCESS is already persisted by the API.
        // Skip the SUCCESS update while retaining variable merging and downstream transitions.
    }

    @Override
    public TaskExecutionStatus matchState() {
        return TaskExecutionStatus.FORCED_SUCCESS;
    }
}
