package io.yak.ops.plugin.task.api.model;

import io.yak.ops.plugin.task.api.enums.TaskExecutionStatus;

import lombok.Data;
import lombok.EqualsAndHashCode;

@EqualsAndHashCode(callSuper = true)
@Data
public class ConditionDependentItem extends DependentItem {

    private TaskExecutionStatus status;
}
