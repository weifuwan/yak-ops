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
@SuperBuilder
@EqualsAndHashCode(callSuper = true)
@NoArgsConstructor
public class TaskExecutorFailedLifecycleEvent extends AbstractTaskExecutorLifecycleEvent
        implements
            IReportableTaskExecutorLifecycleEvent {

    private int workflowInstanceId;

    private String taskInstanceHost;

    private String appIds;

    private long endTime;

    private Long latestReportTime;

    public static TaskExecutorFailedLifecycleEvent of(final ITaskExecutor taskExecutor) {
        final TaskExecutionContext taskExecutionContext = taskExecutor.getTaskExecutionContext();
        return TaskExecutorFailedLifecycleEvent.builder()
                .taskInstanceId(taskExecutor.getId())
                .workflowInstanceId(taskExecutionContext.getWorkflowInstanceId())
                .taskInstanceHost(taskExecutionContext.getHost())
                .appIds(taskExecutionContext.getAppIds())
                .endTime(taskExecutionContext.getEndTime())
                .type(TaskExecutorLifecycleEventType.FAILED)
                .build();
    }

}
