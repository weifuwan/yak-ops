package io.yak.ops.plugin.task.api.execution;

/**
 * Proves execution was rejected before any external execution side effect.
 *
 * <p>A launcher may release its resource claims only for this proven pre-start failure
 * or after observing an authoritative terminal TaskHandle outcome.
 */
public final class TaskStartRejectedException extends RuntimeException {

    public TaskStartRejectedException(String message, Throwable cause) {
        super(message, cause);
    }
}
