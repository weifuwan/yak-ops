-- Yak Ops data modeling schema (warehouse planning domain)
-- Additive and repeatable. All rows are isolated by the authenticated project_code.

CREATE TABLE IF NOT EXISTS `t_yak_ops_business_type`
(
    `id`            varchar(32) not null comment '主键',
    `project_code` bigint NOT NULL COMMENT 'Yak Ops workspace project code',
    `en_short_name` varchar(100) default null comment '英文缩写',
    `business_name` varchar(255) default null comment '业务名称',
    `reviewer`      varchar(500) default null comment '模型评审人，逗号隔开的字符串',
    `director`      varchar(100) default null comment '负责人',
    `remark`        text comment '备注',
    `parent_id`     varchar(50)  default null comment '父id',
    `create_time`   datetime     default null comment '创建时间',
    `create_by`     varchar(100) default null comment '创建人',
    `update_time`   datetime     default null comment '更新时间',
    `update_by`     varchar(100) default null comment '更新人',
    KEY `idx_yak_business_type_project_code` (`project_code`),

    PRIMARY KEY (`id`)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COMMENT='智能建模-业务分类';

CREATE TABLE IF NOT EXISTS `t_yak_ops_dw_layer`
(
    `id`            varchar(32) not null comment '主键',
    `project_code` bigint NOT NULL COMMENT 'Yak Ops workspace project code',
    `en_short_name` varchar(100) default null comment '英文缩写',
    `en_name`       varchar(255) default null comment '英文名',
    `cn_name`       varchar(255) default null comment '中文名',
    `dw_type`       varchar(20)  default null comment '数仓分层类型：dim-贴源层、dwd-公共层、ads-应用层、other-其他',
    `dw_model_type` varchar(20)  default null comment '数仓模型类型：SOURCE-贴源表、DIMENSION-维度表、DETAIL-明细表、SUMMARY-汇总表、APPLICATION-应用表',
    `director`      varchar(100) default null comment '负责人',
    `remark`        text         default null comment '备注',
    `create_time`   datetime     default null comment '创建时间',
    `create_by`     varchar(100) default null comment '创建人',
    `update_time`   datetime     default null comment '更新时间',
    `update_by`     varchar(100) default null comment '更新人',
    KEY `idx_yak_dw_layer_project_code` (`project_code`),

    primary key (`id`)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COMMENT='智能建模-数仓分层';

CREATE TABLE IF NOT EXISTS `t_yak_ops_data_domain`
(
    `id`            varchar(32) not null comment '主键',
    `project_code` bigint NOT NULL COMMENT 'Yak Ops workspace project code',
    `en_short_name` varchar(100) default null comment '英文缩写',
    `en_name`       varchar(255) default null comment '英文名',
    `cn_name`       varchar(255) default null comment '中文名',
    `reviewer`      varchar(500) default null comment '模型评审人，逗号隔开的字符串',
    `director`      varchar(100) default null comment '负责人',
    `remark`        text         default null comment '备注',
    `flag`          int          default 0 comment '是否默认',
    `create_time`   datetime     default null comment '创建时间',
    `create_by`     varchar(100) default null comment '创建人',
    `update_time`   datetime     default null comment '更新时间',
    `update_by`     varchar(100) default null comment '更新人',
    KEY `idx_yak_data_domain_project_code` (`project_code`),

    primary key (`id`)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COMMENT='智能建模-公共层-数据域';

CREATE TABLE IF NOT EXISTS `t_yak_ops_business_process`
(
    `id`             varchar(32) not null comment '主键',
    `project_code` bigint NOT NULL COMMENT 'Yak Ops workspace project code',
    `en_short_name`  varchar(100) default null comment '英文缩写',
    `en_name`        varchar(255) default null comment '英文名',
    `cn_name`        varchar(255) default null comment '中文名',
    `data_domain_id` varchar(32)  default null comment '数据域ID',
    `director`       varchar(100) default null comment '负责人',
    `remark`         text         default null comment '备注',
    `flag`           int          default 0 comment '是否默认',
    `create_time`    datetime     default null comment '创建时间',
    `create_by`      varchar(100) default null comment '创建人',
    `update_time`    datetime     default null comment '更新时间',
    `update_by`      varchar(100) default null comment '更新人',
    KEY `idx_yak_business_process_project_code` (`project_code`),

    primary key (`id`)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COMMENT='智能建模-公共层-业务过程';

CREATE TABLE IF NOT EXISTS `t_yak_ops_data_mart`
(
    `id`               varchar(32) not null comment '主键',
    `project_code` bigint NOT NULL COMMENT 'Yak Ops workspace project code',
    `en_short_name`    varchar(100) default null comment '英文缩写',
    `mart_name`        varchar(255) default null comment '集市名称',
    `mart_type`        varchar(50)  default null comment '集市类型',
    `business_type_id` varchar(32)  default null comment '业务分类ID',
    `director`         varchar(100) default null comment '负责人',
    `remark`           text comment '备注',
    `parent_id`        varchar(50)  default null comment '父id',
    `create_time`      datetime     default null comment '创建时间',
    `create_by`        varchar(100) default null comment '创建人',
    `update_time`      datetime     default null comment '更新时间',
    `update_by`        varchar(100) default null comment '更新人',
    KEY `idx_yak_data_mart_project_code` (`project_code`),

    PRIMARY KEY (`id`)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COMMENT='智能建模-应用层-数据集市';

CREATE TABLE IF NOT EXISTS `t_yak_ops_subject_domain`
(
    `id`            varchar(32) not null comment '主键',
    `project_code` bigint NOT NULL COMMENT 'Yak Ops workspace project code',
    `en_short_name` varchar(100) default null comment '英文缩写',
    `domain_name`   varchar(255) default null comment '主题域名称',
    `data_mart_id`  varchar(32)  default null comment '所属数据集市ID',
    `director`      varchar(100) default null comment '负责人',
    `remark`        text comment '备注',
    `parent_id`     varchar(50)  default null comment '父id',
    `create_time`   datetime     default null comment '创建时间',
    `create_by`     varchar(100) default null comment '创建人',
    `update_time`   datetime     default null comment '更新时间',
    `update_by`     varchar(100) default null comment '更新人',
    KEY `idx_yak_subject_domain_project_code` (`project_code`),

    PRIMARY KEY (`id`)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COMMENT='智能建模-应用层-主题域';

CREATE TABLE IF NOT EXISTS `t_yak_ops_business_type_related_data_domain`
(
    `id`          varchar(32) NOT NULL COMMENT '主键',
    `project_code` bigint NOT NULL COMMENT 'Yak Ops workspace project code',
    `bus_type_id` varchar(32) NOT NULL COMMENT '业务类型ID',
    `data_id`     varchar(32) NOT NULL COMMENT '数据域ID',
    `create_by`   varchar(32) DEFAULT NULL COMMENT '创建人',
    `create_time` datetime    DEFAULT NULL COMMENT '创建时间',
    `update_by`   varchar(32) DEFAULT NULL COMMENT '最后更新人',
    `update_time` datetime    DEFAULT NULL COMMENT '最后更新时间',
    KEY `idx_yak_business_type_related_data_domain_project_code` (`project_code`),

    PRIMARY KEY (`id`)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COMMENT ='业务分类-数据域关联表';

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
  PRIMARY KEY (id),
  KEY idx_yak_standard_directory_1 (project_code, dir_module),
  KEY idx_yak_standard_directory_2 (parent_id),
  UNIQUE KEY uk_yak_standard_directory_1 (project_code, dir_module, dir_name)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COMMENT='数据标准-标准目录';

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
  PRIMARY KEY (id),
  KEY idx_yak_field_standard_1 (project_code),
  KEY idx_yak_field_standard_2 (parent_id),
  KEY idx_yak_field_standard_3 (reference)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COMMENT='数据标准-字段标准';

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
  PRIMARY KEY (id),
  KEY idx_yak_code_standard_1 (project_code),
  KEY idx_yak_code_standard_2 (parent_id)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COMMENT='数据标准-标准代码';

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
  PRIMARY KEY (id),
  KEY idx_yak_code_detail_1 (project_code),
  KEY idx_yak_code_detail_2 (code_id)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COMMENT='数据标准-标准代码明细';

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
  PRIMARY KEY (id),
  KEY idx_yak_unit_standard_1 (project_code),
  KEY idx_yak_unit_standard_2 (parent_id)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COMMENT='数据标准-度量单位';

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
  PRIMARY KEY (id),
  KEY idx_yak_dictionary_standard_1 (project_code)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COMMENT='数据标准-命名词典';

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
  PRIMARY KEY (id),
  KEY idx_yak_indicator_atom_1 (project_code),
  KEY idx_yak_indicator_atom_2 (bus_type_id),
  KEY idx_yak_indicator_atom_3 (domain_id),
  KEY idx_yak_indicator_atom_4 (bus_process_id),
  KEY idx_yak_indicator_atom_5 (unit_id)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COMMENT='指标管理-原子指标';

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
  PRIMARY KEY (id),
  KEY idx_yak_indicator_derivative_1 (project_code),
  KEY idx_yak_indicator_derivative_2 (atomic_id),
  KEY idx_yak_indicator_derivative_3 (time_period_id),
  KEY idx_yak_indicator_derivative_4 (bus_type_id),
  KEY idx_yak_indicator_derivative_5 (domain_id)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COMMENT='指标管理-派生指标';

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
  PRIMARY KEY (id),
  KEY idx_yak_indicator_compose_1 (project_code),
  KEY idx_yak_indicator_compose_2 (derive_id),
  KEY idx_yak_indicator_compose_3 (bus_type_id),
  KEY idx_yak_indicator_compose_4 (domain_id)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COMMENT='指标管理-复合指标';

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
  PRIMARY KEY (id),
  KEY idx_yak_indicator_modifier_1 (project_code),
  KEY idx_yak_indicator_modifier_2 (modifier_type),
  KEY idx_yak_indicator_modifier_3 (code_id),
  KEY idx_yak_indicator_modifier_4 (code_detail_id)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COMMENT='指标管理-指标修饰词';

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
  PRIMARY KEY (id),
  KEY idx_yak_time_period_category_1 (project_code),
  KEY idx_yak_time_period_category_2 (parent_id)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COMMENT='指标管理-时间周期目录';

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
  PRIMARY KEY (id),
  KEY idx_yak_time_period_1 (project_code),
  KEY idx_yak_time_period_2 (category_id)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COMMENT='指标管理-时间周期';

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
  PRIMARY KEY (id),
  KEY idx_yak_version_management_1 (project_code, source_table_id),
  KEY idx_yak_version_management_2 (version_type),
  UNIQUE KEY uk_yak_version_management_1 (project_code, source_table_id, version_type, version_number)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COMMENT='指标管理-版本管理';


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
  PRIMARY KEY (id),
  KEY idx_yak_dimension_table_1 (project_code, dwh_id),
  KEY idx_yak_dimension_table_2 (bus_type_id, domain_id, bus_process_id),
  KEY idx_yak_dimension_table_3 (mart_id, subject_id),
  KEY idx_yak_dimension_table_4 (project_code, table_name),
  KEY idx_yak_dimension_table_5 (project_code, cn_name)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COMMENT='智能建模-维度表元数据';

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
  PRIMARY KEY (id),
  KEY idx_yak_dimension_fields_1 (project_code, table_id),
  KEY idx_yak_dimension_fields_2 (unit_id),
  KEY idx_yak_dimension_fields_3 (field_std_id),
  KEY idx_yak_dimension_fields_4 (code_std_id)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COMMENT='智能建模-维度表字段元数据';
