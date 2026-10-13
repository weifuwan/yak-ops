ALTER TABLE yak_ops_task_definition
    ADD COLUMN debug TINYINT(1) NOT NULL DEFAULT 0 COMMENT 'Detailed diagnostics for future Task Instances';

ALTER TABLE yak_ops_task_instance
    ADD COLUMN debug TINYINT(1) NOT NULL DEFAULT 0 COMMENT 'Detailed diagnostics captured at admission';
