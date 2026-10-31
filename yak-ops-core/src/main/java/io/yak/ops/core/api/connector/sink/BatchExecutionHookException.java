package io.yak.ops.core.api.connector.sink;

/** Preserves an external hook failure together with whether its committed effects are unknown. */
public final class BatchExecutionHookException extends Exception {
    private final boolean outcomeUnknown;

    public BatchExecutionHookException(String message, Throwable cause, boolean outcomeUnknown) {
        super(message, cause);
        this.outcomeUnknown = outcomeUnknown;
    }

    public boolean isOutcomeUnknown() {
        return outcomeUnknown;
    }
}
