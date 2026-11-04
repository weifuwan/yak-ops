package io.yak.ops.task.executor.dto;

import java.util.Date;

import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class TaskExecutorDTO {

    private int id;

    private String name;

    private String taskType;

    private long projectCode;

    private int workflowInstanceId;

    private String workflowInstanceName;

    private Date startTime;

}
