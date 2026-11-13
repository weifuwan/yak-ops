-- Additive schema for data standards and data indicators.
-- Every row is scoped to a Yak Ops project through project_code.

CREATE TABLE IF NOT EXISTS t_cockpit_standard_directory (
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
CREATE INDEX IF NOT EXISTS idx_standard_directory_1 ON t_cockpit_standard_directory (project_code, dir_module);
CREATE INDEX IF NOT EXISTS idx_standard_directory_2 ON t_cockpit_standard_directory (parent_id);
CREATE UNIQUE INDEX IF NOT EXISTS uk_standard_directory_1 ON t_cockpit_standard_directory (project_code, dir_module, dir_name);

CREATE TABLE IF NOT EXISTS t_cockpit_field_standard (
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
CREATE INDEX IF NOT EXISTS idx_field_standard_1 ON t_cockpit_field_standard (project_code);
CREATE INDEX IF NOT EXISTS idx_field_standard_2 ON t_cockpit_field_standard (parent_id);
CREATE INDEX IF NOT EXISTS idx_field_standard_3 ON t_cockpit_field_standard (reference);

CREATE TABLE IF NOT EXISTS t_cockpit_code_standard (
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
CREATE INDEX IF NOT EXISTS idx_code_standard_1 ON t_cockpit_code_standard (project_code);
CREATE INDEX IF NOT EXISTS idx_code_standard_2 ON t_cockpit_code_standard (parent_id);

CREATE TABLE IF NOT EXISTS t_cockpit_code_detail (
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
CREATE INDEX IF NOT EXISTS idx_code_detail_1 ON t_cockpit_code_detail (project_code);
CREATE INDEX IF NOT EXISTS idx_code_detail_2 ON t_cockpit_code_detail (code_id);

CREATE TABLE IF NOT EXISTS t_cockpit_unit_standard (
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
CREATE INDEX IF NOT EXISTS idx_unit_standard_1 ON t_cockpit_unit_standard (project_code);
CREATE INDEX IF NOT EXISTS idx_unit_standard_2 ON t_cockpit_unit_standard (parent_id);

CREATE TABLE IF NOT EXISTS t_cockpit_dictionary_standard (
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
CREATE INDEX IF NOT EXISTS idx_dictionary_standard_1 ON t_cockpit_dictionary_standard (project_code);

CREATE TABLE IF NOT EXISTS t_cockpit_indicator_atom (
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
CREATE INDEX IF NOT EXISTS idx_indicator_atom_1 ON t_cockpit_indicator_atom (project_code);
CREATE INDEX IF NOT EXISTS idx_indicator_atom_2 ON t_cockpit_indicator_atom (bus_type_id);
CREATE INDEX IF NOT EXISTS idx_indicator_atom_3 ON t_cockpit_indicator_atom (domain_id);
CREATE INDEX IF NOT EXISTS idx_indicator_atom_4 ON t_cockpit_indicator_atom (bus_process_id);
CREATE INDEX IF NOT EXISTS idx_indicator_atom_5 ON t_cockpit_indicator_atom (unit_id);

CREATE TABLE IF NOT EXISTS t_cockpit_indicator_derivative (
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
CREATE INDEX IF NOT EXISTS idx_indicator_derivative_1 ON t_cockpit_indicator_derivative (project_code);
CREATE INDEX IF NOT EXISTS idx_indicator_derivative_2 ON t_cockpit_indicator_derivative (atomic_id);
CREATE INDEX IF NOT EXISTS idx_indicator_derivative_3 ON t_cockpit_indicator_derivative (time_period_id);
CREATE INDEX IF NOT EXISTS idx_indicator_derivative_4 ON t_cockpit_indicator_derivative (bus_type_id);
CREATE INDEX IF NOT EXISTS idx_indicator_derivative_5 ON t_cockpit_indicator_derivative (domain_id);

CREATE TABLE IF NOT EXISTS t_cockpit_indicator_compose (
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
CREATE INDEX IF NOT EXISTS idx_indicator_compose_1 ON t_cockpit_indicator_compose (project_code);
CREATE INDEX IF NOT EXISTS idx_indicator_compose_2 ON t_cockpit_indicator_compose (derive_id);
CREATE INDEX IF NOT EXISTS idx_indicator_compose_3 ON t_cockpit_indicator_compose (bus_type_id);
CREATE INDEX IF NOT EXISTS idx_indicator_compose_4 ON t_cockpit_indicator_compose (domain_id);

CREATE TABLE IF NOT EXISTS t_cockpit_indicator_modifier (
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
CREATE INDEX IF NOT EXISTS idx_indicator_modifier_1 ON t_cockpit_indicator_modifier (project_code);
CREATE INDEX IF NOT EXISTS idx_indicator_modifier_2 ON t_cockpit_indicator_modifier (modifier_type);
CREATE INDEX IF NOT EXISTS idx_indicator_modifier_3 ON t_cockpit_indicator_modifier (code_id);
CREATE INDEX IF NOT EXISTS idx_indicator_modifier_4 ON t_cockpit_indicator_modifier (code_detail_id);

CREATE TABLE IF NOT EXISTS t_cockpit_time_period_category (
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
CREATE INDEX IF NOT EXISTS idx_time_period_category_1 ON t_cockpit_time_period_category (project_code);
CREATE INDEX IF NOT EXISTS idx_time_period_category_2 ON t_cockpit_time_period_category (parent_id);

CREATE TABLE IF NOT EXISTS t_cockpit_time_period (
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
CREATE INDEX IF NOT EXISTS idx_time_period_1 ON t_cockpit_time_period (project_code);
CREATE INDEX IF NOT EXISTS idx_time_period_2 ON t_cockpit_time_period (category_id);

CREATE TABLE IF NOT EXISTS t_cockpit_version_management (
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
CREATE INDEX IF NOT EXISTS idx_version_management_1 ON t_cockpit_version_management (project_code, source_table_id);
CREATE INDEX IF NOT EXISTS idx_version_management_2 ON t_cockpit_version_management (version_type);
CREATE UNIQUE INDEX IF NOT EXISTS uk_version_management_1 ON t_cockpit_version_management (project_code, source_table_id, version_type, version_number);
