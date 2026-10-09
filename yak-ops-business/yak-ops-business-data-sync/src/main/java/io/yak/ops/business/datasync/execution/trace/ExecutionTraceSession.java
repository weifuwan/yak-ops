package io.yak.ops.business.datasync.execution.trace;

import io.yak.ops.flow.api.trace.RuntimeTraceListener;

/**
 * 一次 Attempt 的 Runtime Trace 收集会话。
 *
 * @author weifuwan
 * @since 2026-10-03
 */
public interface ExecutionTraceSession extends AutoCloseable {

    /**
     * 返回交给 YakFlow Source / Sink 的诊断 Listener。
     *
     * @return runtime trace listener
     */
    RuntimeTraceListener listener();

    /**
     * 收口当前 Attempt Trace。Trace 收口失败不能抛出异常影响数据同步状态。
     */
    @Override
    void close();

    static ExecutionTraceSession noop() {
        return new ExecutionTraceSession() {
            @Override
            public RuntimeTraceListener listener() {
                return RuntimeTraceListener.noop();
            }

            @Override
            public void close() {}
        };
    }
}
