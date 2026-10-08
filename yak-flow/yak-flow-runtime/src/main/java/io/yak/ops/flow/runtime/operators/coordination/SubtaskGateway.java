package io.yak.ops.flow.runtime.operators.coordination;

import java.util.concurrent.CompletionStage;

/**
 * Coordinator 向指定本地子任务发送 OperatorEvent 的控制通道。
 *
 * <p>sendEvent() 必须立即返回，不能在协调器线程阻塞等待 Task。
 * 当前同 JVM 实现中，返回的阶段在 Task 的 Mailbox 实际处理完成后才成功，
 * 而不仅是消息入队；这是一种比网络消息送达更强的本地确认语义。
 *
 * <p>该确认不代表 Split 已读取完成或记录已持久化，更不代表 Checkpoint 成功。
 * 分片一致性恢复必须结合 Reader 状态与 SplitAssignmentTracker。
 *
 * @author weifuwan
 */
@FunctionalInterface
public interface SubtaskGateway {

    /** 异步投递事件，处理失败时阶段异常完成。 */
    CompletionStage<Void> sendEvent(OperatorEvent event);
}
