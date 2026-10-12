CREATE TABLE yak_ops_task_instance (
  id VARCHAR(64) NOT NULL COMMENT 'Stable logical run identifier',
  workspace_id VARCHAR(64) NOT NULL COMMENT 'Workspace owning this run',
  definition_id VARCHAR(64) NOT NULL COMMENT 'Task Definition pinned for execution',
  definition_version INT UNSIGNED NOT NULL COMMENT 'Immutable pinned Definition version',
  task_type VARCHAR(64) NOT NULL COMMENT 'Registered Task plugin type',
  trigger_type VARCHAR(24) NOT NULL COMMENT 'Submission namespace for request identity',
  request_id VARCHAR(128) CHARACTER SET utf8mb4 COLLATE utf8mb4_bin NOT NULL COMMENT 'Case-sensitive idempotency key',
  request_fingerprint VARCHAR(256) NOT NULL COMMENT 'Non-secret request intent fingerprint',
  resource_revision VARCHAR(128) NOT NULL COMMENT 'Admitted resource binding revision',
  status VARCHAR(24) NOT NULL COMMENT 'Business execution lifecycle state',
  state_version INT UNSIGNED NOT NULL DEFAULT 0 COMMENT 'Optimistic lifecycle update version',
  start_time DATETIME(3) NULL COMMENT 'Observed start time, absent before execution',
  end_time DATETIME(3) NULL COMMENT 'Terminal time, absent while active',
  failure_code VARCHAR(128) NULL COMMENT 'Safe failure category, absent unless failed',
  create_time DATETIME(3) NOT NULL COMMENT 'Creation timestamp',
  update_time DATETIME(3) NOT NULL COMMENT 'Last update timestamp',
  create_by VARCHAR(64) NOT NULL COMMENT 'Actor creating this run',
  update_by VARCHAR(64) NOT NULL COMMENT 'Actor updating this run',
  PRIMARY KEY (id),
  UNIQUE KEY uk_ops_task_instance_request (workspace_id, trigger_type, request_id),
  KEY idx_ops_task_instance_definition (workspace_id, definition_id, create_time),
  KEY idx_ops_task_instance_status (workspace_id, status, update_time)
) ENGINE=InnoDB DEFAULT CHARACTER SET=utf8mb4 COLLATE=utf8mb4_unicode_ci
  COMMENT='Durable Task execution intent';

CREATE TABLE yak_ops_task_attempt (
  id VARCHAR(64) NOT NULL COMMENT 'Stable physical submission identifier',
  workspace_id VARCHAR(64) NOT NULL COMMENT 'Workspace owning the submission',
  instance_id VARCHAR(64) NOT NULL COMMENT 'Logical Task Instance owning this Attempt',
  attempt_number INT UNSIGNED NOT NULL COMMENT 'Ordinal starting at one within the Instance',
  status VARCHAR(24) NOT NULL COMMENT 'Durable dispatch or physical execution state',
  owner_incarnation VARCHAR(128) NULL COMMENT 'Dispatcher owner, absent before launch claim',
  dispatch_token VARCHAR(128) NULL COMMENT 'Opaque dispatch claim token, absent before launch',
  runtime_engine VARCHAR(32) NULL COMMENT 'Execution engine name, absent until known',
  runtime_id VARCHAR(128) NULL COMMENT 'Physical Job identifier, absent until known',
  log_reference VARCHAR(512) NULL COMMENT 'Opaque diagnostics reference, absent until available',
  failure_code VARCHAR(128) NULL COMMENT 'Safe failure category, absent when healthy',
  start_time DATETIME(3) NULL COMMENT 'Observed physical start time',
  end_time DATETIME(3) NULL COMMENT 'Confirmed physical terminal time',
  create_time DATETIME(3) NOT NULL COMMENT 'Creation timestamp',
  update_time DATETIME(3) NOT NULL COMMENT 'Last update timestamp',
  create_by VARCHAR(64) NOT NULL COMMENT 'Actor creating this Attempt',
  update_by VARCHAR(64) NOT NULL COMMENT 'Actor updating this Attempt',
  PRIMARY KEY (id),
  UNIQUE KEY uk_ops_task_attempt_number (workspace_id, instance_id, attempt_number),
  KEY idx_ops_task_attempt_status (workspace_id, status, update_time)
) ENGINE=InnoDB DEFAULT CHARACTER SET=utf8mb4 COLLATE=utf8mb4_unicode_ci
  COMMENT='One physical submission per platform Task Attempt';

CREATE TABLE yak_ops_task_execution_claim (
  id VARCHAR(64) NOT NULL COMMENT 'Stable exclusive claim identifier',
  workspace_id VARCHAR(64) NOT NULL COMMENT 'Workspace owning the resource identity',
  resource_type VARCHAR(32) NOT NULL COMMENT 'Resource category requiring exclusive use',
  resource_key VARCHAR(512) CHARACTER SET utf8mb4 COLLATE utf8mb4_bin NOT NULL COMMENT 'Case-sensitive canonical resource identity',
  instance_id VARCHAR(64) NOT NULL COMMENT 'Instance authorized to release the claim',
  create_time DATETIME(3) NOT NULL COMMENT 'Claim acquisition timestamp',
  update_time DATETIME(3) NOT NULL COMMENT 'Last claim update timestamp',
  create_by VARCHAR(64) NOT NULL COMMENT 'Actor creating this claim',
  update_by VARCHAR(64) NOT NULL COMMENT 'Actor updating this claim',
  PRIMARY KEY (id),
  UNIQUE KEY uk_ops_task_claim_resource (workspace_id, resource_type, resource_key),
  KEY idx_ops_task_claim_instance (workspace_id, instance_id)
) ENGINE=InnoDB DEFAULT CHARACTER SET=utf8mb4 COLLATE=utf8mb4_unicode_ci
  COMMENT='Exclusive claims held by nonterminal Task Instances';

CREATE TABLE yak_ops_task_execution_owner (
  id VARCHAR(64) NOT NULL COMMENT 'Singleton embedded owner record identity',
  host_id VARCHAR(128) NOT NULL COMMENT 'Host holding the local execution lock',
  state_root VARCHAR(512) NOT NULL COMMENT 'Canonical directory for execution owner lock',
  owner_incarnation VARCHAR(128) NOT NULL COMMENT 'JVM incarnation with dispatch authority',
  last_seen_time DATETIME(3) NOT NULL COMMENT 'Diagnostic heartbeat, not an execution fence',
  create_time DATETIME(3) NOT NULL COMMENT 'Owner record creation timestamp',
  update_time DATETIME(3) NOT NULL COMMENT 'Last owner record update timestamp',
  PRIMARY KEY (id)
) ENGINE=InnoDB DEFAULT CHARACTER SET=utf8mb4 COLLATE=utf8mb4_unicode_ci
  COMMENT='Single embedded dispatcher identity; heartbeat expiry is not an execution fence';
