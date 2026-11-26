CREATE TABLE IF NOT EXISTS t_yak_ops_data_sync_execution_report (
    id BIGSERIAL PRIMARY KEY,
    execution_id VARCHAR(64) NOT NULL UNIQUE,
    task_instance_id INTEGER NOT NULL,
    submitted_at BIGINT NOT NULL DEFAULT 0,
    job_id VARCHAR(64) NOT NULL,
    deployment_mode VARCHAR(32) NOT NULL,
    cluster_id VARCHAR(128),
    execution_state VARCHAR(32) NOT NULL,
    metrics_state VARCHAR(32) NOT NULL,
    report_version SMALLINT NOT NULL DEFAULT 1,
    latest_sequence BIGINT NOT NULL DEFAULT 0,
    latest_collected_at BIGINT,
    latest_snapshot_json TEXT,
    final_sequence BIGINT,
    final_collected_at BIGINT,
    final_snapshot_json TEXT,
    finalized BOOLEAN NOT NULL DEFAULT FALSE,
    last_metrics_error VARCHAR(1000),
    create_time TIMESTAMP NOT NULL DEFAULT CURRENT_TIMESTAMP,
    update_time TIMESTAMP NOT NULL DEFAULT CURRENT_TIMESTAMP
);
CREATE INDEX IF NOT EXISTS idx_data_sync_report_task_update
    ON t_yak_ops_data_sync_execution_report(task_instance_id, update_time);
CREATE INDEX IF NOT EXISTS idx_data_sync_report_job_id
    ON t_yak_ops_data_sync_execution_report(job_id);

CREATE TABLE IF NOT EXISTS t_yak_ops_data_sync_table_report (
    id BIGSERIAL PRIMARY KEY,
    execution_id VARCHAR(64) NOT NULL,
    table_key CHAR(64) NOT NULL,
    source_table VARCHAR(512),
    sink_table VARCHAR(512),
    latest_metrics_json TEXT,
    final_metrics_json TEXT,
    latest_sequence BIGINT NOT NULL DEFAULT 0,
    latest_collected_at BIGINT,
    final_collected_at BIGINT,
    create_time TIMESTAMP NOT NULL DEFAULT CURRENT_TIMESTAMP,
    update_time TIMESTAMP NOT NULL DEFAULT CURRENT_TIMESTAMP,
    CONSTRAINT uk_data_sync_execution_table UNIQUE (execution_id, table_key)
);
CREATE INDEX IF NOT EXISTS idx_data_sync_table_report_execution
    ON t_yak_ops_data_sync_table_report(execution_id);
