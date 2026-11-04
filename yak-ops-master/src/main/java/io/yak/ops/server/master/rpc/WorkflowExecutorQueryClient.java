package io.yak.ops.server.master.rpc;

import io.yak.ops.extract.master.IWorkflowExecutorQueryClient;
import io.yak.ops.extract.master.transportor.WorkflowExecutorQueryRequest;
import io.yak.ops.extract.master.transportor.WorkflowExecutorQueryResponse;
import io.yak.ops.server.master.engine.WorkflowEngine;

import org.apache.commons.lang3.exception.ExceptionUtils;

import org.springframework.stereotype.Service;

import lombok.extern.slf4j.Slf4j;

@Slf4j
@Service
public class WorkflowExecutorQueryClient implements IWorkflowExecutorQueryClient {

    private final WorkflowEngine workflowEngine;

    public WorkflowExecutorQueryClient(WorkflowEngine workflowEngine) {
        this.workflowEngine = workflowEngine;
    }

    @Override
    public WorkflowExecutorQueryResponse queryWorkflowExecutors(WorkflowExecutorQueryRequest request) {
        try {
            return WorkflowExecutorQueryResponse.success(workflowEngine.queryWorkflowExecutors());
        } catch (Exception ex) {
            log.error("Query running workflow instances failed", ex);
            return WorkflowExecutorQueryResponse.fail(
                    "Query running workflow instances failed: " + ExceptionUtils.getMessage(ex));
        }
    }

}
