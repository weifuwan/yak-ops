package io.yak.ops.business.datasync.execution.executor;

/**
 * 取消后等待 YakFlow Source/Sink 工作线程真正退出，防止重试或 CDC serverId 释放先于 Runtime 终止。
 *
 * @author weifuwan
 * @since 2026-10-08
 */
final class ExecutionRuntimeTermination {

    private ExecutionRuntimeTermination() {}

    static void cancelAndAwait(SyncExecution execution) {
        if (execution == null) return;
        execution.cancel();
        awaitUninterruptibly(execution);
    }

    static void stopAndAwait(SyncExecution execution) {
        if (execution == null) return;
        if (execution.status() == ExecutionStatus.RUNNING) execution.cancel();
        awaitUninterruptibly(execution);
    }

    private static void awaitUninterruptibly(SyncExecution execution) {
        boolean interrupted = Thread.interrupted();
        try {
            while (true) {
                try {
                    execution.await();
                    return;
                } catch (InterruptedException exception) {
                    interrupted = true;
                }
            }
        } finally {
            if (interrupted) Thread.currentThread().interrupt();
        }
    }
}
