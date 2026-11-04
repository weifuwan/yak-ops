package io.yak.ops.server.master.engine.task.statemachine;

import io.yak.ops.plugin.task.api.enums.TaskExecutionStatus;

import java.util.Arrays;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

import org.springframework.stereotype.Component;

@Component
public class TaskStateActionFactory {

    private final Map<TaskExecutionStatus, ITaskStateAction> taskStateActionMap = new HashMap<>();

    public TaskStateActionFactory(List<ITaskStateAction> taskStateActions) {
        taskStateActions.forEach(
                taskStateAction -> taskStateActionMap.put(taskStateAction.matchState(), taskStateAction));
        Arrays.stream(TaskExecutionStatus.values()).forEach(this::getTaskStateAction);
    }

    public ITaskStateAction getTaskStateAction(final TaskExecutionStatus taskExecutionStatus) {
        final ITaskStateAction taskStateAction = taskStateActionMap.get(taskExecutionStatus);
        if (taskStateAction == null) {
            throw new IllegalArgumentException("Cannot find TaskStateAction for state: " + taskExecutionStatus);
        }
        return taskStateAction;
    }

}
