package io.yak.ops.extract.master;

import io.yak.ops.extract.base.RpcMethod;
import io.yak.ops.extract.base.RpcService;
import io.yak.ops.task.executor.eventbus.ITaskExecutorLifecycleEventReporter;
import io.yak.ops.task.executor.operations.TaskExecutorDispatchRequest;
import io.yak.ops.task.executor.operations.TaskExecutorDispatchResponse;
import io.yak.ops.task.executor.operations.TaskExecutorKillRequest;
import io.yak.ops.task.executor.operations.TaskExecutorKillResponse;
import io.yak.ops.task.executor.operations.TaskExecutorPauseRequest;
import io.yak.ops.task.executor.operations.TaskExecutorPauseResponse;

@RpcService
public interface ILogicTaskExecutorOperator {

    @RpcMethod
    TaskExecutorDispatchResponse dispatchTask(final TaskExecutorDispatchRequest taskExecutorDispatchRequest);

    @RpcMethod
    TaskExecutorKillResponse killTask(final TaskExecutorKillRequest taskExecutorKillRequest);

    @RpcMethod
    TaskExecutorPauseResponse pauseTask(final TaskExecutorPauseRequest taskExecutorPauseRequest);

    @RpcMethod
    void ackTaskExecutorLifecycleEvent(final ITaskExecutorLifecycleEventReporter.TaskExecutorLifecycleEventAck taskExecutorLifecycleEventAck);

}
