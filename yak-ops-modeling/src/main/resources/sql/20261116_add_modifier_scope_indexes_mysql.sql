-- One-time additive indexes for existing MySQL installations.
-- The statements are idempotent and preserve all modifier rows.

SET @yak_modifier_index_sql = IF(
  (SELECT COUNT(*) FROM information_schema.statistics
   WHERE table_schema = DATABASE()
     AND table_name = 't_yak_ops_indicator_modifier'
     AND index_name = 'idx_yak_indicator_modifier_5') = 0,
  'CREATE INDEX idx_yak_indicator_modifier_5 ON t_yak_ops_indicator_modifier (project_code, dwh_id)',
  'SELECT 1'
);
PREPARE yak_modifier_index_stmt FROM @yak_modifier_index_sql;
EXECUTE yak_modifier_index_stmt;
DEALLOCATE PREPARE yak_modifier_index_stmt;

SET @yak_modifier_index_sql = IF(
  (SELECT COUNT(*) FROM information_schema.statistics
   WHERE table_schema = DATABASE()
     AND table_name = 't_yak_ops_indicator_modifier'
     AND index_name = 'idx_yak_indicator_modifier_6') = 0,
  'CREATE INDEX idx_yak_indicator_modifier_6 ON t_yak_ops_indicator_modifier (project_code, bus_type_id)',
  'SELECT 1'
);
PREPARE yak_modifier_index_stmt FROM @yak_modifier_index_sql;
EXECUTE yak_modifier_index_stmt;
DEALLOCATE PREPARE yak_modifier_index_stmt;

SET @yak_modifier_index_sql = IF(
  (SELECT COUNT(*) FROM information_schema.statistics
   WHERE table_schema = DATABASE()
     AND table_name = 't_yak_ops_indicator_modifier'
     AND index_name = 'idx_yak_indicator_modifier_7') = 0,
  'CREATE INDEX idx_yak_indicator_modifier_7 ON t_yak_ops_indicator_modifier (project_code, domain_id)',
  'SELECT 1'
);
PREPARE yak_modifier_index_stmt FROM @yak_modifier_index_sql;
EXECUTE yak_modifier_index_stmt;
DEALLOCATE PREPARE yak_modifier_index_stmt;

SET @yak_modifier_index_sql = IF(
  (SELECT COUNT(*) FROM information_schema.statistics
   WHERE table_schema = DATABASE()
     AND table_name = 't_yak_ops_indicator_modifier'
     AND index_name = 'idx_yak_indicator_modifier_8') = 0,
  'CREATE INDEX idx_yak_indicator_modifier_8 ON t_yak_ops_indicator_modifier (project_code, dimension_tb_id, dimension_fd_id)',
  'SELECT 1'
);
PREPARE yak_modifier_index_stmt FROM @yak_modifier_index_sql;
EXECUTE yak_modifier_index_stmt;
DEALLOCATE PREPARE yak_modifier_index_stmt;
