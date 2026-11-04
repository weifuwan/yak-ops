package io.yak.ops.dao.model;

import io.yak.ops.plugin.task.api.enums.TaskExecutionStatus;

import lombok.AllArgsConstructor;
import lombok.Data;
import lombok.NoArgsConstructor;

@Data
@NoArgsConstructor
@AllArgsConstructor
public class TaskInstanceStatusCountDto {

    private TaskExecutionStatus state;

    private int count;
}
