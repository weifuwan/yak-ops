package io.yak.ops.dao.entity.datasync;

import com.baomidou.mybatisplus.annotation.TableField;
import com.baomidou.mybatisplus.annotation.TableName;
import io.yak.ops.common.enums.task.ScheduleTargetType;
import io.yak.ops.dao.entity.BaseEntity;
import lombok.Getter;
import lombok.Setter;
import lombok.ToString;

/**
 * DATA_SYNC 离线任务在通用 Schedule 表中的兼容投影，调度类型固定为 TASK。
 *
 * @author weifuwan
 * @since 2026-09-29
 */
@Getter
@Setter
@ToString
@TableName("yak_ops_schedule")
public class DataSyncScheduleEntity extends BaseEntity {

    /** 调度所属 Workspace ID。 */
    private String workspaceId;

    /** 调度目标类型；数据同步只能操作 TASK，不能处理未来的 WORKFLOW 调度。 */
    private ScheduleTargetType targetType = ScheduleTargetType.TASK;

    /** Task 定义ID，映射通用调度的 target_id 列。 */
    @TableField("target_id")
    private String taskId;

    /** Quartz Cron 表达式。 */
    private String cronExpression;

    /** Cron 解释使用的 IANA 时区 ID。 */
    private String timeZone;

    /** 调度是否启用；启用后才会注册到 Scheduler Runtime。 */
    private Boolean enabled;
}
