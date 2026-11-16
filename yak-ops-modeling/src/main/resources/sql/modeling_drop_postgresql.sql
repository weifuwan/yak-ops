-- Yak Ops modeling schema reset (PostgreSQL).
-- WARNING: destructive. Drops the 22 logical modeling tables (current and legacy names)
-- and all modeling data belonging to every Yak Ops project.
-- Base tables such as t_yak_ops_project, users, roles and permissions are not touched.
-- Legacy t_cockpit_ names are included so startup compatibility logic cannot rename old data back.
-- Run manually only after backing up the target database.
-- Never execute this script during application startup.

DROP TABLE IF EXISTS t_cockpit_business_type;
DROP TABLE IF EXISTS t_cockpit_business_type_related_data_domain;
DROP TABLE IF EXISTS t_cockpit_dw_layer;
DROP TABLE IF EXISTS t_cockpit_data_domain;
DROP TABLE IF EXISTS t_cockpit_business_process;
DROP TABLE IF EXISTS t_cockpit_data_mart;
DROP TABLE IF EXISTS t_cockpit_subject_domain;
DROP TABLE IF EXISTS t_cockpit_dimension_table;
DROP TABLE IF EXISTS t_cockpit_dimension_table_fields;
DROP TABLE IF EXISTS t_cockpit_standard_directory;
DROP TABLE IF EXISTS t_cockpit_field_standard;
DROP TABLE IF EXISTS t_cockpit_code_standard;
DROP TABLE IF EXISTS t_cockpit_code_detail;
DROP TABLE IF EXISTS t_cockpit_unit_standard;
DROP TABLE IF EXISTS t_cockpit_dictionary_standard;
DROP TABLE IF EXISTS t_cockpit_indicator_atom;
DROP TABLE IF EXISTS t_cockpit_indicator_derivative;
DROP TABLE IF EXISTS t_cockpit_indicator_compose;
DROP TABLE IF EXISTS t_cockpit_indicator_modifier;
DROP TABLE IF EXISTS t_cockpit_time_period_category;
DROP TABLE IF EXISTS t_cockpit_time_period;
DROP TABLE IF EXISTS t_cockpit_version_management;
DROP TABLE IF EXISTS t_yak_ops_business_type;
DROP TABLE IF EXISTS t_yak_ops_business_type_related_data_domain;
DROP TABLE IF EXISTS t_yak_ops_dw_layer;
DROP TABLE IF EXISTS t_yak_ops_data_domain;
DROP TABLE IF EXISTS t_yak_ops_business_process;
DROP TABLE IF EXISTS t_yak_ops_data_mart;
DROP TABLE IF EXISTS t_yak_ops_subject_domain;
DROP TABLE IF EXISTS t_yak_ops_dimension_table;
DROP TABLE IF EXISTS t_yak_ops_dimension_table_fields;
DROP TABLE IF EXISTS t_yak_ops_standard_directory;
DROP TABLE IF EXISTS t_yak_ops_field_standard;
DROP TABLE IF EXISTS t_yak_ops_code_standard;
DROP TABLE IF EXISTS t_yak_ops_code_detail;
DROP TABLE IF EXISTS t_yak_ops_unit_standard;
DROP TABLE IF EXISTS t_yak_ops_dictionary_standard;
DROP TABLE IF EXISTS t_yak_ops_indicator_atom;
DROP TABLE IF EXISTS t_yak_ops_indicator_derivative;
DROP TABLE IF EXISTS t_yak_ops_indicator_compose;
DROP TABLE IF EXISTS t_yak_ops_indicator_modifier;
DROP TABLE IF EXISTS t_yak_ops_time_period_category;
DROP TABLE IF EXISTS t_yak_ops_time_period;
DROP TABLE IF EXISTS t_yak_ops_version_management;
