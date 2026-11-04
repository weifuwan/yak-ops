package io.yak.ops.server.worker.rpc;

import io.yak.ops.extract.worker.IPhysicalTaskExecutorOperator;
import io.yak.ops.plugin.task.api.TaskExecutionContext;
import io.yak.ops.server.worker.executor.PhysicalTaskEngineDelegator;
import io.yak.ops.task.executor.eventbus.ITaskExecutorLifecycleEventReporter;
import io.yak.ops.task.executor.operations.TaskExecutorDispatchRequest;
import io.yak.ops.task.executor.operations.TaskExecutorDispatchResponse;
import io.yak.ops.task.executor.operations.TaskExecutorKillRequest;
import io.yak.ops.task.executor.operations.TaskExecutorKillResponse;
import io.yak.ops.task.executor.operations.TaskExecutorPauseRequest;
import io.yak.ops.task.executor.operations.TaskExecutorPauseResponse;
import io.yak.ops.task.executor.operations.TaskExecutorReassignMasterRequest;
import io.yak.ops.task.executor.operations.TaskExecutorReassignMasterResponse;

import org.apache.commons.lang3.exception.ExceptionUtils;

import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Component;

import lombok.extern.slf4j.Slf4j;

@Slf4j
@Component
public class PhysicalTaskExecutorOperatorImpl implements IPhysicalTaskExecutorOperator {

    @Autowired
    private PhysicalTaskEngineDelegator physicalTaskEngineDelegator;

    @Override
    public TaskExecutorDispatchResponse dispatchTask(final TaskExecutorDispatchRequest taskExecutorDispatchRequest) {
        log.info("Receive TaskExecutorDispatchResponse: {}", taskExecutorDispatchRequest);
        final TaskExecutionContext taskExecutionContext = taskExecutorDispatchRequest.getTaskExecutionContext();
        try {
            physicalTaskEngineDelegator.dispatchLogicTask(taskExecutionContext);
            log.info("Handle TaskExecutorDispatchResponse: {} success", taskExecutorDispatchRequest);
            return TaskExecutorDispatchResponse.success();
        } catch (Throwable throwable) {
            log.error("Handle TaskExecutorDispatchResponse: {} failed", taskExecutorDispatchRequest, throwable);
            return TaskExecutorDispatchResponse.failed(ExceptionUtils.getMessage(throwable));
        }
    }

    @Override
    public TaskExecutorKillResponse killTask(final TaskExecutorKillRequest taskExecutorKillRequest) {
        log.info("Receive TaskExecutorKillRequest: {}", taskExecutorKillRequest);
        final int taskInstanceId = taskExecutorKillRequest.getTaskInstanceId();
        try {
            physicalTaskEngineDelegator.killLogicTask(taskInstanceId);
            log.info("Handle TaskExecutorKillRequest: {} success", taskExecutorKillRequest);
            return TaskExecutorKillResponse.success();
        } catch (Throwable throwable) {
            log.error("Handle TaskExecutorKillRequest: {} failed", taskExecutorKillRequest, throwable);
            return TaskExecutorKillResponse.fail(ExceptionUtils.getMessage(throwable));
        }
    }

    @Override
    public TaskExecutorPauseResponse pauseTask(final TaskExecutorPauseRequest taskPauseRequest) {
        log.info("Receive TaskExecutorPauseRequest: {}", taskPauseRequest);
        final int taskInstanceId = taskPauseRequest.getTaskInstanceId();
        try {
            physicalTaskEngineDelegator.pauseLogicTask(taskInstanceId);
            log.info("Handle TaskExecutorPauseRequest: {} success", taskPauseRequest);
            return TaskExecutorPauseResponse.success();
        } catch (Throwable throwable) {
            log.error("Handle TaskExecutorPauseRequest: {} failed", taskPauseRequest, throwable);
            return TaskExecutorPauseResponse.fail(ExceptionUtils.getMessage(throwable));
        }
    }

    @Override
    public TaskExecutorReassignMasterResponse reassignWorkflowInstanceHost(final TaskExecutorReassignMasterRequest taskExecutorReassignMasterRequest) {
        boolean success =
                physicalTaskEngineDelegator.reassignWorkflowInstanceHost(taskExecutorReassignMasterRequest);
        if (success) {
            return TaskExecutorReassignMasterResponse.success();
        }
        return TaskExecutorReassignMasterResponse.failed("Reassign master host failed");
    }

    @Override
    public void ackPhysicalTaskExecutorLifecycleEvent(final ITaskExecutorLifecycleEventReporter.TaskExecutorLifecycleEventAck taskExecutorLifecycleEventAck) {
        log.info("Receive TaskExecutorLifecycleEventAck: {}", taskExecutorLifecycleEventAck);
        physicalTaskEngineDelegator.ackPhysicalTaskExecutorLifecycleEventACK(taskExecutorLifecycleEventAck);
    }
}
