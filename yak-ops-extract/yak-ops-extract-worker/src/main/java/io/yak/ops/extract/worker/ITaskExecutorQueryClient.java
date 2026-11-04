package io.yak.ops.extract.worker;

import io.yak.ops.extract.base.RpcMethod;
import io.yak.ops.extract.base.RpcService;
import io.yak.ops.extract.worker.transportor.TaskExecutorQueryRequest;
import io.yak.ops.extract.worker.transportor.TaskExecutorQueryResponse;

@RpcService
public interface ITaskExecutorQueryClient {

    @RpcMethod
    TaskExecutorQueryResponse queryTaskInstances(final TaskExecutorQueryRequest request);

}
