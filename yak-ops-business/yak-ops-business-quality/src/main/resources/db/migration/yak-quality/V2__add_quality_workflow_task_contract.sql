CREATE TABLE IF NOT EXISTS yak_quality_monitor_revision (
    id BIGINT NOT NULL AUTO_INCREMENT COMMENT 'Workflow 可固定的质量监控 revision ID',
    project_id BIGINT NOT NULL COMMENT 'Yak Security Project ID',
    monitor_id BIGINT NOT NULL COMMENT '来源质量监控 ID',
    revision_no INT NOT NULL COMMENT '监控任务 revision 号',
    monitor_name VARCHAR(100) NOT NULL COMMENT '监控名称快照',
    definition_json MEDIUMTEXT NOT NULL COMMENT '不可变质量执行定义 JSON',
    checksum VARCHAR(64) NOT NULL COMMENT '执行定义 SHA-256',
    created_at DATETIME(3) NOT NULL DEFAULT CURRENT_TIMESTAMP(3),
    PRIMARY KEY (id),
    UNIQUE KEY uk_yak_quality_monitor_revision_no
      (project_id, monitor_id, revision_no),
    KEY idx_yak_quality_monitor_revision_latest
      (project_id, monitor_id, revision_no),
    KEY idx_yak_quality_monitor_revision_checksum
      (project_id, monitor_id, checksum)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_unicode_ci
  COMMENT='数据质量 Workflow 不可变监控版本';

ALTER TABLE yak_quality_execution
    ADD COLUMN idempotency_key VARCHAR(255) NULL
      COMMENT '外部编排提交幂等键' AFTER execution_no,
    ADD UNIQUE KEY uk_yak_quality_execution_idempotency
      (project_id, idempotency_key);
