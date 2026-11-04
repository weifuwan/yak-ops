package io.yak.ops.server.master.rpc;

import io.yak.ops.extract.master.ILogicTaskExecutorOperator;
import io.yak.ops.plugin.task.api.TaskExecutionContext;
import io.yak.ops.server.master.engine.executor.LogicTaskEngineDelegator;
import io.yak.ops.task.executor.eventbus.ITaskExecutorLifecycleEventReporter;
import io.yak.ops.task.executor.log.TaskExecutorMDCUtils;
import io.yak.ops.task.executor.operations.TaskExecutorDispatchRequest;
import io.yak.ops.task.executor.operations.TaskExecutorDispatchResponse;
import io.yak.ops.task.executor.operations.TaskExecutorKillRequest;
import io.yak.ops.task.executor.operations.TaskExecutorKillResponse;
import io.yak.ops.task.executor.operations.TaskExecutorPauseRequest;
import io.yak.ops.task.executor.operations.TaskExecutorPauseResponse;

import org.apache.commons.lang3.exception.ExceptionUtils;

import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Service;

import lombok.extern.slf4j.Slf4j;

@Slf4j
@Service
public class LogicTaskExecutorOperatorImpl implements ILogicTaskExecutorOperator {

    @Autowired
    private LogicTaskEngineDelegator logicTaskEngineDelegator;

    @Override
    public TaskExecutorDispatchResponse dispatchTask(final TaskExecutorDispatchRequest taskExecutorDispatchRequest) {
        final TaskExecutionContext taskExecutionContext = taskExecutorDispatchRequest.getTaskExecutionContext();
        try (
                final TaskExecutorMDCUtils.MDCAutoClosable ignore =
                        TaskExecutorMDCUtils.logWithMDC(taskExecutionContext.getTaskInstanceId())) {
            log.info("Receive  {}", taskExecutorDispatchRequest);
            try {
                logicTaskEngineDelegator.dispatchLogicTask(taskExecutionContext);
                log.info("Handle {} success", taskExecutorDispatchRequest);
                return TaskExecutorDispatchResponse.success();
            } catch (Throwable throwable) {
                log.error("Handle {} failed", taskExecutorDispatchRequest, throwable);
                return TaskExecutorDispatchResponse.failed(ExceptionUtils.getMessage(throwable));
            }
        }
    }

    @Override
    public TaskExecutorPauseResponse pauseTask(final TaskExecutorPauseRequest taskPauseRequest) {
        final int taskInstanceId = taskPauseRequest.getTaskInstanceId();
        try (final TaskExecutorMDCUtils.MDCAutoClosable ignore = TaskExecutorMDCUtils.logWithMDC(taskInstanceId)) {
            log.info("Receive {}", taskPauseRequest);
            try {
                logicTaskEngineDelegator.pauseLogicTask(taskInstanceId);
                log.info("Handle {} success", taskPauseRequest);
                return TaskExecutorPauseResponse.success();
            } catch (Throwable throwable) {
                log.error("Handle {} failed", taskPauseRequest, throwable);
                return TaskExecutorPauseResponse.fail(ExceptionUtils.getMessage(throwable));
            }
        }
    }

    @Override
    public void ackTaskExecutorLifecycleEvent(final ITaskExecutorLifecycleEventReporter.TaskExecutorLifecycleEventAck taskExecutorLifecycleEventAck) {
        final int taskExecutorId = taskExecutorLifecycleEventAck.getTaskExecutorId();
        try (final TaskExecutorMDCUtils.MDCAutoClosable ignore = TaskExecutorMDCUtils.logWithMDC(taskExecutorId)) {
            log.info("Receive : {}", taskExecutorLifecycleEventAck);
            logicTaskEngineDelegator.ackLogicTaskExecutionEvent(taskExecutorLifecycleEventAck);
        }
    }

    @Override
    public TaskExecutorKillResponse killTask(final TaskExecutorKillRequest taskKillRequest) {
        final int taskInstanceId = taskKillRequest.getTaskInstanceId();
        try (final TaskExecutorMDCUtils.MDCAutoClosable ignore = TaskExecutorMDCUtils.logWithMDC(taskInstanceId)) {
            log.info("Receive  {}", taskKillRequest);
            try {
                logicTaskEngineDelegator.killLogicTask(taskInstanceId);
                log.info("Handle  {} success", taskKillRequest);
                return TaskExecutorKillResponse.success();
            } catch (Throwable throwable) {
                log.error("Handle  {} failed", taskKillRequest, throwable);
                return TaskExecutorKillResponse.fail(ExceptionUtils.getMessage(throwable));
            }
        }
    }

}
