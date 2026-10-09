package io.yak.ops.flow.runtime.operators.coordination;

import java.util.concurrent.CompletionStage;

/**
 * Asynchronous path for an operator to send control events to its coordinator.
 *
 * <p>Unlike SubtaskGateway, this direction runs from task to coordinator. Completion
 * acknowledges coordinator processing, not consumption of split records or checkpoint success.
 */
@FunctionalInterface
public interface OperatorEventGateway {

    CompletionStage<Void> sendEventToCoordinator(OperatorEvent event);
}
