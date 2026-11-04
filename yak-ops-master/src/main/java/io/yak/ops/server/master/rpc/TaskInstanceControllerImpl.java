package io.yak.ops.server.master.rpc;

import io.yak.ops.extract.master.ITaskInstanceController;
import io.yak.ops.extract.master.transportor.TaskGroupSlotAcquireSuccessNotifyRequest;
import io.yak.ops.extract.master.transportor.TaskGroupSlotAcquireSuccessNotifyResponse;
import io.yak.ops.plugin.task.api.utils.LogUtils;
import io.yak.ops.server.master.engine.IWorkflowRepository;
import io.yak.ops.server.master.engine.task.execution.ITaskExecution;
import io.yak.ops.server.master.engine.task.lifecycle.event.TaskDispatchLifecycleEvent;
import io.yak.ops.server.master.engine.workflow.execution.IWorkflowExecution;

import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Component;

import lombok.extern.slf4j.Slf4j;

@Slf4j
@Component
public class TaskInstanceControllerImpl implements ITaskInstanceController {

    @Autowired
    private IWorkflowRepository workflowExecutionMemoryRepository;

    @Override
    public TaskGroupSlotAcquireSuccessNotifyResponse notifyTaskGroupSlotAcquireSuccess(
                                                                                       final TaskGroupSlotAcquireSuccessNotifyRequest taskGroupSlotAcquireSuccessNotifyRequest) {
        log.info("Received TaskGroupSlotAcquireSuccessRequest request{}", taskGroupSlotAcquireSuccessNotifyRequest);
        try {
            final int workflowInstanceId = taskGroupSlotAcquireSuccessNotifyRequest.getWorkflowInstanceId();
            final int taskInstanceId = taskGroupSlotAcquireSuccessNotifyRequest.getTaskInstanceId();
            LogUtils.setWorkflowAndTaskInstanceIDMDC(workflowInstanceId, taskInstanceId);
            final IWorkflowExecution workflowExecution =
                    workflowExecutionMemoryRepository.get(workflowInstanceId);
            if (workflowExecution == null) {
                log.warn("cannot find WorkflowExecuteRunnable: {}, no need to Wakeup task", workflowInstanceId);
                return TaskGroupSlotAcquireSuccessNotifyResponse
                        .failed("cannot find WorkflowExecuteRunnable: " + workflowInstanceId);
            }
            final ITaskExecution taskExecution = workflowExecution
                    .getWorkflowExecuteContext()
                    .getWorkflowExecutionGraph()
                    .getTaskExecutionById(taskInstanceId);
            if (taskExecution == null) {
                log.warn("Cannot find TaskExecution: {}, no need to Wakeup task", taskInstanceId);
                return TaskGroupSlotAcquireSuccessNotifyResponse
                        .failed("Cannot find TaskExecution: " + taskInstanceId);
            }
            workflowExecution.getWorkflowEventBus()
                    .publish(TaskDispatchLifecycleEvent.of(taskExecution));
            log.info("Success Wakeup TaskInstance: {}", taskInstanceId);
            return TaskGroupSlotAcquireSuccessNotifyResponse.success();
        } finally {
            LogUtils.removeWorkflowAndTaskInstanceIdMDC();
        }
    }
}
