-- Expand yak_security_message into a general user notification inbox.
-- Existing rows remain valid and are treated as system/info notifications.

ALTER TABLE yak_security_message
  ADD COLUMN summary VARCHAR(255) NULL COMMENT '列表摘要' AFTER title,
  ADD COLUMN message_type VARCHAR(32) NOT NULL DEFAULT 'SYSTEM'
    COMMENT '消息类型：TASK/QUALITY/SECURITY/SYSTEM 等' AFTER content,
  ADD COLUMN message_level VARCHAR(16) NOT NULL DEFAULT 'INFO'
    COMMENT '消息级别：INFO/SUCCESS/WARNING/ERROR' AFTER message_type,
  ADD COLUMN message_scope VARCHAR(16) NOT NULL DEFAULT 'SYSTEM'
    COMMENT '消息范围：SYSTEM/PROJECT' AFTER message_level,
  ADD COLUMN project_id BIGINT NULL COMMENT '项目主键，系统消息为空' AFTER message_scope,
  ADD COLUMN source_type VARCHAR(64) NULL COMMENT '业务来源类型' AFTER project_id,
  ADD COLUMN source_id VARCHAR(128) NULL COMMENT '业务来源标识' AFTER source_type,
  ADD COLUMN action_path VARCHAR(512) NULL COMMENT '前端详情跳转路径' AFTER source_id,
  ADD COLUMN read_time TIMESTAMP NULL COMMENT '已读时间' AFTER read_tag,
  ADD KEY idx_message_app_user_read(app_name,user_id,read_tag,create_time),
  ADD KEY idx_message_app_user_project(app_name,user_id,project_id,create_time),
  ADD KEY idx_message_app_source(app_name,source_type,source_id);
