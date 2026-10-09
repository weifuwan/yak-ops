package io.yak.ops.dao.entity.datasync;

import com.baomidou.mybatisplus.annotation.TableName;
import io.yak.ops.dao.entity.BaseEntity;
import lombok.Getter;
import lombok.Setter;
import lombok.ToString;

/**
 * 映射 yak_ops_data_sync_schedule 表，保存 Offline Task 的 Cron 调度定义。
 *
 * @author weifuwan
 * @since 2026-09-29
 */
@Getter
@Setter
@ToString
@TableName("yak_ops_data_sync_schedule")
public class DataSyncScheduleEntity extends BaseEntity {

    /** 调度所属 Workspace ID。 */
    private String workspaceId;

    /** 关联的离线同步任务 ID，同一 Workspace 内一个 Task 最多一个 Schedule。 */
    private String taskId;

    /** Quartz Cron 表达式。 */
    private String cronExpression;

    /** Cron 解释使用的 IANA 时区 ID。 */
    private String timeZone;

    /** 调度是否启用；启用后才会注册到 Scheduler Runtime。 */
    private Boolean enabled;
}
