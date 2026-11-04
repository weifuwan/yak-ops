package io.yak.ops.task.executor;

import io.yak.ops.plugin.task.api.enums.TaskExecutionStatus;

public class TaskExecutorStateMappings {

    // todo: we should use TaskExecutorState in task module the TaskExecutionStatus is a logic state in master
    public static TaskExecutorState mapState(final TaskExecutionStatus taskExecutionStatus) {
        switch (taskExecutionStatus) {
            case RUNNING_EXECUTION:
                return TaskExecutorState.RUNNING;
            case SUCCESS:
                return TaskExecutorState.SUCCEEDED;
            case FAILURE:
                return TaskExecutorState.FAILED;
            case KILL:
                return TaskExecutorState.KILLED;
            case PAUSE:
                return TaskExecutorState.PAUSED;
            default:
                return TaskExecutorState.INITIALIZED;
        }
    }

}
