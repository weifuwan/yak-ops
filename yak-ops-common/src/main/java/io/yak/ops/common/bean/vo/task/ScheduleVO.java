package io.yak.ops.common.bean.vo.task;

import java.time.LocalDateTime;
import lombok.Data;

/**
 * 通用调度定义只读视图；enabled不代表Quartz Trigger一定存在。
 *
 * @author weifuwan
 * @since 2026-10-10
 */
@Data
public class ScheduleVO {

    /** Schedule稳定ID。 */
    private String id;

    /** 调度目标类型TASK或未来的WORKFLOW。 */
    private String targetType;

    /** 调度目标Definition ID。 */
    private String targetId;

    /** Quartz Cron表达式。 */
    private String cronExpression;

    /** IANA时区标识。 */
    private String timeZone;

    /** 数据库存储的调度启用状态。 */
    private Boolean enabled;

    /** Schedule创建时间。 */
    private LocalDateTime createTime;

    /** Schedule更新时间。 */
    private LocalDateTime updateTime;
}
