package io.yak.ops.dao.repository.task;

import lombok.Data;

/**
 * Task Instance单表聚合统计结果；根实例数不累计或放大Attempt数。
 *
 * @author weifuwan
 * @since 2026-10-10
 */
@Data
public class MetricsStats {

    /** 时间窗口内创建的Task根实例数。 */
    private Long instanceCount;

    /** 成功结束的实例数。 */
    private Long succeededCount;

    /** 失败结束的实例数。 */
    private Long failedCount;

    /** 被标记为LOST的实例数。 */
    private Long lostCount;

    /** 当前活动实例数，含等待重试的根实例。 */
    private Long activeCount;
}
