package io.yak.ops.core.project;

/**
 * Trusted project context resolved for the current request.
 *
 * @param projectId Yak Security project ID
 * @param projectName project display name
 */
public record ProjectContext(Long projectId, String projectName) {

  public ProjectContext {
    if (projectId == null || projectId <= 0) {
      throw new IllegalArgumentException("projectId must be a positive number");
    }
  }
}
