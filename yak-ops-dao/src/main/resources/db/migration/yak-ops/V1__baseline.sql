-- Yak Ops first stable schema baseline.
-- Current product scope only contains user/login persistence and datasource management persistence.
-- This baseline is for rebuildable early-stage databases. Once released to a shared environment it becomes immutable.

CREATE TABLE yak_security_user (
    id VARCHAR(64) NOT NULL COMMENT '主键ID，由应用雪花算法生成',
    app_name VARCHAR(64) NOT NULL COMMENT '应用级数据隔离键',
    user_name VARCHAR(64) NOT NULL COMMENT '用户账号',
    pw VARCHAR(2048) NOT NULL COMMENT '单向哈希密码',
    salt VARCHAR(64) NOT NULL DEFAULT '' COMMENT '密码盐兼容字段',
    real_name VARCHAR(128) NULL COMMENT '真实姓名',
    phone VARCHAR(32) NULL COMMENT '手机号码',
    email VARCHAR(128) NULL COMMENT '电子邮箱',
    dept_id BIGINT NULL COMMENT '部门ID，仅作为用户资料字段保留，不创建部门表外键',
    status INT NOT NULL DEFAULT 1 COMMENT '用户状态：1 正常，2 禁用',
    is_delete TINYINT(1) NOT NULL DEFAULT 0 COMMENT '逻辑删除：0 未删除，1 已删除',
    create_time DATETIME(3) NOT NULL COMMENT '创建时间',
    update_time DATETIME(3) NOT NULL COMMENT '更新时间',
    create_by VARCHAR(64) NOT NULL COMMENT '创建人标识',
    update_by VARCHAR(64) NOT NULL COMMENT '更新人标识',
    active_user_name VARCHAR(64)
        GENERATED ALWAYS AS (IF(is_delete = 0, user_name, NULL)) STORED
        COMMENT '未删除用户唯一键',
    PRIMARY KEY (id),
    UNIQUE KEY uk_security_user_app_user_name (app_name, active_user_name),
    KEY idx_security_user_app_email (app_name, email, is_delete),
    KEY idx_security_user_app_phone (app_name, phone, is_delete),
    KEY idx_security_user_app_create_time (app_name, create_time, is_delete)
) ENGINE=InnoDB
  DEFAULT CHARACTER SET=utf8mb4
  COLLATE=utf8mb4_unicode_ci
  COMMENT='用户账号表';

CREATE TABLE yak_ops_data_source (
    id VARCHAR(64) NOT NULL COMMENT '主键ID，由应用雪花算法生成',
    name VARCHAR(128) NOT NULL COMMENT '数据源名称，在当前产品范围内唯一',
    db_type VARCHAR(32) NOT NULL COMMENT '数据库类型，对应已注册的数据源插件类型',
    jdbc_url VARCHAR(1024) NOT NULL COMMENT 'JDBC 连接地址',
    environment VARCHAR(32) NOT NULL DEFAULT 'DEVELOP' COMMENT '运行环境：DEVELOP、TEST、PROD',
    conn_status VARCHAR(32) NOT NULL DEFAULT 'UNKNOWN' COMMENT '连接状态：UNKNOWN、CONNECTED、DISCONNECTED',
    remark VARCHAR(500) NULL COMMENT '数据源备注',
    connection_params LONGTEXT NOT NULL COMMENT '规范化连接参数 JSON，包含敏感连接信息',
    original_json LONGTEXT NOT NULL COMMENT '前端编辑回显参数 JSON，可能包含敏感连接信息',
    create_time DATETIME(3) NOT NULL COMMENT '创建时间',
    update_time DATETIME(3) NOT NULL COMMENT '更新时间',
    create_by VARCHAR(64) NOT NULL COMMENT '创建人标识',
    update_by VARCHAR(64) NOT NULL COMMENT '更新人标识',
    PRIMARY KEY (id),
    UNIQUE KEY uk_ops_data_source_name (name),
    KEY idx_ops_data_source_type (db_type),
    KEY idx_ops_data_source_environment (environment),
    KEY idx_ops_data_source_status (conn_status),
    KEY idx_ops_data_source_update_time (update_time)
) ENGINE=InnoDB
  DEFAULT CHARACTER SET=utf8mb4
  COLLATE=utf8mb4_unicode_ci
  COMMENT='数据源管理表';
