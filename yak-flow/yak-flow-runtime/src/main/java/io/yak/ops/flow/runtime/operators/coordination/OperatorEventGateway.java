package io.yak.ops.flow.runtime.operators.coordination;

import java.util.concurrent.CompletionStage;

/**
 * Operator 向所属 Coordinator 发送控制事件的异步通道。
 *
 * <p>区别于 Coordinator → Task 的 SubtaskGateway；确认只表示协调侧处理完成，
 * 不代表 Split 消费完成或 Checkpoint 成功。
 */
@FunctionalInterface
public interface OperatorEventGateway {

    CompletionStage<Void> sendEventToCoordinator(OperatorEvent event);
}
