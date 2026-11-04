package io.yak.ops.task.executor.operations;

import io.yak.ops.plugin.task.api.TaskExecutionContext;

import java.io.Serializable;

import lombok.AllArgsConstructor;
import lombok.Data;
import lombok.NoArgsConstructor;

@Data
@NoArgsConstructor
@AllArgsConstructor
public class TaskExecutorDispatchRequest implements Serializable {

    private static final long serialVersionUID = -1L;

    private TaskExecutionContext taskExecutionContext;

    public static TaskExecutorDispatchRequest of(TaskExecutionContext taskExecutionContext) {
        return new TaskExecutorDispatchRequest(taskExecutionContext);
    }
}
