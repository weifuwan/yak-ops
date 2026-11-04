package io.yak.ops.extract.master.transportor;

import io.yak.ops.extract.master.dto.WorkflowExecutorDTO;

import java.util.List;

import lombok.AllArgsConstructor;
import lombok.Data;
import lombok.NoArgsConstructor;

@Data
@NoArgsConstructor
@AllArgsConstructor
public class WorkflowExecutorQueryResponse {

    private boolean success;

    private String message;

    private List<WorkflowExecutorDTO> workflowExecutors;

    public static WorkflowExecutorQueryResponse success(List<WorkflowExecutorDTO> workflowExecutors) {
        return new WorkflowExecutorQueryResponse(true, null, workflowExecutors);
    }

    public static WorkflowExecutorQueryResponse fail(String message) {
        return new WorkflowExecutorQueryResponse(false, message, null);
    }

}
