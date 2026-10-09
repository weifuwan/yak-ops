package io.yak.ops.flow.api.trace;

import java.util.Objects;

/**
 * YakFlow Runtime Trace 观察接口。
 *
 * <p>Trace 是诊断旁路，不能改变数据流执行结果。Connector 应通过 {@link #emit(RuntimeTraceEvent)}
 * 派发事件，使 Listener 自身异常不会传播到 Source / Sink 主链路。
 *
 * @author weifuwan
 * @since 2026-10-03
 */
@FunctionalInterface
public interface RuntimeTraceListener {

    RuntimeTraceListener NOOP = event -> {};

    /**
     * 消费一条 Runtime Trace Event。
     *
     * @param event 诊断事件
     */
    void onEvent(RuntimeTraceEvent event);

    /**
     * 以 best-effort 方式派发事件；Listener 异常不会中断数据同步。
     *
     * @param event 诊断事件
     */
    default void emit(RuntimeTraceEvent event) {
        Objects.requireNonNull(event, "event must not be null");
        try {
            onEvent(event);
        } catch (RuntimeException ignored) {
            // Trace 是旁路诊断能力，Listener 故障不能改变数据平面执行结果。
        }
    }

    /**
     * 返回不记录任何事件的 Listener。
     *
     * @return no-op listener
     */
    static RuntimeTraceListener noop() {
        return NOOP;
    }
}
