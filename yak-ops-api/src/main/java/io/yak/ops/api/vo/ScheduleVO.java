package io.yak.ops.api.vo;

import io.yak.ops.common.enums.FailureStrategy;
import io.yak.ops.common.enums.Priority;
import io.yak.ops.common.enums.ReleaseState;
import io.yak.ops.common.enums.ScheduleMissedFirePolicy;
import io.yak.ops.common.enums.WarningType;
import io.yak.ops.common.utils.DateUtils;
import io.yak.ops.dao.entity.Schedule;

import java.time.ZoneId;
import java.util.Date;

import lombok.Data;

@Data
public class ScheduleVO {

    private int id;

    private long workflowDefinitionCode;

    private String workflowDefinitionName;

    private String projectName;

    private String definitionDescription;

    private String startTime;

    private String endTime;

    /**
     * timezoneId
     * <p>see {@link java.util.TimeZone#getTimeZone(String)}
     */
    private String timezoneId;

    private String crontab;

    private ScheduleMissedFirePolicy missedFirePolicy;

    private FailureStrategy failureStrategy;

    private WarningType warningType;

    private Date createTime;

    private Date updateTime;

    private int userId;

    private String userName;

    private ReleaseState releaseState;

    private int warningGroupId;

    private Priority workflowInstancePriority;

    private String workerGroup;

    private String tenantCode;

    private Long environmentCode;

    private String environmentName;

    public ScheduleVO(Schedule schedule) {
        this.setId(schedule.getId());
        this.setCrontab(schedule.getCrontab());
        this.setMissedFirePolicy(schedule.getMissedFirePolicy());
        this.setProjectName(schedule.getProjectName());
        this.setUserName(schedule.getUserName());
        this.setWorkerGroup(schedule.getWorkerGroup());
        this.setWarningType(schedule.getWarningType());
        this.setWarningGroupId(schedule.getWarningGroupId());
        this.setUserId(schedule.getUserId());
        this.setCreateTime(schedule.getCreateTime());
        this.setUpdateTime(schedule.getUpdateTime());
        this.setTimezoneId(schedule.getTimezoneId());
        this.setReleaseState(schedule.getReleaseState());
        this.setWorkflowInstancePriority(schedule.getWorkflowInstancePriority());
        this.setWorkflowDefinitionName(schedule.getWorkflowDefinitionName());
        this.setWorkflowDefinitionCode(schedule.getWorkflowDefinitionCode());
        this.setFailureStrategy(schedule.getFailureStrategy());
        this.setEnvironmentCode(schedule.getEnvironmentCode());
        this.setStartTime(DateUtils.dateToString(schedule.getStartTime(), ZoneId.systemDefault().getId()));
        this.setEndTime(DateUtils.dateToString(schedule.getEndTime(), ZoneId.systemDefault().getId()));
        this.setEnvironmentName(schedule.getEnvironmentName());
        this.setTenantCode(schedule.getTenantCode());
    }
}
