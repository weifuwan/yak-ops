package io.yak.ops.plugin.task.api.execution;

import java.util.Optional;
import java.util.OptionalInt;
import java.util.concurrent.CompletionStage;

/**
 * Process-local observation and cancellation handle for a submitted Task.
 *
 * <p>A cancellation request is not a successful terminal outcome. Consumers must
 * observe completion before persisting CANCELED or releasing execution resources.
 */
public interface TaskHandle {

    /**
     * Returns a plugin-owned public JSON observation of the current physical attempt.
     * No credentials, row values, checkpoint bytes or raw exception messages may be included.
     * The platform persists the final report atomically with the Instance outcome.
     * An optional object-valued {@code summary} contains the compact observation for historical list queries.
     *
     * @return immutable JSON snapshot up to 262144 characters, or empty when unavailable
     */
    default Optional<String> executionReport() {
        return Optional.empty();
    }

    /**
     * Returns a compact public JSON observation for batch Instance queries.
     * This object matches the full report's optional {@code summary} and excludes per-item detail.
     *
     * @return an immutable JSON object up to 16384 characters, or empty when unavailable
     */
    default Optional<String> executionSummary() {
        return Optional.empty();
    }

    /**
     * Returns the physical Job identity, when provided by the execution engine.
     *
     * @return the known runtime identity, or empty before it is available
     */
    Optional<TaskRuntimeReference> runtimeReference();

    /**
     * Observes the terminal outcome of one launched Task.
     *
     * @return eventual terminal result, or exceptional completion on execution error
     */
    CompletionStage<TaskOutcome> completion();

    /** Returns the active physical job's measured progress, when available. */
    default OptionalInt progressPercent() {
        return OptionalInt.empty();
    }

    /**
     * Requests physical cancellation without declaring the business Instance canceled.
     *
     * @return completion of the physical cancellation request
     */
    CompletionStage<Void> cancel();
}
