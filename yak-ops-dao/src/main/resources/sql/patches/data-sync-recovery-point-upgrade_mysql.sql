-- One-time migration for an existing database using the legacy single-row-per-task-instance schema.
-- Fresh installations already have the current schema in sql/yak-ops_mysql.sql.
-- Back up the database before execution. Run this migration only once.

ALTER TABLE `t_yak_ops_data_sync_recovery_point`
    DROP INDEX `uk_data_sync_recovery_task_instance`,
    ADD COLUMN `recovery_point_id` VARCHAR(128) NULL AFTER `job_id`,
    ADD COLUMN `checkpoint_id` VARCHAR(64) DEFAULT NULL AFTER `recovery_point_id`,
    ADD COLUMN `completed_at` DATETIME DEFAULT NULL AFTER `created_at`,
    ADD COLUMN `used_by_instance_id` INT DEFAULT NULL AFTER `used_by_workflow_instance_id`;

UPDATE `t_yak_ops_data_sync_recovery_point`
SET `recovery_point_id` = CONCAT('legacy:', `id`),
    `completed_at` = `created_at`
WHERE `recovery_point_id` IS NULL;

ALTER TABLE `t_yak_ops_data_sync_recovery_point`
    MODIFY COLUMN `recovery_point_id` VARCHAR(128) NOT NULL,
    MODIFY COLUMN `restore_path` VARCHAR(2048) DEFAULT NULL,
    ADD UNIQUE KEY `uk_data_sync_recovery_point_id` (`recovery_point_id`),
    ADD KEY `idx_data_sync_recovery_task_instance` (`task_instance_id`, `state`, `completed_at`),
    ADD KEY `idx_data_sync_recovery_used_instance` (`used_by_instance_id`);
