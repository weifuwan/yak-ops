ALTER TABLE yak_ops_task_instance
  ADD COLUMN execution_report MEDIUMTEXT NULL COMMENT 'Plugin public execution report fixed with terminal outcome; null when unobserved';
