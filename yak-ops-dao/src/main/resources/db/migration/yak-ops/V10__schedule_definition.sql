CREATE TABLE yak_ops_schedule (
  id VARCHAR(64) NOT NULL COMMENT 'Stable schedule identifier, never reused',
  workspace_id VARCHAR(64) NOT NULL COMMENT 'Workspace owning the schedule and target',
  target_type VARCHAR(32) NOT NULL COMMENT 'Root target category; initially TASK',
  target_id VARCHAR(64) NOT NULL COMMENT 'Immutable target Definition identifier',
  cron_expression VARCHAR(256) NOT NULL COMMENT 'Quartz Cron expression retained as entered',
  time_zone VARCHAR(64) NOT NULL COMMENT 'Explicit named zone for Cron evaluation',
  enabled TINYINT(1) NOT NULL DEFAULT 0 COMMENT 'Desired enablement, independent of engine health',
  revision BIGINT NOT NULL DEFAULT 1 COMMENT 'Monotonic configuration and eligibility revision',
  enabled_at DATETIME(3) NULL COMMENT 'UTC enablement boundary; null until enabled',
  enabled_by VARCHAR(64) NULL COMMENT 'Actor authorizing latest enablement; rechecked at admission',
  retired_before DATETIME(3) NULL COMMENT 'Monotonic UTC floor preventing readmission after history cleanup',
  deleted_at DATETIME(3) NULL COMMENT 'Logical deletion time; null for active configuration',
  active_target_id VARCHAR(64) GENERATED ALWAYS AS
    (CASE WHEN deleted_at IS NULL THEN target_id ELSE NULL END) STORED
    COMMENT 'Unique target only while schedule is not deleted',
  create_time DATETIME(3) NOT NULL COMMENT 'Creation audit time',
  update_time DATETIME(3) NOT NULL COMMENT 'Latest mutation audit time',
  create_by VARCHAR(64) NOT NULL COMMENT 'Creating actor',
  update_by VARCHAR(64) NOT NULL COMMENT 'Latest modifying actor',
  PRIMARY KEY (id),
  UNIQUE KEY uk_ops_schedule_target (workspace_id, target_type, active_target_id),
  KEY idx_ops_schedule_workspace_updated (workspace_id, update_time, id),
  KEY idx_ops_schedule_enabled (enabled, deleted_at, id),
  CONSTRAINT ck_ops_schedule_revision CHECK (revision >= 1),
  CONSTRAINT ck_ops_schedule_enabled CHECK (enabled IN (0, 1)),
  CONSTRAINT ck_ops_schedule_authority CHECK (enabled = 0 OR (enabled_at IS NOT NULL AND enabled_by IS NOT NULL))
) ENGINE=InnoDB DEFAULT CHARACTER SET=utf8mb4 COLLATE=utf8mb4_unicode_ci
  COMMENT='Workspace-scoped periodic schedule intent';
