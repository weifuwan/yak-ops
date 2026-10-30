-- Yak Ops v1.3.0 fresh-install database schema.
-- This baseline creates the final schema on an EMPTY database only.
-- Databases with applied V1-V16 history are not automatically upgraded.
-- No business rows are inserted, updated, deleted or converted.

-- Users and workspaces

CREATE TABLE yak_security_user (
    id VARCHAR(64) NOT NULL COMMENT '主键ID，由应用雪花算法生成',
    app_name VARCHAR(64) NOT NULL COMMENT '应用级数据隔离键',
    user_name VARCHAR(64) NOT NULL COMMENT '用户账号',
    pw VARCHAR(2048) NOT NULL COMMENT '单向哈希密码',
    salt VARCHAR(64) NOT NULL DEFAULT '' COMMENT '密码盐兼容字段',
    real_name VARCHAR(128) NULL COMMENT '真实姓名',
    phone VARCHAR(32) NULL COMMENT '手机号码',
    email VARCHAR(128) NULL COMMENT '电子邮箱',
    dept_id BIGINT NULL COMMENT '部门ID，仅作为用户资料字段保留，不创建部门表外键',
    status TINYINT UNSIGNED NOT NULL DEFAULT 1 COMMENT '用户状态：1 正常，2 禁用',
    is_delete TINYINT(1) NOT NULL DEFAULT 0 COMMENT '逻辑删除：0 未删除，1 已删除',
    create_time DATETIME(3) NOT NULL COMMENT '创建时间',
    update_time DATETIME(3) NOT NULL COMMENT '更新时间',
    create_by VARCHAR(64) NOT NULL COMMENT '创建人标识',
    update_by VARCHAR(64) NOT NULL COMMENT '更新人标识',
    active_user_name VARCHAR(64)
        GENERATED ALWAYS AS (IF(is_delete = 0, user_name, NULL)) STORED
        COMMENT '未删除用户唯一键',
    PRIMARY KEY (id),
    UNIQUE KEY uk_security_user_app_user_name (app_name, active_user_name),
    KEY idx_security_user_app_email (app_name, email, is_delete),
    KEY idx_security_user_app_phone (app_name, phone, is_delete),
    KEY idx_security_user_app_create_time (app_name, create_time, is_delete)
) ENGINE=InnoDB
  DEFAULT CHARACTER SET=utf8mb4
  COLLATE=utf8mb4_unicode_ci
  COMMENT='用户账号表';

CREATE TABLE yak_ops_workspace (
    id VARCHAR(64) NOT NULL COMMENT '主键ID，由应用雪花算法生成',
    name VARCHAR(128) NOT NULL COMMENT '工作空间展示名称',
    description VARCHAR(500) NULL COMMENT '工作空间说明',
    create_time DATETIME(3) NOT NULL COMMENT '创建时间',
    update_time DATETIME(3) NOT NULL COMMENT '更新时间',
    create_by VARCHAR(64) NOT NULL COMMENT '创建人标识，仅用于审计',
    update_by VARCHAR(64) NOT NULL COMMENT '更新人标识，仅用于审计',
    PRIMARY KEY (id),
    KEY idx_ops_workspace_update_time (update_time)
) ENGINE=InnoDB
  DEFAULT CHARACTER SET=utf8mb4
  COLLATE=utf8mb4_unicode_ci
  COMMENT='工作空间表';

CREATE TABLE yak_ops_workspace_member (
    id VARCHAR(64) NOT NULL COMMENT '主键ID，由应用雪花算法生成',
    workspace_id VARCHAR(64) NOT NULL COMMENT '工作空间ID',
    user_id VARCHAR(64) NOT NULL COMMENT '成员用户ID',
    role TINYINT UNSIGNED NOT NULL COMMENT '成员角色：1 所有者，2 管理员，3 成员',
    create_time DATETIME(3) NOT NULL COMMENT '加入时间',
    update_time DATETIME(3) NOT NULL COMMENT '更新时间',
    create_by VARCHAR(64) NOT NULL COMMENT '创建人标识',
    update_by VARCHAR(64) NOT NULL COMMENT '更新人标识',
    PRIMARY KEY (id),
    UNIQUE KEY uk_ops_workspace_member_workspace_user (workspace_id, user_id),
    KEY idx_ops_workspace_member_user_workspace (user_id, workspace_id)
) ENGINE=InnoDB
  DEFAULT CHARACTER SET=utf8mb4
  COLLATE=utf8mb4_unicode_ci
  COMMENT='工作空间成员关系表';

CREATE TABLE yak_ops_user_preference (
    id VARCHAR(64) NOT NULL COMMENT '主键ID，由应用雪花算法生成',
    user_id VARCHAR(64) NOT NULL COMMENT '偏好所属用户ID',
    scene VARCHAR(64) NOT NULL COMMENT '偏好场景，例如 PRODUCT_MENU 或 DATASOURCE_CREATE_TYPE',
    item_key VARCHAR(128) NOT NULL COMMENT '场景内稳定业务标识',
    favorite TINYINT(1) NOT NULL DEFAULT 0 COMMENT '是否被用户显式收藏：0 否，1 是',
    sort_order INT UNSIGNED NOT NULL DEFAULT 0 COMMENT '收藏展示顺序，未收藏时为0',
    use_count BIGINT UNSIGNED NOT NULL DEFAULT 0 COMMENT '累计使用次数',
    last_used_time DATETIME(3) NULL COMMENT '最近一次使用时间',
    create_time DATETIME(3) NOT NULL COMMENT '创建时间',
    update_time DATETIME(3) NOT NULL COMMENT '更新时间',
    create_by VARCHAR(64) NOT NULL COMMENT '创建人标识',
    update_by VARCHAR(64) NOT NULL COMMENT '更新人标识',
    PRIMARY KEY (id),
    UNIQUE KEY uk_ops_user_preference_user_scene_item (user_id, scene, item_key)
) ENGINE=InnoDB
  DEFAULT CHARACTER SET=utf8mb4
  COLLATE=utf8mb4_unicode_ci
  COMMENT='用户偏好表';

CREATE TABLE yak_ops_data_source (
    id VARCHAR(64) NOT NULL COMMENT '主键ID，由应用雪花算法生成',
    workspace_id VARCHAR(64) NOT NULL COMMENT '所属工作空间ID，是数据源业务归属与隔离边界',
    name VARCHAR(128) NOT NULL COMMENT '数据源名称，在同一工作空间内唯一',
    db_type VARCHAR(32) NOT NULL COMMENT '数据库类型，对应已注册的数据源插件类型',
    jdbc_url VARCHAR(1024) NOT NULL COMMENT 'JDBC 连接地址',
    environment TINYINT UNSIGNED NOT NULL DEFAULT 0 COMMENT '运行环境：0 开发，1 测试，2 生产',
    conn_status TINYINT UNSIGNED NOT NULL DEFAULT 0 COMMENT '连接状态：0 未测试，1 连接可用，2 连接不可用',
    remark VARCHAR(500) NULL COMMENT '数据源备注',
    connection_params LONGTEXT NOT NULL COMMENT '规范化连接参数 JSON，包含敏感连接信息',
    original_json LONGTEXT NOT NULL COMMENT '前端编辑回显参数 JSON，可能包含敏感连接信息',
    create_time DATETIME(3) NOT NULL COMMENT '创建时间',
    update_time DATETIME(3) NOT NULL COMMENT '更新时间',
    create_by VARCHAR(64) NOT NULL COMMENT '创建人标识',
    update_by VARCHAR(64) NOT NULL COMMENT '更新人标识',
    PRIMARY KEY (id),
    UNIQUE KEY uk_ops_data_source_workspace_name (workspace_id, name),
    KEY idx_ops_data_source_workspace_type (workspace_id, db_type),
    KEY idx_ops_data_source_workspace_environment (workspace_id, environment),
    KEY idx_ops_data_source_workspace_status (workspace_id, conn_status),
    KEY idx_ops_data_source_workspace_update_time (workspace_id, update_time),
  revision BIGINT UNSIGNED NOT NULL DEFAULT 1
    COMMENT 'Monotonic datasource binding revision for verified background execution'
) ENGINE=InnoDB
  DEFAULT CHARACTER SET=utf8mb4
  COLLATE=utf8mb4_unicode_ci
  COMMENT='数据源管理表';

-- Task definitions

CREATE TABLE yak_ops_task_definition (
  id VARCHAR(64) NOT NULL COMMENT '任务定义稳定ID',
  workspace_id VARCHAR(64) NOT NULL COMMENT '所属工作空间ID',
  name VARCHAR(128) NOT NULL COMMENT '任务名称',
  task_type VARCHAR(64) NOT NULL COMMENT '任务插件类型',
  status TINYINT UNSIGNED NOT NULL DEFAULT 0 COMMENT '发布状态0下线1上线',
  current_version INT UNSIGNED NOT NULL COMMENT '当前可执行配置版本',
  remark VARCHAR(500) NULL COMMENT '备注',
  deleted_at DATETIME(3) NULL COMMENT '逻辑删除时间',
  active_name VARCHAR(128) GENERATED ALWAYS AS
    (CASE WHEN deleted_at IS NULL THEN name ELSE NULL END) STORED
    COMMENT '未删除任务唯一名称',
  create_time DATETIME(3) NOT NULL COMMENT '创建时间',
  update_time DATETIME(3) NOT NULL COMMENT '更新时间',
  create_by VARCHAR(64) NOT NULL COMMENT '创建人',
  update_by VARCHAR(64) NOT NULL COMMENT '更新人',
  PRIMARY KEY (id),
  UNIQUE KEY uk_ops_task_definition_workspace_active_name (workspace_id,active_name),
  KEY idx_ops_task_definition_workspace_type (workspace_id,task_type,status,update_time),
  debug TINYINT(1) NOT NULL DEFAULT 0 COMMENT 'Detailed diagnostics for future Task Instances'
) ENGINE=InnoDB DEFAULT CHARACTER SET=utf8mb4 COLLATE=utf8mb4_unicode_ci
  COMMENT='通用任务定义';

CREATE TABLE yak_ops_task_definition_version (
  id VARCHAR(64) NOT NULL COMMENT '定义版本记录ID',
  workspace_id VARCHAR(64) NOT NULL COMMENT '所属工作空间ID',
  definition_id VARCHAR(64) NOT NULL COMMENT '任务定义ID',
  version INT UNSIGNED NOT NULL COMMENT '可执行版本号',
  parameters LONGTEXT NOT NULL COMMENT '不含连接凭证的插件参数',
  parameter_schema_version INT UNSIGNED NOT NULL DEFAULT 1 COMMENT '参数结构版本',
  create_time DATETIME(3) NOT NULL COMMENT '创建时间',
  update_time DATETIME(3) NOT NULL COMMENT '更新时间',
  create_by VARCHAR(64) NOT NULL COMMENT '创建人',
  update_by VARCHAR(64) NOT NULL COMMENT '更新人',
  PRIMARY KEY (id),
  UNIQUE KEY uk_ops_task_version_definition_version (workspace_id,definition_id,version)
) ENGINE=InnoDB DEFAULT CHARACTER SET=utf8mb4 COLLATE=utf8mb4_unicode_ci
  COMMENT='不可变任务参数版本';

-- Task execution

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
  KEY idx_ops_task_instance_status (workspace_id, status, update_time),
  owner_incarnation VARCHAR(128) NULL COMMENT 'Embedded dispatcher that owns launch',
  dispatch_token VARCHAR(128) NULL COMMENT 'Opaque launch ownership token',
  runtime_engine VARCHAR(32) NULL COMMENT 'Submitted execution engine',
  runtime_id VARCHAR(128) NULL COMMENT 'Engine-assigned Job identifier',
  KEY idx_ops_task_instance_owner (status, owner_incarnation),
  log_path VARCHAR(512) NULL COMMENT 'Relative task execution log path',
  debug TINYINT(1) NOT NULL DEFAULT 0 COMMENT 'Detailed diagnostics captured at admission',
  schedule_id VARCHAR(64) NULL COMMENT 'Originating schedule; null for historical or non-scheduled runs',
  schedule_revision BIGINT NULL COMMENT 'Fired registration revision; null when provenance is absent',
  scheduled_time DATETIME(3) NULL COMMENT 'Logical UTC occurrence, separate from execution start time',
  KEY idx_ops_task_instance_schedule (workspace_id, schedule_id, scheduled_time),
  CONSTRAINT ck_ops_task_schedule_provenance CHECK (
    (schedule_id IS NULL AND schedule_revision IS NULL AND scheduled_time IS NULL) OR
    (schedule_id IS NOT NULL AND schedule_revision IS NOT NULL AND schedule_revision >= 1 AND scheduled_time IS NOT NULL)),
  progress_percent TINYINT UNSIGNED NULL COMMENT 'Last measured bounded Source split progress, 0 to 99; null if unavailable',
  INDEX idx_ops_task_instance_latest (workspace_id, definition_id, create_time, id),
  execution_report MEDIUMTEXT NULL COMMENT 'Plugin public execution report fixed with terminal outcome; null when unobserved',
  definition_name VARCHAR(128) NULL
    COMMENT 'Definition display name pinned at Task admission; nullable for historical runs'
) ENGINE=InnoDB DEFAULT CHARACTER SET=utf8mb4 COLLATE=utf8mb4_unicode_ci
  COMMENT='Durable Task execution intent';

-- DolphinScheduler TaskGroup slot accounting (TaskGroupMapper.xml, dev@ee47c371).
-- Yak Ops adds Workspace/resource keys and string Task IDs for atomic multi-target
-- reservations. Slot CAS and release SQL follow the upstream TaskGroup DAO.
CREATE TABLE yak_ops_task_group (
  id BIGINT NOT NULL AUTO_INCREMENT,
  name VARCHAR(255) NOT NULL,
  description VARCHAR(255) NULL,
  user_id INT NULL,
  project_code BIGINT NOT NULL DEFAULT 0,
  workspace_id VARCHAR(64) NOT NULL,
  resource_type VARCHAR(32) NOT NULL,
  resource_key VARCHAR(512) CHARACTER SET utf8mb4 COLLATE utf8mb4_bin NOT NULL,
  group_size INT NOT NULL DEFAULT 1,
  use_size INT NOT NULL DEFAULT 0,
  status TINYINT NOT NULL DEFAULT 1,
  create_time DATETIME(3) NOT NULL,
  update_time DATETIME(3) NOT NULL,
  PRIMARY KEY (id),
  UNIQUE KEY uk_ops_task_group_resource (workspace_id, resource_type, resource_key)
) ENGINE=InnoDB DEFAULT CHARACTER SET=utf8mb4 COLLATE=utf8mb4_unicode_ci
  COMMENT='DolphinScheduler task group slots for exclusive execution resources';

CREATE TABLE yak_ops_task_group_queue (
  id VARCHAR(64) NOT NULL,
  task_id VARCHAR(64) NOT NULL,
  task_name VARCHAR(255) NULL,
  group_id BIGINT NOT NULL,
  workflow_instance_id VARCHAR(64) NULL,
  priority INT NOT NULL DEFAULT 0,
  status TINYINT NOT NULL DEFAULT 1,
  force_start TINYINT NOT NULL DEFAULT 0,
  in_queue TINYINT NOT NULL DEFAULT 1,
  create_time DATETIME(3) NOT NULL,
  update_time DATETIME(3) NOT NULL,
  PRIMARY KEY (id),
  UNIQUE KEY uk_ops_task_group_queue_task (task_id, group_id),
  KEY idx_ops_task_group_queue_task (task_id),
  KEY idx_ops_task_group_queue_group (group_id),
  KEY idx_ops_task_group_queue_workflow (workflow_instance_id),
  KEY idx_ops_task_group_queue_status (status),
  KEY idx_ops_task_group_queue_waiting (group_id, status, in_queue, priority, create_time, id)
) ENGINE=InnoDB DEFAULT CHARACTER SET=utf8mb4 COLLATE=utf8mb4_unicode_ci
  COMMENT='DolphinScheduler task group slot allocations; task IDs are Yak Ops strings';

-- DolphinScheduler JDBC Registry DDL, upstream dev@ee47c371 (Apache-2.0)
CREATE TABLE `yak_ops_jdbc_registry_data`
(
    `id`               bigint(11)   NOT NULL AUTO_INCREMENT COMMENT 'primary key',
    `data_key`         varchar(256) NOT NULL COMMENT 'key, like zookeeper node path',
    `data_value`       text         NOT NULL COMMENT 'data, like zookeeper node value',
    `data_type`        varchar(64)  NOT NULL COMMENT 'EPHEMERAL, PERSISTENT',
    `client_id`        bigint(11)   NOT NULL COMMENT 'client id',
    `create_time`      timestamp    NOT NULL DEFAULT CURRENT_TIMESTAMP COMMENT 'create time',
    `last_update_time` timestamp    NOT NULL DEFAULT CURRENT_TIMESTAMP COMMENT 'last update time',
    PRIMARY KEY (`id`),
    unique Key `uk_yak_ops_jdbc_registry_dataKey` (`data_key`)
) ENGINE = InnoDB
  DEFAULT CHARSET = utf8;

CREATE TABLE `yak_ops_jdbc_registry_lock`
(
    `id`          bigint(11)   NOT NULL AUTO_INCREMENT COMMENT 'primary key',
    `lock_key`    varchar(256) NOT NULL COMMENT 'lock path',
    `lock_owner`  varchar(256) NOT NULL COMMENT 'the lock owner, ip_processId',
    `client_id`   bigint(11)   NOT NULL COMMENT 'client id',
    `create_time` timestamp    NOT NULL DEFAULT CURRENT_TIMESTAMP COMMENT 'create time',
    PRIMARY KEY (`id`),
    unique Key `uk_yak_ops_jdbc_registry_lockKey` (`lock_key`)
) ENGINE = InnoDB
  DEFAULT CHARSET = utf8;

CREATE TABLE `yak_ops_jdbc_registry_client_heartbeat`
(
    `id`                  bigint(11)   NOT NULL COMMENT 'primary key',
    `client_name`         varchar(256) NOT NULL COMMENT 'client name, ip_processId',
    `last_heartbeat_time` bigint(11)   NOT NULL COMMENT 'last heartbeat timestamp',
    `connection_config`   text         NOT NULL COMMENT 'connection config',
    `create_time`         timestamp    NOT NULL DEFAULT CURRENT_TIMESTAMP COMMENT 'create time',
    PRIMARY KEY (`id`)
) ENGINE = InnoDB
  DEFAULT CHARSET = utf8;

CREATE TABLE `yak_ops_jdbc_registry_data_change_event`
(
    `id`                 bigint(11)  NOT NULL AUTO_INCREMENT COMMENT 'primary key',
    `event_type`         varchar(64) NOT NULL COMMENT 'ADD, UPDATE, DELETE',
    `jdbc_registry_data` text        NOT NULL COMMENT 'jdbc registry data',
    `create_time`        timestamp   NOT NULL DEFAULT CURRENT_TIMESTAMP COMMENT 'create time',
    PRIMARY KEY (`id`)
) ENGINE = InnoDB
  DEFAULT CHARSET = utf8;

-- Scheduling

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

CREATE TABLE yak_ops_schedule_fire (
  id VARCHAR(64) NOT NULL COMMENT 'Stable final occurrence result ID',
  workspace_id VARCHAR(64) NOT NULL COMMENT 'Workspace owning this result and target',
  schedule_id VARCHAR(64) NOT NULL COMMENT 'Originating schedule identity retained after deletion',
  schedule_revision BIGINT NOT NULL COMMENT 'Immutable fired registration revision',
  target_type VARCHAR(32) NOT NULL COMMENT 'Historical root target category',
  target_id VARCHAR(64) NOT NULL COMMENT 'Historical target Definition identity for authorization',
  scheduled_time DATETIME(3) NOT NULL COMMENT 'Logical UTC occurrence at millisecond precision',
  fire_time DATETIME(3) NOT NULL COMMENT 'Actual UTC callback time, not part of idempotency',
  resolved_time DATETIME(3) NOT NULL COMMENT 'UTC final submission resolution time',
  request_id VARCHAR(128) CHARACTER SET utf8mb4 COLLATE utf8mb4_bin NOT NULL COMMENT 'Stable request key in the SCHEDULE namespace',
  outcome VARCHAR(32) NOT NULL COMMENT 'ADMITTED, SKIPPED, REJECTED, or SUBMISSION_FAILED; not Task status',
  reason VARCHAR(64) NULL COMMENT 'Safe non-admission reason; null for ADMITTED',
  instance_id VARCHAR(64) NULL COMMENT 'Admitted Task Instance; null for non-admission',
  create_time DATETIME(3) NOT NULL COMMENT 'Creation audit time',
  update_time DATETIME(3) NOT NULL COMMENT 'Result persistence audit time',
  create_by VARCHAR(64) NOT NULL COMMENT 'Authorizing or configuration-owning actor',
  update_by VARCHAR(64) NOT NULL COMMENT 'Result audit actor',
  PRIMARY KEY (id),
  UNIQUE KEY uk_ops_schedule_fire_occurrence (workspace_id, schedule_id, schedule_revision, scheduled_time),
  UNIQUE KEY uk_ops_schedule_fire_request (workspace_id, request_id),
  KEY idx_ops_schedule_fire_history (workspace_id, schedule_id, scheduled_time, id),
  CONSTRAINT ck_ops_schedule_fire_revision CHECK (schedule_revision >= 1),
  CONSTRAINT ck_ops_schedule_fire_outcome CHECK (outcome IN ('ADMITTED','SKIPPED','REJECTED','SUBMISSION_FAILED')),
  CONSTRAINT ck_ops_schedule_fire_instance CHECK (
    (outcome = 'ADMITTED' AND instance_id IS NOT NULL AND reason IS NULL) OR
    (outcome <> 'ADMITTED' AND instance_id IS NULL AND reason IS NOT NULL))
) ENGINE=InnoDB DEFAULT CHARACTER SET=utf8mb4 COLLATE=utf8mb4_unicode_ci
  COMMENT='Final schedule submission results; execution belongs to Task Instances';

-- Quartz persistent scheduler

CREATE TABLE YAK_QRTZ_JOB_DETAILS (
  SCHED_NAME VARCHAR(120) NOT NULL COMMENT 'Engine namespace',
  JOB_NAME VARCHAR(190) NOT NULL COMMENT 'Engine job identity',
  JOB_GROUP VARCHAR(190) NOT NULL COMMENT 'Engine job group',
  DESCRIPTION VARCHAR(250) NULL COMMENT 'Optional engine description',
  JOB_CLASS_NAME VARCHAR(250) NOT NULL COMMENT 'Quartz job class',
  IS_DURABLE VARCHAR(1) NOT NULL COMMENT 'Retain job when no triggers remain',
  IS_NONCONCURRENT VARCHAR(1) NOT NULL COMMENT 'Serialize notification jobs by JobKey',
  IS_UPDATE_DATA VARCHAR(1) NOT NULL COMMENT 'Whether the job persists changed data',
  REQUESTS_RECOVERY VARCHAR(1) NOT NULL COMMENT 'Engine recovery request flag',
  JOB_DATA BLOB NULL COMMENT 'String-only registration properties; no credentials or serialized beans',
  PRIMARY KEY (SCHED_NAME,JOB_NAME,JOB_GROUP),
  KEY IDX_YAK_QRTZ_J_REQ_RECOVERY (SCHED_NAME,REQUESTS_RECOVERY),
  KEY IDX_YAK_QRTZ_J_GRP (SCHED_NAME,JOB_GROUP)
) ENGINE=InnoDB DEFAULT CHARACTER SET=utf8mb4 COLLATE=utf8mb4_bin
  COMMENT='Quartz job details; engine-owned state';

CREATE TABLE YAK_QRTZ_TRIGGERS (
  SCHED_NAME VARCHAR(120) NOT NULL COMMENT 'Engine namespace',
  TRIGGER_NAME VARCHAR(190) NOT NULL COMMENT 'Engine trigger identity',
  TRIGGER_GROUP VARCHAR(190) NOT NULL COMMENT 'Engine trigger group',
  JOB_NAME VARCHAR(190) NOT NULL COMMENT 'Engine job identity',
  JOB_GROUP VARCHAR(190) NOT NULL COMMENT 'Engine job group',
  DESCRIPTION VARCHAR(250) NULL COMMENT 'Optional engine description',
  NEXT_FIRE_TIME BIGINT NULL COMMENT 'Next epoch-millisecond time; null when exhausted',
  PREV_FIRE_TIME BIGINT NULL COMMENT 'Previous epoch-millisecond time',
  PRIORITY INTEGER NULL COMMENT 'Engine acquisition priority',
  TRIGGER_STATE VARCHAR(16) NOT NULL COMMENT 'Engine trigger acquisition state',
  TRIGGER_TYPE VARCHAR(8) NOT NULL COMMENT 'Quartz trigger subtype',
  START_TIME BIGINT NOT NULL COMMENT 'First eligible epoch-millisecond time',
  END_TIME BIGINT NULL COMMENT 'Optional last eligible epoch-millisecond time',
  CALENDAR_NAME VARCHAR(190) NULL COMMENT 'Optional calendar identity',
  MISFIRE_INSTR SMALLINT NULL COMMENT 'Quartz subtype misfire instruction',
  JOB_DATA BLOB NULL COMMENT 'String-only registration properties; no credentials or serialized beans',
  PRIMARY KEY (SCHED_NAME,TRIGGER_NAME,TRIGGER_GROUP),
  FOREIGN KEY (SCHED_NAME,JOB_NAME,JOB_GROUP) REFERENCES YAK_QRTZ_JOB_DETAILS(SCHED_NAME,JOB_NAME,JOB_GROUP),
  KEY IDX_YAK_QRTZ_T_J (SCHED_NAME,JOB_NAME,JOB_GROUP),
  KEY IDX_YAK_QRTZ_T_JG (SCHED_NAME,JOB_GROUP),
  KEY IDX_YAK_QRTZ_T_C (SCHED_NAME,CALENDAR_NAME),
  KEY IDX_YAK_QRTZ_T_G (SCHED_NAME,TRIGGER_GROUP),
  KEY IDX_YAK_QRTZ_T_STATE (SCHED_NAME,TRIGGER_STATE),
  KEY IDX_YAK_QRTZ_T_N_STATE (SCHED_NAME,TRIGGER_NAME,TRIGGER_GROUP,TRIGGER_STATE),
  KEY IDX_YAK_QRTZ_T_N_G_STATE (SCHED_NAME,TRIGGER_GROUP,TRIGGER_STATE),
  KEY IDX_YAK_QRTZ_T_NEXT_FIRE_TIME (SCHED_NAME,NEXT_FIRE_TIME),
  KEY IDX_YAK_QRTZ_T_NFT_ST (SCHED_NAME,TRIGGER_STATE,NEXT_FIRE_TIME),
  KEY IDX_YAK_QRTZ_T_NFT_MISFIRE (SCHED_NAME,MISFIRE_INSTR,NEXT_FIRE_TIME),
  KEY IDX_YAK_QRTZ_T_NFT_ST_MISFIRE (SCHED_NAME,MISFIRE_INSTR,NEXT_FIRE_TIME,TRIGGER_STATE),
  KEY IDX_YAK_QRTZ_T_NFT_ST_MISFIRE_GRP (SCHED_NAME,MISFIRE_INSTR,NEXT_FIRE_TIME,TRIGGER_GROUP,TRIGGER_STATE)
) ENGINE=InnoDB DEFAULT CHARACTER SET=utf8mb4 COLLATE=utf8mb4_bin
  COMMENT='Quartz triggers; engine-owned state';

CREATE TABLE YAK_QRTZ_SIMPLE_TRIGGERS (
  SCHED_NAME VARCHAR(120) NOT NULL COMMENT 'Engine namespace',
  TRIGGER_NAME VARCHAR(190) NOT NULL COMMENT 'Engine trigger identity',
  TRIGGER_GROUP VARCHAR(190) NOT NULL COMMENT 'Engine trigger group',
  REPEAT_COUNT BIGINT NOT NULL COMMENT 'Quartz repeat count',
  REPEAT_INTERVAL BIGINT NOT NULL COMMENT 'Quartz repeat interval in milliseconds',
  TIMES_TRIGGERED BIGINT NOT NULL COMMENT 'Engine delivery count',
  PRIMARY KEY (SCHED_NAME,TRIGGER_NAME,TRIGGER_GROUP),
  FOREIGN KEY (SCHED_NAME,TRIGGER_NAME,TRIGGER_GROUP) REFERENCES YAK_QRTZ_TRIGGERS(SCHED_NAME,TRIGGER_NAME,TRIGGER_GROUP)
) ENGINE=InnoDB DEFAULT CHARACTER SET=utf8mb4 COLLATE=utf8mb4_bin
  COMMENT='Quartz simple triggers; engine-owned state';

CREATE TABLE YAK_QRTZ_CRON_TRIGGERS (
  SCHED_NAME VARCHAR(120) NOT NULL COMMENT 'Engine namespace',
  TRIGGER_NAME VARCHAR(190) NOT NULL COMMENT 'Engine trigger identity',
  TRIGGER_GROUP VARCHAR(190) NOT NULL COMMENT 'Engine trigger group',
  CRON_EXPRESSION VARCHAR(256) NOT NULL COMMENT 'Quartz Cron expression, matching the schedule API length',
  TIME_ZONE_ID VARCHAR(80) NULL COMMENT 'Named Cron time zone',
  PRIMARY KEY (SCHED_NAME,TRIGGER_NAME,TRIGGER_GROUP),
  FOREIGN KEY (SCHED_NAME,TRIGGER_NAME,TRIGGER_GROUP) REFERENCES YAK_QRTZ_TRIGGERS(SCHED_NAME,TRIGGER_NAME,TRIGGER_GROUP)
) ENGINE=InnoDB DEFAULT CHARACTER SET=utf8mb4 COLLATE=utf8mb4_bin
  COMMENT='Quartz cron triggers; engine-owned state';

CREATE TABLE YAK_QRTZ_SIMPROP_TRIGGERS (
  SCHED_NAME VARCHAR(120) NOT NULL COMMENT 'Engine namespace',
  TRIGGER_NAME VARCHAR(190) NOT NULL COMMENT 'Engine trigger identity',
  TRIGGER_GROUP VARCHAR(190) NOT NULL COMMENT 'Engine trigger group',
  STR_PROP_1 VARCHAR(512) NULL COMMENT 'Optional simple trigger string property',
  STR_PROP_2 VARCHAR(512) NULL COMMENT 'Optional simple trigger string property',
  STR_PROP_3 VARCHAR(512) NULL COMMENT 'Optional simple trigger string property',
  INT_PROP_1 INT NULL COMMENT 'Optional simple trigger integer property',
  INT_PROP_2 INT NULL COMMENT 'Optional simple trigger integer property',
  LONG_PROP_1 BIGINT NULL COMMENT 'Optional simple trigger long property',
  LONG_PROP_2 BIGINT NULL COMMENT 'Optional simple trigger long property',
  DEC_PROP_1 NUMERIC(13,4) NULL COMMENT 'Optional simple trigger decimal property',
  DEC_PROP_2 NUMERIC(13,4) NULL COMMENT 'Optional simple trigger decimal property',
  BOOL_PROP_1 VARCHAR(1) NULL COMMENT 'Optional simple trigger boolean property',
  BOOL_PROP_2 VARCHAR(1) NULL COMMENT 'Optional simple trigger boolean property',
  PRIMARY KEY (SCHED_NAME,TRIGGER_NAME,TRIGGER_GROUP),
  FOREIGN KEY (SCHED_NAME,TRIGGER_NAME,TRIGGER_GROUP) REFERENCES YAK_QRTZ_TRIGGERS(SCHED_NAME,TRIGGER_NAME,TRIGGER_GROUP)
) ENGINE=InnoDB DEFAULT CHARACTER SET=utf8mb4 COLLATE=utf8mb4_bin
  COMMENT='Quartz simprop triggers; engine-owned state';

CREATE TABLE YAK_QRTZ_BLOB_TRIGGERS (
  SCHED_NAME VARCHAR(120) NOT NULL COMMENT 'Engine namespace',
  TRIGGER_NAME VARCHAR(190) NOT NULL COMMENT 'Engine trigger identity',
  TRIGGER_GROUP VARCHAR(190) NOT NULL COMMENT 'Engine trigger group',
  BLOB_DATA BLOB NULL COMMENT 'Quartz binary trigger state, unused by Cron registrations',
  PRIMARY KEY (SCHED_NAME,TRIGGER_NAME,TRIGGER_GROUP),
  FOREIGN KEY (SCHED_NAME,TRIGGER_NAME,TRIGGER_GROUP) REFERENCES YAK_QRTZ_TRIGGERS(SCHED_NAME,TRIGGER_NAME,TRIGGER_GROUP)
) ENGINE=InnoDB DEFAULT CHARACTER SET=utf8mb4 COLLATE=utf8mb4_bin
  COMMENT='Quartz blob triggers; engine-owned state';

CREATE TABLE YAK_QRTZ_CALENDARS (
  SCHED_NAME VARCHAR(120) NOT NULL COMMENT 'Engine namespace',
  CALENDAR_NAME VARCHAR(190) NOT NULL COMMENT 'Quartz calendar identity',
  CALENDAR BLOB NOT NULL COMMENT 'Quartz calendar state',
  PRIMARY KEY (SCHED_NAME,CALENDAR_NAME)
) ENGINE=InnoDB DEFAULT CHARACTER SET=utf8mb4 COLLATE=utf8mb4_bin
  COMMENT='Quartz calendars; engine-owned state';

CREATE TABLE YAK_QRTZ_PAUSED_TRIGGER_GRPS (
  SCHED_NAME VARCHAR(120) NOT NULL COMMENT 'Engine namespace',
  TRIGGER_GROUP VARCHAR(190) NOT NULL COMMENT 'Engine trigger group',
  PRIMARY KEY (SCHED_NAME,TRIGGER_GROUP)
) ENGINE=InnoDB DEFAULT CHARACTER SET=utf8mb4 COLLATE=utf8mb4_bin
  COMMENT='Quartz paused trigger grps; engine-owned state';

CREATE TABLE YAK_QRTZ_FIRED_TRIGGERS (
  SCHED_NAME VARCHAR(120) NOT NULL COMMENT 'Engine namespace',
  ENTRY_ID VARCHAR(95) NOT NULL COMMENT 'Quartz callback identifier, not business idempotency',
  TRIGGER_NAME VARCHAR(190) NOT NULL COMMENT 'Engine trigger identity',
  TRIGGER_GROUP VARCHAR(190) NOT NULL COMMENT 'Engine trigger group',
  INSTANCE_NAME VARCHAR(190) NOT NULL COMMENT 'Quartz owner identity',
  FIRED_TIME BIGINT NOT NULL COMMENT 'Actual epoch-millisecond firing time',
  SCHED_TIME BIGINT NOT NULL COMMENT 'Scheduled epoch-millisecond firing time',
  PRIORITY INTEGER NOT NULL COMMENT 'Quartz priority',
  STATE VARCHAR(16) NOT NULL COMMENT 'Engine firing state',
  JOB_NAME VARCHAR(190) NULL COMMENT 'Optional owning job identity',
  JOB_GROUP VARCHAR(190) NULL COMMENT 'Optional owning job group',
  IS_NONCONCURRENT VARCHAR(1) NULL COMMENT 'Engine concurrency flag',
  REQUESTS_RECOVERY VARCHAR(1) NULL COMMENT 'Engine recovery flag',
  PRIMARY KEY (SCHED_NAME,ENTRY_ID),
  KEY IDX_YAK_QRTZ_FT_TRIG_INST_NAME (SCHED_NAME,INSTANCE_NAME),
  KEY IDX_YAK_QRTZ_FT_INST_JOB_REQ_RCVRY (SCHED_NAME,INSTANCE_NAME,REQUESTS_RECOVERY),
  KEY IDX_YAK_QRTZ_FT_J_G (SCHED_NAME,JOB_NAME,JOB_GROUP),
  KEY IDX_YAK_QRTZ_FT_JG (SCHED_NAME,JOB_GROUP),
  KEY IDX_YAK_QRTZ_FT_T_G (SCHED_NAME,TRIGGER_NAME,TRIGGER_GROUP),
  KEY IDX_YAK_QRTZ_FT_TG (SCHED_NAME,TRIGGER_GROUP)
) ENGINE=InnoDB DEFAULT CHARACTER SET=utf8mb4 COLLATE=utf8mb4_bin
  COMMENT='Quartz fired triggers; engine-owned state';

CREATE TABLE YAK_QRTZ_SCHEDULER_STATE (
  SCHED_NAME VARCHAR(120) NOT NULL COMMENT 'Engine namespace',
  INSTANCE_NAME VARCHAR(190) NOT NULL COMMENT 'Quartz instance identity',
  LAST_CHECKIN_TIME BIGINT NOT NULL COMMENT 'Engine check-in epoch milliseconds',
  CHECKIN_INTERVAL BIGINT NOT NULL COMMENT 'Engine check-in interval',
  PRIMARY KEY (SCHED_NAME,INSTANCE_NAME)
) ENGINE=InnoDB DEFAULT CHARACTER SET=utf8mb4 COLLATE=utf8mb4_bin
  COMMENT='Quartz scheduler state; engine-owned state';

CREATE TABLE YAK_QRTZ_LOCKS (
  SCHED_NAME VARCHAR(120) NOT NULL COMMENT 'Engine namespace',
  LOCK_NAME VARCHAR(40) NOT NULL COMMENT 'Quartz store lock identity',
  PRIMARY KEY (SCHED_NAME,LOCK_NAME)
) ENGINE=InnoDB DEFAULT CHARACTER SET=utf8mb4 COLLATE=utf8mb4_bin
  COMMENT='Quartz locks; engine-owned state';

-- Offline incremental sync

CREATE TABLE yak_ops_task_cursor (
  workspace_id VARCHAR(64) NOT NULL COMMENT 'Workspace owning this incremental state',
  definition_id VARCHAR(64) NOT NULL COMMENT 'Task definition owning this table cursor',
  table_key CHAR(64) NOT NULL COMMENT 'Stable SHA-256 of explicit source and target table identity',
  binding CHAR(64) NOT NULL COMMENT 'SHA-256 of all incremental interpretation and routing options',
  cursor_value VARCHAR(128) NULL COMMENT 'Committed position; null until a successful first batch',
  pending_value VARCHAR(128) NULL COMMENT 'Frozen next position reused when an earlier run fails',
  initialized TINYINT(1) NOT NULL DEFAULT 0 COMMENT 'Whether an initial batch has successfully committed',
  revision BIGINT NOT NULL DEFAULT 0 COMMENT 'Monotonic optimistic progress revision',
  PRIMARY KEY (workspace_id, definition_id, table_key)
) COMMENT='Durable table-granular offline incremental progress';

CREATE TABLE yak_ops_task_cursor_batch (
  workspace_id VARCHAR(64) NOT NULL COMMENT 'Owning Workspace',
  instance_id VARCHAR(64) NOT NULL COMMENT 'Task Instance whose snapshot is immutable',
  table_key CHAR(64) NOT NULL COMMENT 'Source-to-target table identity digest',
  binding CHAR(64) NOT NULL COMMENT 'Frozen incremental route configuration digest',
  mode VARCHAR(16) NOT NULL COMMENT 'Incremental comparison mode',
  column_name VARCHAR(128) NOT NULL COMMENT 'Validated source column',
  initial_value VARCHAR(128) NULL COMMENT 'User-configured first cursor if applicable',
  lower_value VARCHAR(128) NULL COMMENT 'Committed cursor when this batch was frozen',
  upper_value VARCHAR(128) NOT NULL COMMENT 'Frozen next cursor for deterministic retry',
  allow_initial_load TINYINT(1) NOT NULL COMMENT 'Whether an initial full scan is configured',
  initial_load TINYINT(1) NOT NULL COMMENT 'Whether this Instance physically runs a full initial scan',
  expected_version BIGINT NOT NULL COMMENT 'Cursor revision expected when result commits',
  PRIMARY KEY (workspace_id, instance_id, table_key)
) COMMENT='Immutable per-Instance incremental window and progress candidate';
