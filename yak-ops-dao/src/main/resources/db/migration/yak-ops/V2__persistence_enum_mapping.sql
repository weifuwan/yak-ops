-- Persist closed status/environment enums as compact numeric values.
-- Existing canonical string values are converted before the column type changes.

UPDATE yak_ops_data_source
SET environment = CASE environment
    WHEN 'DEVELOP' THEN '0'
    WHEN 'TEST' THEN '1'
    WHEN 'PROD' THEN '2'
    ELSE environment
END;

UPDATE yak_ops_data_source
SET conn_status = CASE conn_status
    WHEN 'UNKNOWN' THEN '0'
    WHEN 'CONNECTED' THEN '1'
    WHEN 'DISCONNECTED' THEN '2'
    ELSE conn_status
END;

ALTER TABLE yak_ops_data_source
    MODIFY COLUMN environment TINYINT UNSIGNED NOT NULL DEFAULT 0 COMMENT '运行环境：0 开发，1 测试，2 生产',
    MODIFY COLUMN conn_status TINYINT UNSIGNED NOT NULL DEFAULT 0 COMMENT '连接状态：0 未测试，1 连接可用，2 连接不可用';

ALTER TABLE yak_security_user
    MODIFY COLUMN status TINYINT UNSIGNED NOT NULL DEFAULT 1 COMMENT '用户状态：1 正常，2 禁用';
