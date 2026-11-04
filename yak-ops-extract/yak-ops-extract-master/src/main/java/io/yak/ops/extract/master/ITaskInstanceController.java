package io.yak.ops.extract.master;

import io.yak.ops.extract.base.RpcMethod;
import io.yak.ops.extract.base.RpcService;
import io.yak.ops.extract.master.transportor.TaskGroupSlotAcquireSuccessNotifyRequest;
import io.yak.ops.extract.master.transportor.TaskGroupSlotAcquireSuccessNotifyResponse;

/**
 * Task instance controller used to do control operation for task instance.
 */
@RpcService
public interface ITaskInstanceController {

    @RpcMethod
    TaskGroupSlotAcquireSuccessNotifyResponse notifyTaskGroupSlotAcquireSuccess(TaskGroupSlotAcquireSuccessNotifyRequest taskGroupSlotAcquireSuccessNotifyRequest);

}
