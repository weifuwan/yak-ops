package io.yak.ops.api.vo;

import io.yak.ops.dao.model.TaskInstanceStatusCountDto;
import io.yak.ops.plugin.task.api.enums.TaskExecutionStatus;

import java.util.ArrayList;
import java.util.Collections;
import java.util.List;
import java.util.Map;
import java.util.function.Function;
import java.util.stream.Collectors;

import lombok.AllArgsConstructor;
import lombok.Data;
import lombok.NoArgsConstructor;

@Data
@NoArgsConstructor
@AllArgsConstructor
public class TaskInstanceCountVO {

    private int totalCount;

    private List<TaskInstanceStatusCountDto> taskInstanceStatusCounts;

    public static TaskInstanceCountVO empty() {
        return of(Collections.emptyList());
    }

    public static TaskInstanceCountVO of(List<TaskInstanceStatusCountDto> taskInstanceStatusCounts) {
        taskInstanceStatusCounts = new ArrayList<>(taskInstanceStatusCounts);

        Map<TaskExecutionStatus, TaskInstanceStatusCountDto> taskInstanceStatusCountMap =
                taskInstanceStatusCounts.stream()
                        .collect(Collectors.toMap(TaskInstanceStatusCountDto::getState, Function.identity()));

        for (TaskExecutionStatus value : TaskExecutionStatus.values()) {
            if (!taskInstanceStatusCountMap.containsKey(value)) {
                taskInstanceStatusCounts.add(new TaskInstanceStatusCountDto(value, 0));
            }
        }

        TaskInstanceCountVO taskInstanceCountVo = new TaskInstanceCountVO();
        taskInstanceCountVo.setTaskInstanceStatusCounts(taskInstanceStatusCounts);
        taskInstanceCountVo
                .setTotalCount(taskInstanceStatusCounts.stream().mapToInt(TaskInstanceStatusCountDto::getCount).sum());
        return taskInstanceCountVo;
    }
}
