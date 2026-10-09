package io.yak.ops.flow.runtime.operators.coordination;

/**
 * Handler of coordination events on the owning StreamTask's mailbox thread.
 *
 * <p>Event handling, reader polling and state snapshot callbacks are serialized on that
 * thread. Unknown events must be rejected rather than silently ignored.
 *
 * @author weifuwan
 */
@FunctionalInterface
public interface OperatorEventHandler {

    /** Handles one coordinator event on the owning mailbox thread. */
    void handleOperatorEvent(OperatorEvent event) throws Exception;
}
