package io.yak.ops.core.api.connector.sink;

/**
 * One ordered external operation surrounding a bounded job's data execution.
 * The runtime journals intent before calling the hook and confirmed completion afterwards.
 * An interrupted operation is not assumed to be safe to replay.
 */
public interface BatchExecutionHook {
    enum Phase {
        BEFORE_DATA,
        AFTER_DATA
    }

    /** Returns the data boundary at which this operation must execute. */
    Phase phase();

    /** Returns a stable SHA-256 identity of the complete operation, without exposing SQL or credentials. */
    String fingerprint();

    /**
     * Executes one operation using attempt-owned resources.
     *
     * @throws BatchExecutionHookException when the connector can distinguish a confirmed failure from uncertainty
     * @throws Exception for an outcome which the runtime must conservatively treat as unknown
     */
    void execute() throws Exception;

    /** Requests cancellation without interrupting a driver before its outcome can be classified. */
    default void cancel() throws Exception {}
}
