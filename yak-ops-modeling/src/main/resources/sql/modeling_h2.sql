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
