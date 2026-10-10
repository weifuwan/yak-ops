-- v1.3 Draft Migration: one persistent Task instance/attempt/event model and generic Schedule targets.
-- No data is copied or duplicated; original Task/Execution/Attempt/Schedule IDs remain unchanged.
-- Released V1-V3 and existing draft V4-V7 are not modified.

RENAME TABLE yak_ops_data_sync_instance TO yak_ops_task_instance;

ALTER TABLE yak_ops_task_instance
    ADD COLUMN task_type VARCHAR(64) NOT NULL DEFAULT 'DATA_SYNC'
        COMMENT '稳定任务插件类型；已有实例全部为DATA_SYNC' AFTER workspace_id,
    ADD COLUMN workflow_instance_id VARCHAR(64) NULL
        COMMENT '可选上游工作流实例ID；独立运行时为空' AFTER task_version,
    ADD COLUMN workflow_node_id VARCHAR(64) NULL
        COMMENT '可选工作流节点ID；独立运行时为空' AFTER workflow_instance_id,
    ADD COLUMN schedule_id VARCHAR(64) NULL
        COMMENT '本次调度触发来源ID；手动触发为空' AFTER trigger_type,
    ADD COLUMN scheduled_fire_time DATETIME(3) NULL
        COMMENT '调度计划触发时间；手动触发为空' AFTER schedule_id,
    MODIFY COLUMN sync_type TINYINT UNSIGNED NULL
        COMMENT 'DATA_SYNC特有的同步类型：1离线、2实时；其他Task类型可为空',
    ADD KEY idx_ops_task_instance_workspace_type_create (workspace_id, task_type, create_time),
    ADD KEY idx_ops_task_instance_workflow (workspace_id, workflow_instance_id, workflow_node_id);

RENAME TABLE yak_ops_data_sync_attempt TO yak_ops_task_attempt;

ALTER TABLE yak_ops_task_attempt
    CHANGE COLUMN execution_id instance_id VARCHAR(64) NOT NULL
        COMMENT '通用Task Instance ID；历史Execution ID不变',
    ADD COLUMN log_uri VARCHAR(1024) NULL
        COMMENT '单次Attempt执行日志的受控存储定位符，不是外部可任意访问的文件路径' AFTER error_message,
    ADD COLUMN worker_id VARCHAR(128) NULL
        COMMENT '未来执行Worker身份；历史本地Attempt为空' AFTER log_uri;

RENAME TABLE yak_ops_data_sync_execution_event TO yak_ops_task_event;

ALTER TABLE yak_ops_task_event
    CHANGE COLUMN execution_id instance_id VARCHAR(64) NOT NULL
        COMMENT '通用Task Instance ID；历史Execution ID不变';

RENAME TABLE yak_ops_data_sync_schedule TO yak_ops_schedule;

ALTER TABLE yak_ops_schedule
    CHANGE COLUMN task_id target_id VARCHAR(64) NOT NULL
        COMMENT '调度目标ID；当前为Task Definition ID',
    ADD COLUMN target_type VARCHAR(16) NOT NULL DEFAULT 'TASK'
        COMMENT '调度目标：TASK；未来可扩展WORKFLOW' AFTER workspace_id,
    DROP INDEX uk_ops_data_sync_schedule_workspace_task,
    ADD UNIQUE KEY uk_ops_schedule_workspace_target (workspace_id, target_type, target_id);

-- Quartz still uses the business schedule ID and validated Task ID. This migration never
-- creates triggers, executes tasks, rewrites historical events or invents log locations.
