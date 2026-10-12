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

    /** Returns the physical Job identity, when the execution engine provides one. */
    Optional<TaskRuntimeReference> runtimeReference();

    /** Returns the terminal outcome; unexpected execution errors complete exceptionally. */
    CompletionStage<TaskOutcome> completion();

    /** Requests cancellation without declaring the business Instance canceled. */
    CompletionStage<Void> cancel();
}
