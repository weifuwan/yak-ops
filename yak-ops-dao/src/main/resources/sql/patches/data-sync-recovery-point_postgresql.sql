CREATE TABLE IF NOT EXISTS t_yak_ops_data_sync_recovery_point (
    id BIGSERIAL PRIMARY KEY,
    task_instance_id INTEGER NOT NULL UNIQUE,
    project_code BIGINT NOT NULL,
    task_code BIGINT NOT NULL,
    task_definition_version INTEGER NOT NULL,
    execution_id VARCHAR(64),
    job_id VARCHAR(64),
    runtime_mode VARCHAR(32) NOT NULL,
    recovery_type VARCHAR(32) NOT NULL,
    restore_path`? VARCHAR(2048) DEFAULT NULL,
    state VARCHAR(16) NOT NULL,
    created_at TIMESTAMP NOT NULL DEFAULT CURRENT_TIMESTAMP,
    used_by_workflow_instance_id INTEGER,
    used_at TIMESTAMP
);
CREATE INDEX IF NOT EXISTS idx_data_sync_recovery_state_created
    ON t_yak_ops_data_sync_recovery_point(state, created_at);
CREATE INDEX IF NOT EXISTS idx_data_sync_recovery_used_workflow
    ON t_yak_ops_data_sync_recovery_point(used_by_workflow_instance_id);
