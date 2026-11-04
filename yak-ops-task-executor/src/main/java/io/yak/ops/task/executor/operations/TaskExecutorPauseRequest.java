package io.yak.ops.task.executor.operations;

import java.io.Serializable;

import lombok.AllArgsConstructor;
import lombok.Data;
import lombok.NoArgsConstructor;

@Data
@NoArgsConstructor
@AllArgsConstructor
public class TaskExecutorPauseRequest implements Serializable {

    private Integer taskInstanceId;

    public static TaskExecutorPauseRequest of(Integer taskInstanceId) {
        return new TaskExecutorPauseRequest(taskInstanceId);
    }
}
