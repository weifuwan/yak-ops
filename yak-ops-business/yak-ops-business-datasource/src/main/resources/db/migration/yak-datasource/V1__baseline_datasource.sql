-- Yak Ops Datasource first-release baseline.
-- This file represents the complete schema owned by the Datasource module.

CREATE TABLE IF NOT EXISTS yak_ops_data_source (
    id BIGINT UNSIGNED NOT NULL AUTO_INCREMENT COMMENT '主键',
    name VARCHAR(128) NOT NULL COMMENT '数据源名称',
    db_type VARCHAR(32) NOT NULL COMMENT '数据库类型',
    jdbc_url VARCHAR(1024) NOT NULL COMMENT 'JDBC 地址',
    environment VARCHAR(32) NOT NULL DEFAULT 'DEVELOP' COMMENT '运行环境',
    conn_status VARCHAR(32) NOT NULL DEFAULT 'UNKNOWN' COMMENT '连通状态',
    remark VARCHAR(500) DEFAULT NULL COMMENT '备注',
    connection_params LONGTEXT NOT NULL COMMENT '规范化连接参数 JSON',
    original_json LONGTEXT NOT NULL COMMENT '前端编辑回显参数 JSON',
    create_time DATETIME(3) NOT NULL DEFAULT CURRENT_TIMESTAMP(3) COMMENT '创建时间',
    update_time DATETIME(3) NOT NULL DEFAULT CURRENT_TIMESTAMP(3)
        ON UPDATE CURRENT_TIMESTAMP(3) COMMENT '更新时间',
    PRIMARY KEY (id),
    UNIQUE KEY uk_yak_ops_data_source_name (name),
    KEY idx_yak_ops_data_source_type (db_type),
    KEY idx_yak_ops_data_source_environment (environment),
    KEY idx_yak_ops_data_source_status (conn_status),
    KEY idx_yak_ops_data_source_update_time (update_time)
) ENGINE=InnoDB
  DEFAULT CHARSET=utf8mb4
  COLLATE=utf8mb4_unicode_ci
  COMMENT='Yak Ops 数据源管理';

CREATE TABLE IF NOT EXISTS yak_ops_sql_execution (
    id BIGINT UNSIGNED NOT NULL AUTO_INCREMENT COMMENT '主键',
    execution_id VARCHAR(96) NOT NULL COMMENT 'SQL Runtime execution id',
    data_source_id VARCHAR(128) NOT NULL COMMENT '数据源引用',
    caller VARCHAR(32) NOT NULL COMMENT '调用方类型',
    caller_reference VARCHAR(255) DEFAULT NULL COMMENT '调用方业务引用',
    operator_name VARCHAR(128) DEFAULT NULL COMMENT '操作人标识，可为空',
    transaction_mode VARCHAR(32) NOT NULL COMMENT '事务模式',
    status VARCHAR(32) NOT NULL COMMENT '执行状态',
    statement_count INT UNSIGNED NOT NULL DEFAULT 0 COMMENT 'Statement 数量',
    succeeded_statement_count INT UNSIGNED NOT NULL DEFAULT 0 COMMENT '成功 Statement 数量',
    returned_rows BIGINT UNSIGNED NOT NULL DEFAULT 0 COMMENT '累计返回行数',
    affected_rows BIGINT NOT NULL DEFAULT 0 COMMENT '累计影响行数',
    started_at DATETIME(3) NOT NULL COMMENT '开始时间',
    finished_at DATETIME(3) NOT NULL COMMENT '结束时间',
    duration_ms BIGINT UNSIGNED NOT NULL DEFAULT 0 COMMENT '执行耗时毫秒',
    error_message VARCHAR(1000) DEFAULT NULL COMMENT '终态错误摘要',
    create_time DATETIME(3) NOT NULL DEFAULT CURRENT_TIMESTAMP(3) COMMENT '记录创建时间',
    PRIMARY KEY (id),
    UNIQUE KEY uk_yak_ops_sql_execution_execution_id (execution_id),
    KEY idx_yak_ops_sql_execution_started_at (started_at),
    KEY idx_yak_ops_sql_execution_status_started (status, started_at),
    KEY idx_yak_ops_sql_execution_caller_started (caller, started_at),
    KEY idx_yak_ops_sql_execution_datasource_started (data_source_id, started_at),
    KEY idx_yak_ops_sql_execution_duration (duration_ms)
) ENGINE=InnoDB
  DEFAULT CHARSET=utf8mb4
  COLLATE=utf8mb4_unicode_ci
  COMMENT='Yak Ops SQL Execution 执行审计';

CREATE TABLE IF NOT EXISTS yak_ops_sql_statement_execution (
    id BIGINT UNSIGNED NOT NULL AUTO_INCREMENT COMMENT '主键',
    execution_id VARCHAR(96) NOT NULL COMMENT '所属 SQL execution id',
    statement_id VARCHAR(128) NOT NULL COMMENT 'SQL Runtime statement id',
    statement_index INT UNSIGNED NOT NULL COMMENT 'Statement 顺序，从 0 开始',
    statement_type VARCHAR(32) NOT NULL COMMENT 'SQL 语义类型',
    sql_fingerprint CHAR(64) NOT NULL COMMENT '字面量脱敏后的 SHA-256 指纹',
    sql_preview VARCHAR(2048) NOT NULL COMMENT '字面量脱敏后的 SQL 预览',
    status VARCHAR(32) NOT NULL COMMENT 'Statement 状态',
    result_type VARCHAR(32) DEFAULT NULL COMMENT 'RESULT_SET / UPDATE_COUNT',
    returned_rows BIGINT UNSIGNED NOT NULL DEFAULT 0 COMMENT '返回行数',
    affected_rows BIGINT NOT NULL DEFAULT 0 COMMENT '影响行数',
    truncated TINYINT(1) NOT NULL DEFAULT 0 COMMENT '结果是否被 maxRows 截断',
    started_at DATETIME(3) DEFAULT NULL COMMENT '开始时间；SKIPPED 可为空',
    finished_at DATETIME(3) NOT NULL COMMENT '结束时间',
    duration_ms BIGINT UNSIGNED NOT NULL DEFAULT 0 COMMENT 'Statement 耗时毫秒',
    error_message VARCHAR(1000) DEFAULT NULL COMMENT 'Statement 错误摘要',
    create_time DATETIME(3) NOT NULL DEFAULT CURRENT_TIMESTAMP(3) COMMENT '记录创建时间',
    PRIMARY KEY (id),
    UNIQUE KEY uk_yak_ops_sql_statement_statement_id (statement_id),
    KEY idx_yak_ops_sql_statement_execution (execution_id, statement_index),
    KEY idx_yak_ops_sql_statement_fingerprint (sql_fingerprint),
    KEY idx_yak_ops_sql_statement_type_status (statement_type, status),
    KEY idx_yak_ops_sql_statement_duration (duration_ms)
) ENGINE=InnoDB
  DEFAULT CHARSET=utf8mb4
  COLLATE=utf8mb4_unicode_ci
  COMMENT='Yak Ops SQL Statement 执行审计';
