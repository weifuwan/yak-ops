package io.yak.ops.core.project;

/**
 * Rollout state for one project-aware web capability.
 *
 * <p>LEGACY_GLOBAL keeps current global semantics. PROJECT_OPTIONAL accepts a trusted project when
 * supplied while compatibility data is still being backfilled. PROJECT_REQUIRED rejects requests
 * without a trusted project context.</p>
 */
public enum ProjectMigrationMode {
  LEGACY_GLOBAL,
  PROJECT_OPTIONAL,
  PROJECT_REQUIRED
}
