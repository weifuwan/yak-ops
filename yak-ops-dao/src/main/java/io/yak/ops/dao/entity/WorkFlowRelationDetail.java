package io.yak.ops.dao.entity;

import java.util.Date;

import lombok.Data;

@Data
public class WorkFlowRelationDetail {

    private long workFlowCode;
    private String workFlowName;
    private String workFlowPublishStatus;
    private Date scheduleStartTime;
    private Date scheduleEndTime;
    private String crontab;
    private int schedulePublishStatus;
    private String sourceWorkFlowCode;

}
