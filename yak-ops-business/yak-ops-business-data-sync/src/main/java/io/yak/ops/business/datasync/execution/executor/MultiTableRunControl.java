package io.yak.ops.business.datasync.execution.executor;

import java.util.Objects;
import java.util.function.Supplier;

/**
 * 多表 Root 的进程内取消令牌，将 Runtime 启动与取消串行化，避免取消后继续启动下一条 Route。
 *
 * @author weifuwan
 * @since 2026-10-08
 */
final class MultiTableRunControl {

    private boolean canceled;
    private Thread worker;
    private SyncExecution active;

    synchronized void setWorker(Thread worker) {
        this.worker = worker;
    }

    synchronized SyncExecution launch(Supplier<? extends SyncExecution> launcher) {
        if (canceled) return null;
        active = Objects.requireNonNull(launcher.get(), "runtime launch must not return null");
        return active;
    }

    synchronized void clearActive() {
        active = null;
    }

    synchronized void cancel() {
        canceled = true;
        if (active != null) active.cancel();
        if (worker != null) worker.interrupt();
    }

    synchronized boolean isCanceled() {
        return canceled;
    }
}
