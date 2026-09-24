package io.yak.ops.core.project;

import java.util.function.Supplier;

/**
 * Runs trusted background work inside an explicit Project Space context.
 *
 * <p>HTTP requests obtain context through {@link ProjectScope}; durable scheduled/outbox work must
 * restore the persisted project through this scope instead of executing with an empty
 * {@link CurrentProject}.</p>
 */
public interface ProjectContextScope {

  <T> T call(ProjectContext context, Supplier<T> action);

  default void run(ProjectContext context, Runnable action) {
    call(
        context,
        () -> {
          action.run();
          return null;
        });
  }
}
