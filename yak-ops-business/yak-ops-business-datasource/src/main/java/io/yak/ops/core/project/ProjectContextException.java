package io.yak.ops.core.project;

/** Exception raised when a trusted Project Space context cannot be established. */
public class ProjectContextException extends RuntimeException {

  private final ProjectContextError error;

  public ProjectContextException(ProjectContextError error) {
    super(error == null ? "Project Space context error" : error.getMessage());
    this.error = error == null ? ProjectContextError.PROJECT_NOT_FOUND : error;
  }

  public ProjectContextException(ProjectContextError error, Throwable cause) {
    super(error == null ? "Project Space context error" : error.getMessage(), cause);
    this.error = error == null ? ProjectContextError.PROJECT_NOT_FOUND : error;
  }

  public ProjectContextError getError() {
    return error;
  }
}
