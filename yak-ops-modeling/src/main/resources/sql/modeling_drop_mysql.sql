-- Yak Ops modeling schema reset (MySQL).
-- WARNING: destructive. Drops all 22 modeling tables and all project data in them.
-- Run manually only after backing up the target database.
-- Never execute this script during application startup.

DROP TABLE IF EXISTS `t_yak_ops_dimension_table_fields`;
DROP TABLE IF EXISTS `t_yak_ops_dimension_table`;
DROP TABLE IF EXISTS `t_yak_ops_version_management`;
DROP TABLE IF EXISTS `t_yak_ops_time_period`;
DROP TABLE IF EXISTS `t_yak_ops_time_period_category`;
DROP TABLE IF EXISTS `t_yak_ops_indicator_modifier`;
DROP TABLE IF EXISTS `t_yak_ops_indicator_compose`;
DROP TABLE IF EXISTS `t_yak_ops_indicator_derivative`;
DROP TABLE IF EXISTS `t_yak_ops_indicator_atom`;
DROP TABLE IF EXISTS `t_yak_ops_dictionary_standard`;
DROP TABLE IF EXISTS `t_yak_ops_unit_standard`;
DROP TABLE IF EXISTS `t_yak_ops_code_detail`;
DROP TABLE IF EXISTS `t_yak_ops_code_standard`;
DROP TABLE IF EXISTS `t_yak_ops_field_standard`;
DROP TABLE IF EXISTS `t_yak_ops_standard_directory`;
DROP TABLE IF EXISTS `t_yak_ops_business_type_related_data_domain`;
DROP TABLE IF EXISTS `t_yak_ops_subject_domain`;
DROP TABLE IF EXISTS `t_yak_ops_data_mart`;
DROP TABLE IF EXISTS `t_yak_ops_business_process`;
DROP TABLE IF EXISTS `t_yak_ops_data_domain`;
DROP TABLE IF EXISTS `t_yak_ops_dw_layer`;
DROP TABLE IF EXISTS `t_yak_ops_business_type`;
