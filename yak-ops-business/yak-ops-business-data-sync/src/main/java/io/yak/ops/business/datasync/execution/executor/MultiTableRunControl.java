package io.yak.ops.business.datasync.execution.executor;

import io.yak.ops.flow.runtime.LocalExecution;

/**
 * 多表 Root 的进程内取消令牌，持有当前活动 YakFlow Execution 和 Worker 线程引用。
 *
 * @author weifuwan
 * @since 2026-10-08
 */
final class MultiTableRunControl {

    private volatile boolean canceled;
    private volatile Thread worker;
    private volatile LocalExecution<?> active;

    void setWorker(Thread worker) {
        this.worker = worker;
    }

    void setActive(LocalExecution<?> execution) {
        this.active = execution;
    }

    void clearActive() {
        active = null;
    }

    void cancel() {
        canceled = true;
        LocalExecution<?> execution = active;
        if (execution != null) execution.cancel();
        Thread thread = worker;
        if (thread != null) thread.interrupt();
    }

    boolean isCanceled() {
        return canceled;
    }
}
