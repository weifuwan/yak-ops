-- Yak Ops agent copilot schema
-- Additive and repeatable. All rows are isolated by owner user and project_code.

CREATE TABLE IF NOT EXISTS `t_yak_ops_agent_session`
(
    `id`          varchar(32)  not null comment '主键',
    `user_id`     int          not null comment '会话归属用户',
    `title`       varchar(200) default null comment '会话标题',
    `status`      varchar(20)  not null default 'ACTIVE' comment '会话状态：ACTIVE、ARCHIVED',
    `create_time` datetime     default null comment '创建时间',
    `create_by`   varchar(100) default null comment '创建人',
    `update_time` datetime     default null comment '更新时间',
    `update_by`   varchar(100) default null comment '更新人',
    KEY `idx_yak_agent_session_user_time` (`user_id`, `create_time`),
    PRIMARY KEY (`id`)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COMMENT='智能助手-会话';

CREATE TABLE IF NOT EXISTS `t_yak_ops_agent_turn`
(
    `id`                varchar(32)  not null comment '主键',
    `session_id`        varchar(32)  not null comment '会话 ID',
    `user_id`           int          not null comment '归属用户',
    `project_code`      bigint       default null comment '项目编码快照',
    `status`            varchar(20)  not null comment '轮次状态：QUEUED、RUNNING、COMPLETED、FAILED、CANCELLED',
    `context_json`      text comment '上下文快照（项目/页面对象）',
    `llm_latency_ms`    int          default null comment 'LLM 调用总耗时毫秒',
    `prompt_tokens`     int          default null comment '输入 token 数',
    `completion_tokens` int          default null comment '输出 token 数',
    `error_code`        varchar(100) default null comment '失败错误码',
    `error_message`     varchar(1000) default null comment '失败信息',
    `create_time`       datetime     default null comment '创建时间',
    `create_by`         varchar(100) default null comment '创建人',
    `update_time`       datetime     default null comment '更新时间',
    `update_by`         varchar(100) default null comment '更新人',
    KEY `idx_yak_agent_turn_session` (`session_id`),
    KEY `idx_yak_agent_turn_status` (`status`),
    PRIMARY KEY (`id`)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COMMENT='智能助手-对话轮次';

CREATE TABLE IF NOT EXISTS `t_yak_ops_agent_message`
(
    `id`           varchar(32) not null comment '主键',
    `session_id`   varchar(32) not null comment '会话 ID',
    `turn_id`      varchar(32) default null comment '轮次 ID',
    `role`         varchar(20) not null comment '角色：user、assistant、tool',
    `content`      mediumtext comment '消息内容',
    `context_json` text comment '消息上下文快照',
    `create_time`  datetime    default null comment '创建时间',
    `create_by`    varchar(100) default null comment '创建人',
    `update_time`  datetime    default null comment '更新时间',
    `update_by`    varchar(100) default null comment '更新人',
    KEY `idx_yak_agent_message_session_time` (`session_id`, `create_time`),
    KEY `idx_yak_agent_message_turn` (`turn_id`),
    PRIMARY KEY (`id`)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COMMENT='智能助手-消息';

CREATE TABLE IF NOT EXISTS `t_yak_ops_agent_tool_call`
(
    `id`             varchar(32)  not null comment '主键',
    `session_id`     varchar(32)  default null comment '会话 ID',
    `turn_id`        varchar(32)  default null comment '轮次 ID',
    `message_id`     varchar(32)  default null comment '关联消息 ID',
    `tool_name`      varchar(100) not null comment '工具名',
    `arguments_json` text comment '入参快照',
    `result_summary` text comment '返回摘要',
    `duration_ms`    int          default null comment '执行耗时毫秒',
    `status`         varchar(20)  not null comment '状态：SUCCESS、FAILED、REJECTED',
    `create_time`    datetime     default null comment '创建时间',
    `create_by`      varchar(100) default null comment '创建人',
    `update_time`    datetime     default null comment '更新时间',
    `update_by`      varchar(100) default null comment '更新人',
    KEY `idx_yak_agent_tool_call_turn` (`turn_id`),
    PRIMARY KEY (`id`)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COMMENT='智能助手-工具调用留痕';
