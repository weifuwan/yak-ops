package io.yak.ops.plugin.task.api.execution;

import java.util.Optional;
import java.util.concurrent.CompletionStage;

/**
 * Process-local observation and cancellation handle for a submitted Task.
 *
 * <p>A cancellation request is not a successful terminal outcome. Consumers must
 * observe completion before persisting CANCELED or releasing execution resources.
 */
public interface TaskHandle {

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

    /**
     * Requests physical cancellation without declaring the business Instance canceled.
     *
     * @return completion of the physical cancellation request
     */
    CompletionStage<Void> cancel();
}
