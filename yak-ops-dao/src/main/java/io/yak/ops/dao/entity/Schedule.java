package io.yak.ops.dao.entity;

import io.yak.ops.common.enums.FailureStrategy;
import io.yak.ops.common.enums.Priority;
import io.yak.ops.common.enums.ReleaseState;
import io.yak.ops.common.enums.ScheduleMissedFirePolicy;
import io.yak.ops.common.enums.WarningType;

import java.util.Date;

import com.baomidou.mybatisplus.annotation.IdType;
import com.baomidou.mybatisplus.annotation.TableField;
import com.baomidou.mybatisplus.annotation.TableId;
import com.baomidou.mybatisplus.annotation.TableName;

import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
@TableName("t_yak_ops_schedules")
public class Schedule {

    @TableId(value = "id", type = IdType.AUTO)
    private Integer id;

    private long workflowDefinitionCode;

    @TableField(exist = false)
    private String workflowDefinitionName;

    @TableField(exist = false)
    private String projectName;

    @TableField(exist = false)
    private String definitionDescription;

    private Date startTime;

    private Date endTime;

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

    @TableField(exist = false)
    private String userName;

    private ReleaseState releaseState;

    private int warningGroupId;

    private Priority workflowInstancePriority;

    private String workerGroup;

    private String tenantCode;

    private Long environmentCode;

    @TableField(exist = false)
    private String environmentName;
}
