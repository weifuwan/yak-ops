-- One-time migration for an existing database using the legacy single-row-per-task-instance schema.
-- Fresh installations already have the current schema in sql/yak-ops_h2.sql.
-- Back up the database before execution. Run this migration only once.

ALTER TABLE t_yak_ops_data_sync_recovery_point
    DROP CONSTRAINT IF EXISTS t_yak_ops_data_sync_recovery_point_task_instance_id_key;

ALTER TABLE t_yak_ops_data_sync_recovery_point
    ADD COLUMN recovery_point_id VARCHAR(128),
    ADD COLUMN checkpoint_id VARCHAR(64),
    ADD COLUMN completed_at TIMESTAMP,
    ADD COLUMN used_by_instance_id INTEGER;

UPDATE t_yak_ops_data_sync_recovery_point
SET recovery_point_id = CONCAT('legacy:', CAST(id AS VARCHAR)),
    completed_at = created_at
WHERE recovery_point_id IS NULL;

ALTER TABLE t_yak_ops_data_sync_recovery_point
    ALTER COLUMN recovery_point_id SET NOT NULL,
    ALTER COLUMN restore_path DROP NOT NULL;

CREATE UNIQUE INDEX IF NOT EXISTS uk_data_sync_recovery_point_id
    ON t_yak_ops_data_sync_recovery_point(recovery_point_id);
CREATE INDEX IF NOT EXISTS idx_data_sync_recovery_task_instance
    ON t_yak_ops_data_sync_recovery_point(task_instance_id, state, completed_at);
CREATE INDEX IF NOT EXISTS idx_data_sync_recovery_used_instance
    ON t_yak_ops_data_sync_recovery_point(used_by_instance_id);
