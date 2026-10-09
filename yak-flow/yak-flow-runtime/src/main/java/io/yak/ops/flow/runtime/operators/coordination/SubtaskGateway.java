package io.yak.ops.flow.runtime.operators.coordination;

import java.util.concurrent.CompletionStage;

/**
 * Coordinator-to-subtask control-message delivery path.
 *
 * <p>Sending must not block the coordinator thread. In the embedded runtime the
 * completion stage acknowledges that the task mailbox executed the event, not merely
 * enqueued it. That does not imply record consumption, persistence or checkpoint success.
 * Recovery still requires Reader and split-assignment checkpoint state.
 *
 * @author weifuwan
 */
@FunctionalInterface
public interface SubtaskGateway {

    /**
 * Delivers an event asynchronously to the registered task attempt.
 *
 * @return a stage completed after mailbox processing or exceptionally on failure
 */
    CompletionStage<Void> sendEvent(OperatorEvent event);
}
