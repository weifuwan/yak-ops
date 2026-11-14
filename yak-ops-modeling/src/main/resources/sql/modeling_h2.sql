-- Yak Ops data modeling schema (warehouse planning domain)
-- Additive and repeatable. All rows are isolated by the authenticated project_code.

CREATE TABLE IF NOT EXISTS t_yak_ops_business_type
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

CREATE TABLE IF NOT EXISTS t_yak_ops_dw_layer
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

-- Normalize legacy layer and model enum values before enum persistence uses the new codes.
UPDATE t_yak_ops_dw_layer SET dw_type = 'SOURCE_LAYER' WHERE LOWER(dw_type) = 'dim';
UPDATE t_yak_ops_dw_layer SET dw_type = 'PUBLIC_LAYER' WHERE LOWER(dw_type) = 'dwd';
UPDATE t_yak_ops_dw_layer SET dw_type = 'APPLICATION_LAYER' WHERE LOWER(dw_type) = 'ads';
UPDATE t_yak_ops_dw_layer SET dw_type = 'NONE_LAYER' WHERE LOWER(dw_type) = 'other';
UPDATE t_yak_ops_dw_layer SET dw_model_type = 'DIM' WHERE LOWER(dw_model_type) = 'dim(';

-- Seed the conventional warehouse layers for projects that have not initialized ODS.
-- ODS is inserted last and acts as the initialization marker for subsequent startup runs.
INSERT INTO t_yak_ops_dw_layer (
    id, project_code, en_short_name, en_name, cn_name, dw_type, dw_model_type,
    director, remark, create_time, create_by, update_time, update_by
)
SELECT CONCAT('DW', p.code, 'DIM'), p.code, 'DIM', 'Dimension', '维度层',
       'PUBLIC_LAYER', 'DIMENSION_DIM', 'admin',
       '统一维护企业级公共维度，建立一致性维度，供各业务过程复用。',
       CURRENT_TIMESTAMP, 'sys', CURRENT_TIMESTAMP, 'sys'
FROM t_yak_ops_project p
WHERE NOT EXISTS (
    SELECT 1 FROM t_yak_ops_dw_layer existing
    WHERE existing.project_code = p.code AND UPPER(existing.en_short_name) = 'ODS'
)
AND NOT EXISTS (
    SELECT 1 FROM t_yak_ops_dw_layer existing
    WHERE existing.project_code = p.code AND UPPER(existing.en_short_name) = 'DIM'
);

INSERT INTO t_yak_ops_dw_layer (
    id, project_code, en_short_name, en_name, cn_name, dw_type, dw_model_type,
    director, remark, create_time, create_by, update_time, update_by
)
SELECT CONCAT('DW', p.code, 'DWS'), p.code, 'DWS', 'Data Warehouse Summary', '汇总数据层',
       'PUBLIC_LAYER', 'SUMMARY', 'admin',
       '面向分析主题对明细数据进行汇总加工，形成可复用的公共汇总数据。',
       CURRENT_TIMESTAMP, 'sys', CURRENT_TIMESTAMP, 'sys'
FROM t_yak_ops_project p
WHERE NOT EXISTS (
    SELECT 1 FROM t_yak_ops_dw_layer existing
    WHERE existing.project_code = p.code AND UPPER(existing.en_short_name) = 'ODS'
)
AND NOT EXISTS (
    SELECT 1 FROM t_yak_ops_dw_layer existing
    WHERE existing.project_code = p.code AND UPPER(existing.en_short_name) = 'DWS'
);

INSERT INTO t_yak_ops_dw_layer (
    id, project_code, en_short_name, en_name, cn_name, dw_type, dw_model_type,
    director, remark, create_time, create_by, update_time, update_by
)
SELECT CONCAT('DW', p.code, 'DWD'), p.code, 'DWD', 'Data Warehouse Detail', '明细数据层',
       'PUBLIC_LAYER', 'DETAIL', 'admin',
       '基于业务过程构建明细数据，遵循一致性维度规范，保留可追溯的明细粒度。',
       CURRENT_TIMESTAMP, 'sys', CURRENT_TIMESTAMP, 'sys'
FROM t_yak_ops_project p
WHERE NOT EXISTS (
    SELECT 1 FROM t_yak_ops_dw_layer existing
    WHERE existing.project_code = p.code AND UPPER(existing.en_short_name) = 'ODS'
)
AND NOT EXISTS (
    SELECT 1 FROM t_yak_ops_dw_layer existing
    WHERE existing.project_code = p.code AND UPPER(existing.en_short_name) = 'DWD'
);

INSERT INTO t_yak_ops_dw_layer (
    id, project_code, en_short_name, en_name, cn_name, dw_type, dw_model_type,
    director, remark, create_time, create_by, update_time, update_by
)
SELECT CONCAT('DW', p.code, 'ADS'), p.code, 'ADS', 'Application Data Service', '应用数据层',
       'APPLICATION_LAYER', 'APPLICATION', 'admin',
       '面向具体业务场景和应用产品组织数据，提供应用统计指标和数据服务。',
       CURRENT_TIMESTAMP, 'sys', CURRENT_TIMESTAMP, 'sys'
FROM t_yak_ops_project p
WHERE NOT EXISTS (
    SELECT 1 FROM t_yak_ops_dw_layer existing
    WHERE existing.project_code = p.code AND UPPER(existing.en_short_name) = 'ODS'
)
AND NOT EXISTS (
    SELECT 1 FROM t_yak_ops_dw_layer existing
    WHERE existing.project_code = p.code AND UPPER(existing.en_short_name) = 'ADS'
);

INSERT INTO t_yak_ops_dw_layer (
    id, project_code, en_short_name, en_name, cn_name, dw_type, dw_model_type,
    director, remark, create_time, create_by, update_time, update_by
)
SELECT CONCAT('DW', p.code, 'ODS'), p.code, 'ODS', 'Operational Data Store', '数据引入层',
       'SOURCE_LAYER', 'SOURCE', 'admin',
       '存放来自业务系统的原始数据，尽量保持与源系统的数据结构一致，供后续清洗和加工使用。',
       CURRENT_TIMESTAMP, 'sys', CURRENT_TIMESTAMP, 'sys'
FROM t_yak_ops_project p
WHERE NOT EXISTS (
    SELECT 1 FROM t_yak_ops_dw_layer existing
    WHERE existing.project_code = p.code AND UPPER(existing.en_short_name) = 'ODS'
);


CREATE TABLE IF NOT EXISTS t_yak_ops_data_domain
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

CREATE TABLE IF NOT EXISTS t_yak_ops_business_process
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

CREATE TABLE IF NOT EXISTS t_yak_ops_data_mart
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

CREATE TABLE IF NOT EXISTS t_yak_ops_subject_domain
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

CREATE TABLE IF NOT EXISTS t_yak_ops_business_type_related_data_domain
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

CREATE INDEX IF NOT EXISTS idx_yak_business_type_project_code ON t_yak_ops_business_type (project_code);
CREATE INDEX IF NOT EXISTS idx_yak_dw_layer_project_code ON t_yak_ops_dw_layer (project_code);
CREATE INDEX IF NOT EXISTS idx_yak_data_domain_project_code ON t_yak_ops_data_domain (project_code);
CREATE INDEX IF NOT EXISTS idx_yak_business_process_project_code ON t_yak_ops_business_process (project_code);
CREATE INDEX IF NOT EXISTS idx_yak_data_mart_project_code ON t_yak_ops_data_mart (project_code);
CREATE INDEX IF NOT EXISTS idx_yak_subject_domain_project_code ON t_yak_ops_subject_domain (project_code);
CREATE INDEX IF NOT EXISTS idx_yak_business_type_related_data_domain_project_code ON t_yak_ops_business_type_related_data_domain (project_code);

-- -----------------------------------------------------------------------------
-- Data standards, indicators and existing dimension metadata
-- -----------------------------------------------------------------------------

-- Additive schema for data standards and data indicators.
-- Every row is scoped to a Yak Ops project through project_code.

CREATE TABLE IF NOT EXISTS t_yak_ops_standard_directory (
  id varchar(32) NOT NULL,
  project_code bigint NOT NULL,
  dir_name varchar(255) NOT NULL,
  dir_type varchar(50) NOT NULL,
  dir_module varchar(50) NOT NULL,
  parent_id varchar(32) NULL,
  create_time timestamp NULL,
  create_by varchar(100) NULL,
  update_time timestamp NULL,
  update_by varchar(100) NULL,
  PRIMARY KEY (id)
);
CREATE INDEX IF NOT EXISTS idx_yak_standard_directory_1 ON t_yak_ops_standard_directory (project_code, dir_module);
CREATE INDEX IF NOT EXISTS idx_yak_standard_directory_2 ON t_yak_ops_standard_directory (parent_id);
CREATE UNIQUE INDEX IF NOT EXISTS uk_yak_standard_directory_1 ON t_yak_ops_standard_directory (project_code, dir_module, dir_name);

CREATE TABLE IF NOT EXISTS t_yak_ops_field_standard (
  id varchar(32) NOT NULL,
  project_code bigint NOT NULL,
  std_code varchar(50) NULL,
  en_short_name varchar(128) NULL,
  en_name varchar(2048) NULL,
  cn_name varchar(2048) NULL,
  field_data_type varchar(50) NULL,
  field_length integer NULL,
  field_precision integer NULL,
  field_can_null boolean NULL,
  field_default_value varchar(2048) NULL,
  business_definition varchar(2048) NULL,
  parent_id varchar(32) NULL,
  parent_std varchar(255) NULL,
  reference varchar(255) NULL,
  create_time timestamp NULL,
  create_by varchar(100) NULL,
  update_time timestamp NULL,
  update_by varchar(100) NULL,
  PRIMARY KEY (id)
);
CREATE INDEX IF NOT EXISTS idx_yak_field_standard_1 ON t_yak_ops_field_standard (project_code);
CREATE INDEX IF NOT EXISTS idx_yak_field_standard_2 ON t_yak_ops_field_standard (parent_id);
CREATE INDEX IF NOT EXISTS idx_yak_field_standard_3 ON t_yak_ops_field_standard (reference);

CREATE TABLE IF NOT EXISTS t_yak_ops_code_standard (
  id varchar(32) NOT NULL,
  project_code bigint NOT NULL,
  parent_id varchar(32) NULL,
  en_short_name varchar(128) NULL,
  en_name varchar(2048) NULL,
  cn_name varchar(2048) NULL,
  remark varchar(2048) NULL,
  create_time timestamp NULL,
  create_by varchar(100) NULL,
  update_time timestamp NULL,
  update_by varchar(100) NULL,
  PRIMARY KEY (id)
);
CREATE INDEX IF NOT EXISTS idx_yak_code_standard_1 ON t_yak_ops_code_standard (project_code);
CREATE INDEX IF NOT EXISTS idx_yak_code_standard_2 ON t_yak_ops_code_standard (parent_id);

CREATE TABLE IF NOT EXISTS t_yak_ops_code_detail (
  id varchar(32) NOT NULL,
  project_code bigint NOT NULL,
  code_id varchar(128) NULL,
  en_short_name varchar(128) NULL,
  en_name varchar(2048) NULL,
  cn_name varchar(2048) NULL,
  remark varchar(2048) NULL,
  create_time timestamp NULL,
  create_by varchar(100) NULL,
  update_time timestamp NULL,
  update_by varchar(100) NULL,
  PRIMARY KEY (id)
);
CREATE INDEX IF NOT EXISTS idx_yak_code_detail_1 ON t_yak_ops_code_detail (project_code);
CREATE INDEX IF NOT EXISTS idx_yak_code_detail_2 ON t_yak_ops_code_detail (code_id);

CREATE TABLE IF NOT EXISTS t_yak_ops_unit_standard (
  id varchar(32) NOT NULL,
  project_code bigint NOT NULL,
  en_short_name varchar(128) NOT NULL,
  en_name varchar(2048) NOT NULL,
  cn_name varchar(2048) NOT NULL,
  parent_id varchar(32) NULL,
  create_time timestamp NULL,
  create_by varchar(100) NULL,
  update_time timestamp NULL,
  update_by varchar(100) NULL,
  PRIMARY KEY (id)
);
CREATE INDEX IF NOT EXISTS idx_yak_unit_standard_1 ON t_yak_ops_unit_standard (project_code);
CREATE INDEX IF NOT EXISTS idx_yak_unit_standard_2 ON t_yak_ops_unit_standard (parent_id);

CREATE TABLE IF NOT EXISTS t_yak_ops_dictionary_standard (
  id varchar(32) NOT NULL,
  project_code bigint NOT NULL,
  en_short_name varchar(128) NOT NULL,
  en_name varchar(2048) NOT NULL,
  cn_name varchar(2048) NOT NULL,
  create_time timestamp NULL,
  create_by varchar(100) NULL,
  update_time timestamp NULL,
  update_by varchar(100) NULL,
  PRIMARY KEY (id)
);
CREATE INDEX IF NOT EXISTS idx_yak_dictionary_standard_1 ON t_yak_ops_dictionary_standard (project_code);

CREATE TABLE IF NOT EXISTS t_yak_ops_indicator_atom (
  id varchar(32) NOT NULL,
  project_code bigint NOT NULL,
  en_short_name varchar(500) NULL,
  en_name varchar(128) NULL,
  cn_name varchar(2048) NULL,
  director varchar(32) NULL,
  remark varchar(2048) NULL,
  business_caliber varchar(500) NULL,
  bus_type_id varchar(32) NULL,
  domain_id varchar(32) NULL,
  bus_process_id varchar(32) NULL,
  calculate_function varchar(500) NULL,
  decimal_number integer NULL,
  unit_id varchar(32) NULL,
  remove_duplicates boolean NULL,
  submit boolean NULL,
  create_time timestamp NULL,
  create_by varchar(100) NULL,
  update_time timestamp NULL,
  update_by varchar(100) NULL,
  PRIMARY KEY (id)
);
CREATE INDEX IF NOT EXISTS idx_yak_indicator_atom_1 ON t_yak_ops_indicator_atom (project_code);
CREATE INDEX IF NOT EXISTS idx_yak_indicator_atom_2 ON t_yak_ops_indicator_atom (bus_type_id);
CREATE INDEX IF NOT EXISTS idx_yak_indicator_atom_3 ON t_yak_ops_indicator_atom (domain_id);
CREATE INDEX IF NOT EXISTS idx_yak_indicator_atom_4 ON t_yak_ops_indicator_atom (bus_process_id);
CREATE INDEX IF NOT EXISTS idx_yak_indicator_atom_5 ON t_yak_ops_indicator_atom (unit_id);

CREATE TABLE IF NOT EXISTS t_yak_ops_indicator_derivative (
  id varchar(32) NOT NULL,
  project_code bigint NOT NULL,
  time_period_id varchar(32) NULL,
  modifier_id varchar(2000) NULL,
  atomic_id varchar(32) NULL,
  dwh_id varchar(32) NULL,
  mart_id varchar(32) NULL,
  subject_id varchar(32) NULL,
  bus_type_id varchar(32) NULL,
  domain_id varchar(32) NULL,
  bus_process_id varchar(128) NULL,
  en_short_name varchar(128) NULL,
  en_name varchar(2048) NULL,
  cn_name varchar(2048) NULL,
  director varchar(32) NULL,
  remark varchar(2048) NULL,
  submit boolean NULL,
  create_time timestamp NULL,
  create_by varchar(100) NULL,
  update_time timestamp NULL,
  update_by varchar(100) NULL,
  PRIMARY KEY (id)
);
CREATE INDEX IF NOT EXISTS idx_yak_indicator_derivative_1 ON t_yak_ops_indicator_derivative (project_code);
CREATE INDEX IF NOT EXISTS idx_yak_indicator_derivative_2 ON t_yak_ops_indicator_derivative (atomic_id);
CREATE INDEX IF NOT EXISTS idx_yak_indicator_derivative_3 ON t_yak_ops_indicator_derivative (time_period_id);
CREATE INDEX IF NOT EXISTS idx_yak_indicator_derivative_4 ON t_yak_ops_indicator_derivative (bus_type_id);
CREATE INDEX IF NOT EXISTS idx_yak_indicator_derivative_5 ON t_yak_ops_indicator_derivative (domain_id);

CREATE TABLE IF NOT EXISTS t_yak_ops_indicator_compose (
  id varchar(32) NOT NULL,
  project_code bigint NOT NULL,
  dwh_id varchar(32) NULL,
  mart_id varchar(32) NULL,
  subject_id varchar(32) NULL,
  bus_type_id varchar(32) NULL,
  domain_id varchar(32) NULL,
  bus_process_id varchar(32) NULL,
  en_short_name varchar(128) NULL,
  en_name varchar(1024) NULL,
  cn_name varchar(1024) NULL,
  director varchar(32) NULL,
  remark varchar(2048) NULL,
  compute_mode varchar(32) NULL,
  compute_expr varchar(2048) NULL,
  derive_id varchar(32) NULL,
  compare_type varchar(32) NULL,
  submit boolean NULL,
  create_time timestamp NULL,
  create_by varchar(100) NULL,
  update_time timestamp NULL,
  update_by varchar(100) NULL,
  PRIMARY KEY (id)
);
CREATE INDEX IF NOT EXISTS idx_yak_indicator_compose_1 ON t_yak_ops_indicator_compose (project_code);
CREATE INDEX IF NOT EXISTS idx_yak_indicator_compose_2 ON t_yak_ops_indicator_compose (derive_id);
CREATE INDEX IF NOT EXISTS idx_yak_indicator_compose_3 ON t_yak_ops_indicator_compose (bus_type_id);
CREATE INDEX IF NOT EXISTS idx_yak_indicator_compose_4 ON t_yak_ops_indicator_compose (domain_id);

CREATE TABLE IF NOT EXISTS t_yak_ops_indicator_modifier (
  id varchar(32) NOT NULL,
  project_code bigint NOT NULL,
  modifier_type varchar(100) NULL,
  en_short_name varchar(128) NULL,
  en_name varchar(2048) NULL,
  cn_name varchar(2048) NULL,
  director varchar(32) NULL,
  business_caliber varchar(500) NULL,
  remark varchar(2048) NULL,
  submit boolean NULL,
  dwh_id varchar(32) NULL,
  mart_id varchar(32) NULL,
  subject_id varchar(32) NULL,
  bus_type_id varchar(32) NULL,
  domain_id varchar(32) NULL,
  dimension_tb_id varchar(32) NULL,
  dimension_fd_id varchar(32) NULL,
  code_id varchar(32) NULL,
  code_detail_id varchar(32) NULL,
  create_time timestamp NULL,
  create_by varchar(100) NULL,
  update_time timestamp NULL,
  update_by varchar(100) NULL,
  PRIMARY KEY (id)
);
CREATE INDEX IF NOT EXISTS idx_yak_indicator_modifier_1 ON t_yak_ops_indicator_modifier (project_code);
CREATE INDEX IF NOT EXISTS idx_yak_indicator_modifier_2 ON t_yak_ops_indicator_modifier (modifier_type);
CREATE INDEX IF NOT EXISTS idx_yak_indicator_modifier_3 ON t_yak_ops_indicator_modifier (code_id);
CREATE INDEX IF NOT EXISTS idx_yak_indicator_modifier_4 ON t_yak_ops_indicator_modifier (code_detail_id);

CREATE TABLE IF NOT EXISTS t_yak_ops_time_period_category (
  id varchar(32) NOT NULL,
  project_code bigint NOT NULL,
  cn_name varchar(255) NULL,
  en_name varchar(255) NULL,
  parent_id varchar(32) NULL,
  leaf boolean NULL,
  create_time timestamp NULL,
  create_by varchar(100) NULL,
  update_time timestamp NULL,
  update_by varchar(100) NULL,
  PRIMARY KEY (id)
);
CREATE INDEX IF NOT EXISTS idx_yak_time_period_category_1 ON t_yak_ops_time_period_category (project_code);
CREATE INDEX IF NOT EXISTS idx_yak_time_period_category_2 ON t_yak_ops_time_period_category (parent_id);

CREATE TABLE IF NOT EXISTS t_yak_ops_time_period (
  id varchar(32) NOT NULL,
  project_code bigint NOT NULL,
  category_id varchar(32) NOT NULL,
  en_short_name varchar(255) NULL,
  cn_name varchar(255) NULL,
  en_name varchar(255) NULL,
  director varchar(100) NULL,
  remark text NULL,
  submit boolean NULL,
  create_time timestamp NULL,
  create_by varchar(100) NULL,
  update_time timestamp NULL,
  update_by varchar(100) NULL,
  PRIMARY KEY (id)
);
CREATE INDEX IF NOT EXISTS idx_yak_time_period_1 ON t_yak_ops_time_period (project_code);
CREATE INDEX IF NOT EXISTS idx_yak_time_period_2 ON t_yak_ops_time_period (category_id);

CREATE TABLE IF NOT EXISTS t_yak_ops_version_management (
  id varchar(32) NOT NULL,
  project_code bigint NOT NULL,
  source_table_id varchar(32) NOT NULL,
  version_data text NOT NULL,
  version_type varchar(100) NOT NULL,
  version_number integer NOT NULL,
  create_time timestamp NULL,
  create_by varchar(100) NULL,
  update_time timestamp NULL,
  update_by varchar(100) NULL,
  PRIMARY KEY (id)
);
CREATE INDEX IF NOT EXISTS idx_yak_version_management_1 ON t_yak_ops_version_management (project_code, source_table_id);
CREATE INDEX IF NOT EXISTS idx_yak_version_management_2 ON t_yak_ops_version_management (version_type);
CREATE UNIQUE INDEX IF NOT EXISTS uk_yak_version_management_1 ON t_yak_ops_version_management (project_code, source_table_id, version_type, version_number);


-- Dimension modeling: dimension tables and their field definitions.
-- Both tables are isolated by the active Yak Ops project.
CREATE TABLE IF NOT EXISTS t_yak_ops_dimension_table (
  id varchar(32) NOT NULL,
  project_code bigint NOT NULL,
  dwh_id varchar(32) NULL,
  bus_type_id varchar(32) NULL,
  bus_process_id varchar(32) NULL,
  domain_id varchar(32) NULL,
  mart_id varchar(32) NULL,
  subject_id varchar(32) NULL,
  time_period_id varchar(1000) NULL,
  dim_id varchar(1000) NULL,
  modifier varchar(1000) NULL,
  name_rule_id varchar(32) NULL,
  table_name varchar(500) NULL,
  cn_name varchar(500) NULL,
  table_type varchar(50) NULL,
  storage_policy varchar(50) NULL,
  life_cycle integer NULL,
  director varchar(32) NULL,
  remark varchar(2048) NULL,
  submit boolean NULL,
  create_by varchar(100) NULL,
  create_time timestamp NULL,
  update_by varchar(100) NULL,
  update_time timestamp NULL,
  PRIMARY KEY (id)
);
CREATE INDEX IF NOT EXISTS idx_yak_dimension_table_1 ON t_yak_ops_dimension_table (project_code, dwh_id);
CREATE INDEX IF NOT EXISTS idx_yak_dimension_table_2 ON t_yak_ops_dimension_table (bus_type_id, domain_id, bus_process_id);
CREATE INDEX IF NOT EXISTS idx_yak_dimension_table_3 ON t_yak_ops_dimension_table (mart_id, subject_id);
CREATE INDEX IF NOT EXISTS idx_yak_dimension_table_4 ON t_yak_ops_dimension_table (project_code, table_name);
CREATE INDEX IF NOT EXISTS idx_yak_dimension_table_5 ON t_yak_ops_dimension_table (project_code, cn_name);

CREATE TABLE IF NOT EXISTS t_yak_ops_dimension_table_fields (
  id varchar(32) NOT NULL,
  project_code bigint NOT NULL,
  table_id varchar(32) NULL,
  field_name varchar(50) NULL,
  cn_name varchar(255) NULL,
  data_type varchar(50) NULL,
  length integer NULL,
  precision integer NULL,
  remark varchar(2048) NULL,
  primary_flag boolean NULL,
  can_null boolean NULL,
  source_tb varchar(100) NULL,
  source_field varchar(100) NULL,
  field_type varchar(50) NULL,
  unit_id varchar(32) NULL,
  field_std_id varchar(32) NULL,
  code_std_id varchar(32) NULL,
  create_by varchar(100) NULL,
  create_time timestamp NULL,
  update_by varchar(100) NULL,
  update_time timestamp NULL,
  PRIMARY KEY (id)
);
CREATE INDEX IF NOT EXISTS idx_yak_dimension_fields_1 ON t_yak_ops_dimension_table_fields (project_code, table_id);
CREATE INDEX IF NOT EXISTS idx_yak_dimension_fields_2 ON t_yak_ops_dimension_table_fields (unit_id);
CREATE INDEX IF NOT EXISTS idx_yak_dimension_fields_3 ON t_yak_ops_dimension_table_fields (field_std_id);
CREATE INDEX IF NOT EXISTS idx_yak_dimension_fields_4 ON t_yak_ops_dimension_table_fields (code_std_id);

-- Table descriptions
COMMENT ON TABLE t_yak_ops_business_type IS '智能建模-业务分类';
COMMENT ON TABLE t_yak_ops_business_type_related_data_domain IS '智能建模-业务分类数据域关联';
COMMENT ON TABLE t_yak_ops_dw_layer IS '智能建模-数仓分层';
COMMENT ON TABLE t_yak_ops_data_domain IS '智能建模-数据域';
COMMENT ON TABLE t_yak_ops_business_process IS '智能建模-业务过程';
COMMENT ON TABLE t_yak_ops_data_mart IS '智能建模-数据集市';
COMMENT ON TABLE t_yak_ops_subject_domain IS '智能建模-主题域';
COMMENT ON TABLE t_yak_ops_dimension_table IS '智能建模-维度表元数据';
COMMENT ON TABLE t_yak_ops_dimension_table_fields IS '智能建模-维度表字段元数据';
COMMENT ON TABLE t_yak_ops_standard_directory IS '数据标准-标准目录';
COMMENT ON TABLE t_yak_ops_field_standard IS '数据标准-字段标准';
COMMENT ON TABLE t_yak_ops_code_standard IS '数据标准-标准代码';
COMMENT ON TABLE t_yak_ops_code_detail IS '数据标准-标准代码明细';
COMMENT ON TABLE t_yak_ops_unit_standard IS '数据标准-度量单位';
COMMENT ON TABLE t_yak_ops_dictionary_standard IS '数据标准-命名词典';
COMMENT ON TABLE t_yak_ops_indicator_atom IS '指标管理-原子指标';
COMMENT ON TABLE t_yak_ops_indicator_derivative IS '指标管理-派生指标';
COMMENT ON TABLE t_yak_ops_indicator_compose IS '指标管理-复合指标';
COMMENT ON TABLE t_yak_ops_indicator_modifier IS '指标管理-指标修饰词';
COMMENT ON TABLE t_yak_ops_time_period_category IS '指标管理-时间周期目录';
COMMENT ON TABLE t_yak_ops_time_period IS '指标管理-时间周期';
COMMENT ON TABLE t_yak_ops_version_management IS '指标管理-版本管理';
