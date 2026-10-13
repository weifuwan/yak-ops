ALTER TABLE yak_ops_task_instance
    ADD COLUMN log_path VARCHAR(512) NULL COMMENT 'Relative task execution log path';
