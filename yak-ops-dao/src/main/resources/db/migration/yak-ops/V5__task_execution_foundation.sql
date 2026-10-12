CREATE TABLE yak_ops_task_instance (
  id VARCHAR(64) NOT NULL,
  workspace_id VARCHAR(64) NOT NULL,
  definition_id VARCHAR(64) NOT NULL,
  definition_version INT UNSIGNED NOT NULL,
  task_type VARCHAR(64) NOT NULL,
  trigger_type VARCHAR(24) NOT NULL,
  request_id VARCHAR(128) CHARACTER SET utf8mb4 COLLATE utf8mb4_bin NOT NULL,
  request_fingerprint VARCHAR(256) NOT NULL,
  resource_revision VARCHAR(128) NOT NULL,
  status VARCHAR(24) NOT NULL,
  state_version INT UNSIGNED NOT NULL DEFAULT 0,
  start_time DATETIME(3) NULL,
  end_time DATETIME(3) NULL,
  failure_code VARCHAR(128) NULL,
  create_time DATETIME(3) NOT NULL,
  update_time DATETIME(3) NOT NULL,
  create_by VARCHAR(64) NOT NULL,
  update_by VARCHAR(64) NOT NULL,
  PRIMARY KEY (id),
  UNIQUE KEY uk_ops_task_instance_request (workspace_id, trigger_type, request_id),
  KEY idx_ops_task_instance_definition (workspace_id, definition_id, create_time),
  KEY idx_ops_task_instance_status (workspace_id, status, update_time)
) ENGINE=InnoDB DEFAULT CHARACTER SET=utf8mb4 COLLATE=utf8mb4_unicode_ci
  COMMENT='Durable Task execution intent';

CREATE TABLE yak_ops_task_attempt (
  id VARCHAR(64) NOT NULL,
  workspace_id VARCHAR(64) NOT NULL,
  instance_id VARCHAR(64) NOT NULL,
  attempt_number INT UNSIGNED NOT NULL,
  status VARCHAR(24) NOT NULL,
  owner_incarnation VARCHAR(128) NULL,
  dispatch_token VARCHAR(128) NULL,
  runtime_engine VARCHAR(32) NULL,
  runtime_id VARCHAR(128) NULL,
  log_reference VARCHAR(512) NULL,
  failure_code VARCHAR(128) NULL,
  start_time DATETIME(3) NULL,
  end_time DATETIME(3) NULL,
  create_time DATETIME(3) NOT NULL,
  update_time DATETIME(3) NOT NULL,
  create_by VARCHAR(64) NOT NULL,
  update_by VARCHAR(64) NOT NULL,
  PRIMARY KEY (id),
  UNIQUE KEY uk_ops_task_attempt_number (workspace_id, instance_id, attempt_number),
  KEY idx_ops_task_attempt_status (workspace_id, status, update_time)
) ENGINE=InnoDB DEFAULT CHARACTER SET=utf8mb4 COLLATE=utf8mb4_unicode_ci
  COMMENT='One physical submission per platform Task Attempt';

CREATE TABLE yak_ops_task_execution_claim (
  id VARCHAR(64) NOT NULL,
  workspace_id VARCHAR(64) NOT NULL,
  resource_type VARCHAR(32) NOT NULL,
  resource_key VARCHAR(512) CHARACTER SET utf8mb4 COLLATE utf8mb4_bin NOT NULL,
  instance_id VARCHAR(64) NOT NULL,
  create_time DATETIME(3) NOT NULL,
  update_time DATETIME(3) NOT NULL,
  create_by VARCHAR(64) NOT NULL,
  update_by VARCHAR(64) NOT NULL,
  PRIMARY KEY (id),
  UNIQUE KEY uk_ops_task_claim_resource (workspace_id, resource_type, resource_key),
  KEY idx_ops_task_claim_instance (workspace_id, instance_id)
) ENGINE=InnoDB DEFAULT CHARACTER SET=utf8mb4 COLLATE=utf8mb4_unicode_ci
  COMMENT='Exclusive claims held by nonterminal Task Instances';

CREATE TABLE yak_ops_task_execution_owner (
  id VARCHAR(64) NOT NULL,
  host_id VARCHAR(128) NOT NULL,
  state_root VARCHAR(512) NOT NULL,
  owner_incarnation VARCHAR(128) NOT NULL,
  last_seen_time DATETIME(3) NOT NULL,
  create_time DATETIME(3) NOT NULL,
  update_time DATETIME(3) NOT NULL,
  PRIMARY KEY (id)
) ENGINE=InnoDB DEFAULT CHARACTER SET=utf8mb4 COLLATE=utf8mb4_unicode_ci
  COMMENT='Single embedded dispatcher identity; heartbeat expiry is not an execution fence';
