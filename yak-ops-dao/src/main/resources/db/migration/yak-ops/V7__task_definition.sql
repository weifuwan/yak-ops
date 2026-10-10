-- v1.3 Draft Migration: unify Task Definition identity and versions.
-- Keep the existing Data Sync table as the indexed, plugin-owned single-table configuration.
-- Preserve all existing IDs: historical Instance/Schedule foreign references remain unchanged.

CREATE TABLE yak_ops_task_definition (
    id VARCHAR(64) NOT NULL COMMENT '稳定任务定义ID，兼容原数据同步任务ID',
    workspace_id VARCHAR(64) NOT NULL COMMENT '任务所属工作空间，所有操作必须按Workspace隔离',
    name VARCHAR(128) NOT NULL COMMENT '工作空间内唯一的任务名称',
    task_type VARCHAR(64) NOT NULL COMMENT 'Task插件canonical类型，当前仅DATA_SYNC',
    status TINYINT UNSIGNED NOT NULL DEFAULT 0 COMMENT '通用发布状态：0已下线，1已上线',
    definition_version INT UNSIGNED NOT NULL DEFAULT 1 COMMENT '可执行任务定义版本，从1开始',
    remark VARCHAR(500) NULL COMMENT '用户维护的任务说明，不参与可执行版本递增',
    create_time DATETIME(3) NOT NULL COMMENT '任务定义创建时间',
    update_time DATETIME(3) NOT NULL COMMENT '任务定义最近更新时间',
    create_by VARCHAR(64) NOT NULL COMMENT '创建人标识',
    update_by VARCHAR(64) NOT NULL COMMENT '更新人标识',
    PRIMARY KEY (id),
    UNIQUE KEY uk_ops_task_definition_workspace_name (workspace_id, name),
    KEY idx_ops_task_definition_workspace_type_status (workspace_id, task_type, status, update_time)
) ENGINE=InnoDB
  DEFAULT CHARACTER SET=utf8mb4
  COLLATE=utf8mb4_unicode_ci
  COMMENT='通用Task定义表，插件参数由插件专属配置表保存';

INSERT INTO yak_ops_task_definition
    (id, workspace_id, name, task_type, status, definition_version, remark,
     create_time, update_time, create_by, update_by)
SELECT id, workspace_id, name, 'DATA_SYNC', status, definition_version, remark,
       create_time, update_time, create_by, update_by
FROM yak_ops_data_sync_task;

CREATE TABLE yak_ops_task_definition_version (
    id VARCHAR(64) NOT NULL COMMENT '版本记录ID，历史回填复用原Task雪花ID；新增记录使用新雪花ID',
    workspace_id VARCHAR(64) NOT NULL COMMENT '定义所属工作空间',
    definition_id VARCHAR(64) NOT NULL COMMENT '稳定任务定义ID',
    task_type VARCHAR(64) NOT NULL COMMENT '版本所属Task插件canonical类型',
    name VARCHAR(128) NOT NULL COMMENT '记录版本时的定义名称快照',
    version INT UNSIGNED NOT NULL COMMENT '可执行定义版本号',
    parameters_snapshot LONGTEXT NOT NULL COMMENT '脱敏的插件参数JSON历史快照，禁止存储连接凭证',
    create_time DATETIME(3) NOT NULL COMMENT '版本首次产生时间',
    update_time DATETIME(3) NOT NULL COMMENT '最近更新时间，历史版本只读',
    create_by VARCHAR(64) NOT NULL COMMENT '版本创建人标识',
    update_by VARCHAR(64) NOT NULL COMMENT '版本更新人标识',
    PRIMARY KEY (id),
    UNIQUE KEY uk_ops_task_definition_version (workspace_id, definition_id, version),
    KEY idx_ops_task_definition_version_lookup (workspace_id, definition_id, version)
) ENGINE=InnoDB
  DEFAULT CHARACTER SET=utf8mb4
  COLLATE=utf8mb4_unicode_ci
  COMMENT='通用Task定义的不可变可执行版本历史';

-- Old published versions cannot be reconstructed; backfill the currently saved version only.
INSERT INTO yak_ops_task_definition_version
    (id, workspace_id, definition_id, task_type, name, version, parameters_snapshot,
     create_time, update_time, create_by, update_by)
SELECT task.id, task.workspace_id, task.id, 'DATA_SYNC', task.name, task.definition_version,
       JSON_OBJECT(
           'syncType', IF(task.sync_type = 1, 'OFFLINE', 'REALTIME'),
           'writeMode', CASE task.write_mode WHEN 2 THEN 'OVERWRITE' WHEN 3 THEN 'UPSERT' ELSE 'APPEND' END,
           'sourceDataSourceId', task.source_data_source_id,
           'sourceDatabase', task.source_database,
           'sourceSchema', task.source_schema,
           'sourceTable', task.source_table,
           'targetDataSourceId', task.target_data_source_id,
           'targetDatabase', task.target_database,
           'targetSchema', task.target_schema,
           'targetTable', task.target_table,
           'runtimeConfig', IF(task.sync_type = 1, JSON_EXTRACT(task.runtime_config, '$'), NULL),
           'realtimeConfig', IF(task.sync_type = 2, JSON_EXTRACT(task.runtime_config, '$'), NULL),
           'retryPolicy', JSON_EXTRACT(task.retry_policy, '$'),
           'legacyAutoCreateTable', task.auto_create_table,
           'legacyMappingConfig', task.mapping_config
       ),
       task.create_time, task.update_time, task.create_by, task.update_by
FROM yak_ops_data_sync_task task;

-- Remove the duplicated shared ownership from the Data Sync extension.
-- Its ID, Workspace, indexed source/target fields, runtime policy and audit metadata remain.
ALTER TABLE yak_ops_data_sync_task
    DROP INDEX uk_ops_data_sync_task_workspace_name,
    DROP INDEX idx_ops_data_sync_task_realtime_desired,
    DROP COLUMN name,
    DROP COLUMN status,
    DROP COLUMN definition_version,
    DROP COLUMN remark,
    ADD KEY idx_ops_data_sync_task_realtime_desired (sync_type, desired_state, update_time);
