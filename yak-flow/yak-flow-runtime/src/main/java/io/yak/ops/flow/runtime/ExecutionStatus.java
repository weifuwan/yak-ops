package io.yak.ops.flow.runtime;

/**
 * 描述一次 Local Execution Engine 执行的生命周期状态。
 *
 * @author weifuwan
 * @since 2026-09-27
 */
public enum ExecutionStatus {

    /** 执行对象已经创建但尚未启动。 */
    CREATED,

    /** Source 与 Sink 正在本地运行。 */
    RUNNING,

    /** 有界 Source 已完成且 Sink 已完成最终刷新。 */
    SUCCEEDED,

    /** 执行因运行异常失败。 */
    FAILED,

    /** 执行被显式取消。 */
    CANCELED;

    /**
     * 判断当前状态是否已经终止。
     *
     * @return 终态返回 true
     */
    public boolean isTerminal() {
        return this == SUCCEEDED || this == FAILED || this == CANCELED;
    }
}
