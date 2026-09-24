package io.yak.ops.core.project;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;

import java.util.Optional;
import org.junit.jupiter.api.Test;

class CurrentProjectTest {

  @Test
  void requiresTrustedContextWhenProjectIsAbsent() {
    CurrentProject currentProject = Optional::empty;

    ProjectContextException error =
        assertThrows(ProjectContextException.class, currentProject::requireProjectId);

    assertEquals(ProjectContextError.PROJECT_REQUIRED, error.getError());
  }

  @Test
  void exposesBoundProjectId() {
    CurrentProject currentProject =
        () -> Optional.of(new ProjectContext(7L, "Project A"));

    assertEquals(7L, currentProject.requireProjectId());
  }
}
