package io.yak.ops.business.datasync.execution.lifecycle;

import java.util.Objects;
import java.util.function.Supplier;

/**
 * 单表 Execution 的进程内启动闸门：取消与 YakFlow 启动互斥，且取消状态在 Runtime 尚未创建时仍可保留。
 *
 * @author weifuwan
 * @since 2026-10-08
 */
public final class DataSyncExecutionControl {

    private boolean canceled;
    private SyncExecution active;

    public synchronized SyncExecution launch(Supplier<? extends SyncExecution> launcher) {
        if (canceled) return null;
        active = Objects.requireNonNull(launcher.get(), "runtime launch must not return null");
        return active;
    }

    public synchronized void cancel() {
        canceled = true;
        if (active != null) active.cancel();
    }

    public synchronized boolean isCanceled() {
        return canceled;
    }
}
