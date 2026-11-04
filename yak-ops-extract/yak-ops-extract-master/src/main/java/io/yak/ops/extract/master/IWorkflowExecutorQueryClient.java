package io.yak.ops.extract.master;

import io.yak.ops.extract.base.RpcMethod;
import io.yak.ops.extract.base.RpcService;
import io.yak.ops.extract.master.transportor.WorkflowExecutorQueryRequest;
import io.yak.ops.extract.master.transportor.WorkflowExecutorQueryResponse;

@RpcService
public interface IWorkflowExecutorQueryClient {

    @RpcMethod
    WorkflowExecutorQueryResponse queryWorkflowExecutors(WorkflowExecutorQueryRequest request);

}
