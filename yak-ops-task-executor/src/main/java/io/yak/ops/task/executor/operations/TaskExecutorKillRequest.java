package io.yak.ops.task.executor.operations;

import java.io.Serializable;

import lombok.AllArgsConstructor;
import lombok.Data;
import lombok.NoArgsConstructor;

@Data
@NoArgsConstructor
@AllArgsConstructor
public class TaskExecutorKillRequest implements Serializable {

    private Integer taskInstanceId;

    public static TaskExecutorKillRequest of(Integer taskInstanceId) {
        return new TaskExecutorKillRequest(taskInstanceId);
    }

}
