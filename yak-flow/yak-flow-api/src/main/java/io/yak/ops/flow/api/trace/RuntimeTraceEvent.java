package io.yak.ops.flow.api.trace;

import java.time.Instant;

/**
 * YakFlow 运行时诊断事件的最小公共契约。
 *
 * <p>事件只描述数据平面运行细节，不承载产品 Task / Execution / Attempt 持久化身份。
 *
 * @author weifuwan
 * @since 2026-10-03
 */
public interface RuntimeTraceEvent {

    /**
     * 返回事件发生时间。
     *
     * @return 事件时间
     */
    Instant timestamp();

    /**
     * 返回稳定事件类型标识。
     *
     * @return 事件类型
     */
    String type();
}
