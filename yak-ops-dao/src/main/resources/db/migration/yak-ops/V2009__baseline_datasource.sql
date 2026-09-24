-- Yak Ops Datasource first-release baseline.
-- Current scope contains datasource management metadata only.

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
