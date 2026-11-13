-- Yak Ops data modeling schema (warehouse planning domain)
-- Additive and repeatable. All rows are isolated by the authenticated project_code.

CREATE TABLE IF NOT EXISTS t_cockpit_business_type
(
    id            varchar(32) not null,
    project_code bigint NOT NULL,
    en_short_name varchar(100) default null,
    business_name varchar(255) default null,
    reviewer      varchar(500) default null,
    director      varchar(100) default null,
    remark        text,
    parent_id     varchar(50)  default null,
    create_time   timestamp     default null,
    create_by     varchar(100) default null,
    update_time   timestamp     default null,
    update_by     varchar(100) default null,

    PRIMARY KEY (id)
);

CREATE TABLE IF NOT EXISTS t_cockpit_dw_layer
(
    id            varchar(32) not null,
    project_code bigint NOT NULL,
    en_short_name varchar(100) default null,
    en_name       varchar(255) default null,
    cn_name       varchar(255) default null,
    dw_type       varchar(20)  default null,
    dw_model_type varchar(20)  default null,
    director      varchar(100) default null,
    remark        text         default null,
    create_time   timestamp     default null,
    create_by     varchar(100) default null,
    update_time   timestamp     default null,
    update_by     varchar(100) default null,

    primary key (id)
);

CREATE TABLE IF NOT EXISTS t_cockpit_data_domain
(
    id            varchar(32) not null,
    project_code bigint NOT NULL,
    en_short_name varchar(100) default null,
    en_name       varchar(255) default null,
    cn_name       varchar(255) default null,
    reviewer      varchar(500) default null,
    director      varchar(100) default null,
    remark        text         default null,
    flag          int          default 0,
    create_time   timestamp     default null,
    create_by     varchar(100) default null,
    update_time   timestamp     default null,
    update_by     varchar(100) default null,

    primary key (id)
);

CREATE TABLE IF NOT EXISTS t_cockpit_business_process
(
    id             varchar(32) not null,
    project_code bigint NOT NULL,
    en_short_name  varchar(100) default null,
    en_name        varchar(255) default null,
    cn_name        varchar(255) default null,
    data_domain_id varchar(32)  default null,
    director       varchar(100) default null,
    remark         text         default null,
    flag           int          default 0,
    create_time    timestamp     default null,
    create_by      varchar(100) default null,
    update_time    timestamp     default null,
    update_by      varchar(100) default null,

    primary key (id)
);

CREATE TABLE IF NOT EXISTS t_cockpit_data_mart
(
    id               varchar(32) not null,
    project_code bigint NOT NULL,
    en_short_name    varchar(100) default null,
    mart_name        varchar(255) default null,
    mart_type        varchar(50)  default null,
    business_type_id varchar(32)  default null,
    director         varchar(100) default null,
    remark           text,
    parent_id        varchar(50)  default null,
    create_time      timestamp     default null,
    create_by        varchar(100) default null,
    update_time      timestamp     default null,
    update_by        varchar(100) default null,

    PRIMARY KEY (id)
);

CREATE TABLE IF NOT EXISTS t_cockpit_subject_domain
(
    id            varchar(32) not null,
    project_code bigint NOT NULL,
    en_short_name varchar(100) default null,
    domain_name   varchar(255) default null,
    data_mart_id  varchar(32)  default null,
    director      varchar(100) default null,
    remark        text,
    parent_id     varchar(50)  default null,
    create_time   timestamp     default null,
    create_by     varchar(100) default null,
    update_time   timestamp     default null,
    update_by     varchar(100) default null,

    PRIMARY KEY (id)
);

CREATE TABLE IF NOT EXISTS t_cockpit_business_type_related_data_domain
(
    id          varchar(32) NOT NULL,
    project_code bigint NOT NULL,
    bus_type_id varchar(32) NOT NULL,
    data_id     varchar(32) NOT NULL,
    create_by   varchar(32) DEFAULT NULL,
    create_time timestamp    DEFAULT NULL,
    update_by   varchar(32) DEFAULT NULL,
    update_time timestamp    DEFAULT NULL,

    PRIMARY KEY (id)
);

CREATE INDEX IF NOT EXISTS idx_t_cockpit_business_type_project_code ON t_cockpit_business_type (project_code);
CREATE INDEX IF NOT EXISTS idx_t_cockpit_dw_layer_project_code ON t_cockpit_dw_layer (project_code);
CREATE INDEX IF NOT EXISTS idx_t_cockpit_data_domain_project_code ON t_cockpit_data_domain (project_code);
CREATE INDEX IF NOT EXISTS idx_t_cockpit_business_process_project_code ON t_cockpit_business_process (project_code);
CREATE INDEX IF NOT EXISTS idx_t_cockpit_data_mart_project_code ON t_cockpit_data_mart (project_code);
CREATE INDEX IF NOT EXISTS idx_t_cockpit_subject_domain_project_code ON t_cockpit_subject_domain (project_code);
CREATE INDEX IF NOT EXISTS idx_t_cockpit_business_type_related_data_domain_project_code ON t_cockpit_business_type_related_data_domain (project_code);
