-- Add the model type to existing Doris publish-history tables.
-- Fresh installations already create this column in modeling_mysql.sql.
SET @yak_ops_publish_table_exists := (
    SELECT COUNT(*)
    FROM information_schema.tables
    WHERE table_schema = DATABASE()
      AND table_name = 't_yak_ops_dimension_table_publish'
);
SET @yak_ops_table_model_exists := (
    SELECT COUNT(*)
    FROM information_schema.columns
    WHERE table_schema = DATABASE()
      AND table_name = 't_yak_ops_dimension_table_publish'
      AND column_name = 'table_model'
);
SET @yak_ops_publish_patch_sql := IF(
    @yak_ops_publish_table_exists > 0 AND @yak_ops_table_model_exists = 0,
    'ALTER TABLE `t_yak_ops_dimension_table_publish` ADD COLUMN `table_model` VARCHAR(32) NULL AFTER `datasource_name`',
    'SELECT 1'
);
PREPARE yak_ops_publish_patch_stmt FROM @yak_ops_publish_patch_sql;
EXECUTE yak_ops_publish_patch_stmt;
DEALLOCATE PREPARE yak_ops_publish_patch_stmt;
