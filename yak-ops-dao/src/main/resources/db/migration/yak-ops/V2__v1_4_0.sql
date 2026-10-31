ALTER TABLE yak_ops_task_definition
  ADD COLUMN revision BIGINT NOT NULL DEFAULT 1 COMMENT 'Monotonic edit and publication revision',
  ADD COLUMN published_version INT UNSIGNED NULL COMMENT 'Immutable parameter version selected for future executions',
  ADD COLUMN published_debug TINYINT(1) NULL COMMENT 'Diagnostics frozen with the current publication';

UPDATE yak_ops_task_definition
SET published_version = current_version, published_debug = debug
WHERE status = 1;

ALTER TABLE yak_ops_task_definition
  ADD CONSTRAINT ck_ops_task_publication_state CHECK (
    (status = 0 AND published_version IS NULL AND published_debug IS NULL) OR
    (status = 1 AND published_version IS NOT NULL AND published_debug IS NOT NULL)),
  ADD CONSTRAINT ck_ops_task_definition_revision CHECK (revision >= 1);

CREATE TABLE yak_ops_task_publication (
  id VARCHAR(64) NOT NULL COMMENT 'Stable successful publication receipt identifier',
  workspace_id VARCHAR(64) NOT NULL COMMENT 'Workspace owning the task and publication',
  definition_id VARCHAR(64) NOT NULL COMMENT 'Task whose published state changed',
  version INT UNSIGNED NOT NULL COMMENT 'Immutable parameter version selected by publication',
  debug TINYINT(1) NOT NULL COMMENT 'Diagnostics setting frozen by publication',
  source_revision BIGINT NOT NULL COMMENT 'Task edit revision supplied by the original request',
  request_id VARCHAR(128) CHARACTER SET utf8mb4 COLLATE utf8mb4_bin NOT NULL COMMENT 'Case-sensitive caller idempotency key',
  create_time DATETIME(3) NOT NULL COMMENT 'Time when the successful publication was recorded',
  update_time DATETIME(3) NOT NULL COMMENT 'Immutable receipt creation audit time',
  create_by VARCHAR(64) NOT NULL COMMENT 'Actor who published the task',
  update_by VARCHAR(64) NOT NULL COMMENT 'Actor who created the immutable receipt',
  PRIMARY KEY (id),
  UNIQUE KEY uk_ops_task_publication_request (workspace_id, definition_id, request_id),
  KEY idx_ops_task_publication_history (workspace_id, definition_id, create_time, id),
  CONSTRAINT ck_ops_task_publication_revision CHECK (source_revision >= 1)
) ENGINE=InnoDB DEFAULT CHARACTER SET=utf8mb4 COLLATE=utf8mb4_unicode_ci
  COMMENT='Successful publications recorded after publication history was introduced';
