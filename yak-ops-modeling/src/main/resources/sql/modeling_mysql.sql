-- Yak Ops data modeling schema (warehouse planning domain)
-- Additive and repeatable. All rows are isolated by the authenticated project_code.

CREATE TABLE IF NOT EXISTS `t_cockpit_business_type`
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
    KEY `idx_t_cockpit_business_type_project_code` (`project_code`),

    PRIMARY KEY (`id`)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COMMENT='智能建模-业务分类';

CREATE TABLE IF NOT EXISTS `t_cockpit_dw_layer`
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
    KEY `idx_t_cockpit_dw_layer_project_code` (`project_code`),

    primary key (`id`)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COMMENT='智能建模-数仓分层';

CREATE TABLE IF NOT EXISTS `t_cockpit_data_domain`
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
    KEY `idx_t_cockpit_data_domain_project_code` (`project_code`),

    primary key (`id`)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COMMENT='智能建模-公共层-数据域';

CREATE TABLE IF NOT EXISTS `t_cockpit_business_process`
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
    KEY `idx_t_cockpit_business_process_project_code` (`project_code`),

    primary key (`id`)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COMMENT='智能建模-公共层-业务过程';

CREATE TABLE IF NOT EXISTS `t_cockpit_data_mart`
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
    KEY `idx_t_cockpit_data_mart_project_code` (`project_code`),

    PRIMARY KEY (`id`)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COMMENT='智能建模-应用层-数据集市';

CREATE TABLE IF NOT EXISTS `t_cockpit_subject_domain`
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
    KEY `idx_t_cockpit_subject_domain_project_code` (`project_code`),

    PRIMARY KEY (`id`)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COMMENT='智能建模-应用层-主题域';

CREATE TABLE IF NOT EXISTS `t_cockpit_business_type_related_data_domain`
(
    `id`          varchar(32) NOT NULL COMMENT '主键',
    `project_code` bigint NOT NULL COMMENT 'Yak Ops workspace project code',
    `bus_type_id` varchar(32) NOT NULL COMMENT '业务类型ID',
    `data_id`     varchar(32) NOT NULL COMMENT '数据域ID',
    `create_by`   varchar(32) DEFAULT NULL COMMENT '创建人',
    `create_time` datetime    DEFAULT NULL COMMENT '创建时间',
    `update_by`   varchar(32) DEFAULT NULL COMMENT '最后更新人',
    `update_time` datetime    DEFAULT NULL COMMENT '最后更新时间',
    KEY `idx_t_cockpit_business_type_related_data_domain_project_code` (`project_code`),

    PRIMARY KEY (`id`)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COMMENT ='业务分类-数据域关联表';
