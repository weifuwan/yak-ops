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
public class TaskExecutorRuntimeContextChangedLifecycleEvent extends AbstractTaskExecutorLifecycleEvent
        implements
            IReportableTaskExecutorLifecycleEvent {

    private int workflowInstanceId;

    private String taskInstanceHost;

    @Deprecated
    private int processId;

    private String appIds;

    private Long latestReportTime;

    public static TaskExecutorRuntimeContextChangedLifecycleEvent of(final ITaskExecutor taskExecutor) {
        final TaskExecutionContext taskExecutionContext = taskExecutor.getTaskExecutionContext();
        return TaskExecutorRuntimeContextChangedLifecycleEvent.builder()
                .taskInstanceId(taskExecutionContext.getTaskInstanceId())
                .workflowInstanceId(taskExecutionContext.getWorkflowInstanceId())
                .taskInstanceHost(taskExecutionContext.getHost())
                .processId(taskExecutionContext.getProcessId())
                .appIds(taskExecutionContext.getAppIds())
                .type(TaskExecutorLifecycleEventType.RUNTIME_CONTEXT_CHANGE)
                .build();
    }

}
