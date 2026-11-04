package io.yak.ops.server.master.engine.task.client;

import static com.google.common.base.Preconditions.checkArgument;

import io.yak.ops.dao.entity.TaskInstance;
import io.yak.ops.plugin.task.api.utils.TaskTypeUtils;
import io.yak.ops.server.master.engine.exceptions.TaskKillException;
import io.yak.ops.server.master.engine.exceptions.TaskPauseException;
import io.yak.ops.server.master.engine.exceptions.TaskReassignMasterHostException;
import io.yak.ops.server.master.engine.task.execution.ITaskExecution;
import io.yak.ops.server.master.exception.dispatch.TaskDispatchException;
import io.yak.ops.task.executor.eventbus.ITaskExecutorLifecycleEventReporter;

import org.apache.commons.lang3.StringUtils;

import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Component;

import lombok.extern.slf4j.Slf4j;

/**
 * The client of task executor, used to communicate with task executor.
 */
@Slf4j
@Component
public class TaskExecutorClient implements ITaskExecutorClient {

    @Autowired
    private LogicTaskExecutorClientDelegator logicTaskExecutorClientDelegator;

    @Autowired
    private PhysicalTaskExecutorClientDelegator physicalTaskExecutorClientDelegator;

    @Override
    public void dispatch(ITaskExecution taskExecution) throws TaskDispatchException {
        try {
            getTaskExecutorClientDelegator(taskExecution).dispatch(taskExecution);
        } catch (TaskDispatchException taskDispatchException) {
            throw taskDispatchException;
        } catch (Exception ex) {
            throw new TaskDispatchException("Dispatch task: " + taskExecution.getName() + " to executor failed",
                    ex);
        }
    }

    @Override
    public boolean reassignWorkflowInstanceHost(final ITaskExecution taskExecution) throws TaskReassignMasterHostException {
        try {
            return getTaskExecutorClientDelegator(taskExecution)
                    .reassignMasterHost(taskExecution);
        } catch (Exception ex) {
            throw new TaskReassignMasterHostException(
                    "Take over task: " + taskExecution.getName() + " from executor failed",
                    ex);
        }
    }

    @Override
    public void pause(final ITaskExecution taskExecution) throws TaskPauseException {
        try {
            getTaskExecutorClientDelegator(taskExecution).pause(taskExecution);
        } catch (Exception ex) {
            throw new TaskPauseException("Pause task: " + taskExecution.getName() + " from executor failed",
                    ex);
        }
    }

    @Override
    public void kill(final ITaskExecution taskExecution) throws TaskKillException {
        try {
            getTaskExecutorClientDelegator(taskExecution).kill(taskExecution);
        } catch (Exception ex) {
            throw new TaskKillException("Kill task: " + taskExecution.getName() + " from executor failed", ex);
        }
    }

    @Override
    public void ackTaskExecutorLifecycleEvent(
                                              final ITaskExecution taskExecution,
                                              final ITaskExecutorLifecycleEventReporter.TaskExecutorLifecycleEventAck taskExecutorLifecycleEventAck) {
        try {
            if (StringUtils.isEmpty(taskExecution.getTaskInstance().getHost())) {
                log.info("The task: {} is didn't dispatched to executor, skip ack taskExecutorLifecycleEventAck: {}",
                        taskExecution.getName(), taskExecutorLifecycleEventAck);
                return;
            }
            getTaskExecutorClientDelegator(taskExecution)
                    .ackTaskExecutorLifecycleEvent(taskExecution, taskExecutorLifecycleEventAck);
        } catch (Exception ex) {
            log.error("Send taskExecutorLifecycleEventAck: {} failed", taskExecutorLifecycleEventAck, ex);
        }
    }

    private ITaskExecutorClientDelegator getTaskExecutorClientDelegator(final ITaskExecution taskExecution) {
        final TaskInstance taskInstance = taskExecution.getTaskInstance();
        checkArgument(taskInstance != null, "taskType cannot be empty");
        if (TaskTypeUtils.isLogicTask(taskInstance.getTaskType())) {
            return logicTaskExecutorClientDelegator;
        }
        return physicalTaskExecutorClientDelegator;
    }
}
