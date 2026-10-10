package io.yak.ops.common.bean.vo.task;

import java.time.LocalDateTime;
import lombok.Data;

/**
 * 通用Task根实例指标；不包含DATA_SYNC专属读写行数。
 *
 * @author weifuwan
 * @since 2026-10-10
 */
@Data
public class MetricsVO {

    /** 当前查询的Task类型过滤值；未过滤时为空。 */
    private String taskType;

    /** 统计窗口开始时间（含）。 */
    private LocalDateTime rangeStart;

    /** 统计窗口结束时间（不含）。 */
    private LocalDateTime rangeEnd;

    /** 窗口内根实例总数。 */
    private Long instanceCount;

    /** 成功结束的根实例数。 */
    private Long succeededCount;

    /** 失败结束的根实例数。 */
    private Long failedCount;

    /** LOST状态的根实例数。 */
    private Long lostCount;

    /** PENDING、RUNNING、RETRY_WAITING活动实例数。 */
    private Long activeCount;
}
