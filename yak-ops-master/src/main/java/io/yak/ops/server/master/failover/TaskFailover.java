package io.yak.ops.server.master.failover;

import io.yak.ops.plugin.task.api.utils.LogUtils;
import io.yak.ops.server.master.engine.task.execution.ITaskExecution;
import io.yak.ops.server.master.engine.task.lifecycle.event.TaskFailoverLifecycleEvent;

import org.springframework.stereotype.Component;

@Component
public class TaskFailover {

    public void failoverTask(final ITaskExecution taskExecution) {
        LogUtils.setWorkflowInstanceIdMDC(taskExecution.getWorkflowInstance().getId());
        taskExecution.getWorkflowEventBus().publish(TaskFailoverLifecycleEvent.of(taskExecution));
        LogUtils.removeWorkflowInstanceIdMDC();
    }

}
