package io.yak.ops.dao.entity;

import io.yak.ops.plugin.task.api.enums.TaskExecutionStatus;

import lombok.AllArgsConstructor;
import lombok.Data;
import lombok.NoArgsConstructor;

@Data
@NoArgsConstructor
@AllArgsConstructor
public class ExecuteStatusCount {

    /**
     * execution state
     */
    private TaskExecutionStatus state;

    /**
     * count for state
     */
    private int count;
}
