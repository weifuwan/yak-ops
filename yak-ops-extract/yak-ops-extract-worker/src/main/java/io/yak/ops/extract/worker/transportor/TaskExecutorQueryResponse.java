package io.yak.ops.extract.worker.transportor;

import io.yak.ops.task.executor.dto.TaskExecutorDTO;

import java.util.List;

import lombok.AllArgsConstructor;
import lombok.Data;
import lombok.NoArgsConstructor;

@Data
@NoArgsConstructor
@AllArgsConstructor
public class TaskExecutorQueryResponse {

    private boolean success;

    private String message;

    private List<TaskExecutorDTO> taskExecutors;

    public static TaskExecutorQueryResponse success(List<TaskExecutorDTO> taskExecutors) {
        return new TaskExecutorQueryResponse(true, null, taskExecutors);
    }

    public static TaskExecutorQueryResponse fail(String message) {
        return new TaskExecutorQueryResponse(false, message, null);
    }

}
