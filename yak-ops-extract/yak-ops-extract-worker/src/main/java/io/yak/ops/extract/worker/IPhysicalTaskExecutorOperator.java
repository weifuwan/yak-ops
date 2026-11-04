package io.yak.ops.extract.worker;

import io.yak.ops.extract.base.RpcMethod;
import io.yak.ops.extract.base.RpcService;
import io.yak.ops.task.executor.eventbus.ITaskExecutorLifecycleEventReporter;
import io.yak.ops.task.executor.operations.TaskExecutorDispatchRequest;
import io.yak.ops.task.executor.operations.TaskExecutorDispatchResponse;
import io.yak.ops.task.executor.operations.TaskExecutorKillRequest;
import io.yak.ops.task.executor.operations.TaskExecutorKillResponse;
import io.yak.ops.task.executor.operations.TaskExecutorPauseRequest;
import io.yak.ops.task.executor.operations.TaskExecutorPauseResponse;
import io.yak.ops.task.executor.operations.TaskExecutorReassignMasterRequest;
import io.yak.ops.task.executor.operations.TaskExecutorReassignMasterResponse;

@RpcService
public interface IPhysicalTaskExecutorOperator {

    @RpcMethod
    TaskExecutorDispatchResponse dispatchTask(final TaskExecutorDispatchRequest taskExecutorDispatchRequest);

    @RpcMethod
    TaskExecutorKillResponse killTask(final TaskExecutorKillRequest taskExecutorKillRequest);

    @RpcMethod
    TaskExecutorPauseResponse pauseTask(final TaskExecutorPauseRequest taskExecutorPauseRequest);

    @RpcMethod
    TaskExecutorReassignMasterResponse reassignWorkflowInstanceHost(final TaskExecutorReassignMasterRequest taskExecutorReassignMasterRequest);

    @RpcMethod
    void ackPhysicalTaskExecutorLifecycleEvent(final ITaskExecutorLifecycleEventReporter.TaskExecutorLifecycleEventAck taskExecutorLifecycleEventAck);

}
