package io.yak.ops.api.vo;

import java.util.Date;

import lombok.Builder;
import lombok.Data;

@Data
@Builder
public class DataSyncInstanceVO {

    private String id;
    private String definitionId;
    private int definitionVersion;
    private boolean debug;
    private String taskType;
    private String triggerType;
    private String status;
    private Date startTime;
    private Date endTime;
    private Date createTime;
    private String runtimeEngine;
    private String runtimeId;
    private String host;
    private Integer workflowInstanceId;
}
