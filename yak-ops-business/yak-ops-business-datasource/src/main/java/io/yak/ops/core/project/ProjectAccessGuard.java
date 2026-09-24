package io.yak.ops.core.project;

/** Resolves and authorizes a requested Yak Security project for an authenticated user. */
@FunctionalInterface
public interface ProjectAccessGuard {

  ProjectContext requireAccessible(Long projectId, String username);
}
