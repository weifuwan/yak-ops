package io.yak.ops.flow.runtime.operators.coordination;

/**
 * 算子在所属 StreamTask 的 Mailbox 线程中处理协调事件。
 *
 * <p>事件处理与 Reader 数据轮询、状态快照由同一线程串行执行。
 * 未知事件必须明确拒绝，不能静默忽略。
 *
 * @author weifuwan
 */
@FunctionalInterface
public interface OperatorEventHandler {

    /** 处理一个来自 Coordinator 的控制事件。 */
    void handleOperatorEvent(OperatorEvent event) throws Exception;
}
