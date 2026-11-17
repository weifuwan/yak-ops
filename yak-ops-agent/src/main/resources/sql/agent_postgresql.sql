-- Yak Ops agent copilot schema
-- Additive and repeatable. All rows are isolated by owner user and project_code.

CREATE TABLE IF NOT EXISTS t_yak_ops_agent_session
(
    id          varchar(32)  not null,
    user_id     int          not null,
    title       varchar(200) default null,
    status      varchar(20)  not null default 'ACTIVE',
    create_time timestamp    default null,
    create_by   varchar(100) default null,
    update_time timestamp    default null,
    update_by   varchar(100) default null,

    PRIMARY KEY (id)
);

CREATE INDEX IF NOT EXISTS idx_yak_agent_session_user_time ON t_yak_ops_agent_session (user_id, create_time);

CREATE TABLE IF NOT EXISTS t_yak_ops_agent_turn
(
    id                varchar(32)   not null,
    session_id        varchar(32)   not null,
    user_id           int           not null,
    project_code      bigint        default null,
    status            varchar(20)   not null,
    context_json      text,
    llm_latency_ms    int           default null,
    prompt_tokens     int           default null,
    completion_tokens int           default null,
    error_code        varchar(100)  default null,
    error_message     varchar(1000) default null,
    create_time       timestamp     default null,
    create_by         varchar(100)  default null,
    update_time       timestamp     default null,
    update_by         varchar(100)  default null,

    PRIMARY KEY (id)
);

CREATE INDEX IF NOT EXISTS idx_yak_agent_turn_session ON t_yak_ops_agent_turn (session_id);
CREATE INDEX IF NOT EXISTS idx_yak_agent_turn_status ON t_yak_ops_agent_turn (status);

CREATE TABLE IF NOT EXISTS t_yak_ops_agent_message
(
    id           varchar(32) not null,
    session_id   varchar(32) not null,
    turn_id      varchar(32) default null,
    role         varchar(20) not null,
    content      text,
    context_json text,
    create_time  timestamp   default null,
    create_by    varchar(100) default null,
    update_time  timestamp   default null,
    update_by    varchar(100) default null,

    PRIMARY KEY (id)
);

CREATE INDEX IF NOT EXISTS idx_yak_agent_message_session_time ON t_yak_ops_agent_message (session_id, create_time);
CREATE INDEX IF NOT EXISTS idx_yak_agent_message_turn ON t_yak_ops_agent_message (turn_id);

CREATE TABLE IF NOT EXISTS t_yak_ops_agent_tool_call
(
    id             varchar(32)  not null,
    session_id     varchar(32)  default null,
    turn_id        varchar(32)  default null,
    message_id     varchar(32)  default null,
    tool_name      varchar(100) not null,
    arguments_json text,
    result_summary text,
    duration_ms    int          default null,
    status         varchar(20)  not null,
    create_time    timestamp    default null,
    create_by      varchar(100) default null,
    update_time    timestamp    default null,
    update_by      varchar(100) default null,

    PRIMARY KEY (id)
);

CREATE INDEX IF NOT EXISTS idx_yak_agent_tool_call_turn ON t_yak_ops_agent_tool_call (turn_id);
