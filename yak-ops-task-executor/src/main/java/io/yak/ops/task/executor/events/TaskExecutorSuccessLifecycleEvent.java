package io.yak.ops.task.executor.events;

import io.yak.ops.plugin.task.api.TaskExecutionContext;
import io.yak.ops.plugin.task.api.model.Property;
import io.yak.ops.task.executor.ITaskExecutor;

import java.util.List;

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
public class TaskExecutorSuccessLifecycleEvent extends AbstractTaskExecutorLifecycleEvent
        implements
            IReportableTaskExecutorLifecycleEvent {

    private int workflowInstanceId;

    private String taskInstanceHost;

    private long endTime;

    private List<Property> varPool;

    private Long latestReportTime;

    public static TaskExecutorSuccessLifecycleEvent of(final ITaskExecutor taskExecutor) {
        final TaskExecutionContext taskExecutionContext = taskExecutor.getTaskExecutionContext();
        return TaskExecutorSuccessLifecycleEvent.builder()
                .workflowInstanceId(taskExecutionContext.getWorkflowInstanceId())
                .taskInstanceId(taskExecutionContext.getTaskInstanceId())
                .taskInstanceHost(taskExecutionContext.getHost())
                .varPool(taskExecutionContext.getVarPool())
                .type(TaskExecutorLifecycleEventType.SUCCESS)
                .endTime(taskExecutionContext.getEndTime())
                .build();
    }

}
