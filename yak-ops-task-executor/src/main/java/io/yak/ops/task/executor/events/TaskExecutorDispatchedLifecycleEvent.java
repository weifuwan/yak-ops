package io.yak.ops.task.executor.events;

import io.yak.ops.plugin.task.api.TaskExecutionContext;
import io.yak.ops.task.executor.ITaskExecutor;

import lombok.Data;
import lombok.EqualsAndHashCode;
import lombok.NoArgsConstructor;
import lombok.ToString;
import lombok.experimental.SuperBuilder;

@Data
@ToString(callSuper = true)
@EqualsAndHashCode(callSuper = true)
@NoArgsConstructor
@SuperBuilder
public class TaskExecutorDispatchedLifecycleEvent extends AbstractTaskExecutorLifecycleEvent
        implements
            IReportableTaskExecutorLifecycleEvent {

    private int workflowInstanceId;

    private String taskInstanceHost;

    private Long latestReportTime;

    public static TaskExecutorDispatchedLifecycleEvent of(final ITaskExecutor taskExecutor) {
        final TaskExecutionContext taskExecutionContext = taskExecutor.getTaskExecutionContext();
        return TaskExecutorDispatchedLifecycleEvent.builder()
                .taskInstanceId(taskExecutionContext.getTaskInstanceId())
                .workflowInstanceId(taskExecutionContext.getWorkflowInstanceId())
                .taskInstanceHost(taskExecutionContext.getHost())
                .type(TaskExecutorLifecycleEventType.DISPATCHED)
                .build();
    }

}
