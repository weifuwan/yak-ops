package io.yak.ops.server.master.engine.task.statemachine;

import io.yak.ops.plugin.task.api.enums.TaskExecutionStatus;

import org.springframework.stereotype.Component;

import lombok.extern.slf4j.Slf4j;

/**
 * The action to be taken when a task is in the DELAY_EXECUTION state.
 */
@Slf4j
@Component
public class TaskDelayExecutionStateAction extends TaskSubmittedStateAction {

    @Override
    public TaskExecutionStatus matchState() {
        return TaskExecutionStatus.DELAY_EXECUTION;
    }
}
