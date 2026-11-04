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
@SuperBuilder
@NoArgsConstructor
public class TaskExecutorStartedLifecycleEvent extends AbstractTaskExecutorLifecycleEvent
        implements
            IReportableTaskExecutorLifecycleEvent {

    private int workflowInstanceId;

    private String taskInstanceHost;

    private long startTime;

    private String logPath;

    private String executePath;

    private Long latestReportTime;

    public static TaskExecutorStartedLifecycleEvent of(final ITaskExecutor taskExecutor) {
        final TaskExecutionContext taskExecutionContext = taskExecutor.getTaskExecutionContext();

        return TaskExecutorStartedLifecycleEvent.builder()
                .taskInstanceId(taskExecutor.getId())
                .workflowInstanceId(taskExecutionContext.getWorkflowInstanceId())
                .taskInstanceHost(taskExecutionContext.getHost())
                .startTime(taskExecutor.getTaskExecutionContext().getStartTime())
                .logPath(taskExecutionContext.getLogPath())
                .executePath(taskExecutionContext.getExecutePath())
                .type(TaskExecutorLifecycleEventType.RUNNING)
                .build();
    }

}
