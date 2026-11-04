package io.yak.ops.extract.master;

import io.yak.ops.extract.base.RpcMethod;
import io.yak.ops.extract.base.RpcService;

@RpcService
public interface IWorkflowMetricService {

    @RpcMethod
    void clearWorkflowMetrics(Long workflowDefinitionCode);

}
