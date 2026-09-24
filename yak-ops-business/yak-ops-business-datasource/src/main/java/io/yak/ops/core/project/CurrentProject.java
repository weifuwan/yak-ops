package io.yak.ops.core.project;

import java.util.Optional;

/** Read-only access to the trusted Project Space bound to the current request. */
public interface CurrentProject {

  Optional<ProjectContext> current();

  default boolean isPresent() {
    return current().isPresent();
  }

  default ProjectContext require() {
    return current().orElseThrow(
        () -> new ProjectContextException(ProjectContextError.PROJECT_REQUIRED));
  }

  default Long requireProjectId() {
    return require().projectId();
  }
}
