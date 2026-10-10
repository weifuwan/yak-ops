package io.yak.ops.dao.entity.task;

import com.baomidou.mybatisplus.annotation.TableName;
import io.yak.ops.common.enums.task.ScheduleTargetType;
import io.yak.ops.dao.entity.BaseEntity;
import lombok.Getter;
import lombok.Setter;
import lombok.ToString;

/**
 * 通用 Schedule 只读投影，目标可表达 TASK 或未来的 WORKFLOW。
 *
 * @author weifuwan
 * @since 2026-10-10
 */
@Getter
@Setter
@ToString
@TableName("yak_ops_schedule")
public class ScheduleEntity extends BaseEntity {

    /** 调度所属 Workspace。 */
    private String workspaceId;

    /** TASK 或未来 WORKFLOW 的目标类型。 */
    private ScheduleTargetType targetType;

    /** 目标 Definition 的稳定 ID。 */
    private String targetId;

    /** Quartz Cron 表达式。 */
    private String cronExpression;

    /** 解释 Cron 所用的 IANA 时区 ID。 */
    private String timeZone;

    /** 数据库中持久化的业务启用状态，不代表 Quartz Trigger 已注册。 */
    private Boolean enabled;
}
