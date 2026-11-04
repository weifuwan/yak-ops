package io.yak.ops.extract.worker;

import io.yak.ops.extract.base.RpcMethod;
import io.yak.ops.extract.base.RpcService;
import io.yak.ops.extract.worker.transportor.TaskInstanceTriggerSavepointRequest;
import io.yak.ops.extract.worker.transportor.TaskInstanceTriggerSavepointResponse;

@RpcService
public interface IStreamingTaskInstanceOperator {

    @RpcMethod
    TaskInstanceTriggerSavepointResponse triggerSavepoint(TaskInstanceTriggerSavepointRequest taskInstanceTriggerSavepointRequest);

}
