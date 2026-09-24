-- Yak Security MariaDB 10.6+ baseline.
-- 关系完整性由 Service 事务维护，不创建物理外键。
-- 本脚本包含最终表结构、基础资源类型、系统权限目录、菜单目录及兼容授权数据。

CREATE TABLE yak_security_dept (
  id BIGINT NOT NULL AUTO_INCREMENT COMMENT '主键',
  dept_name VARCHAR(64) NOT NULL COMMENT '部门名称',
  parent_id BIGINT NOT NULL DEFAULT 0 COMMENT '父部门主键，0 表示根节点',
  leaf TINYINT(1) NOT NULL COMMENT '是否叶子节点：0 否，1 是',
  level INT NOT NULL COMMENT '部门层级',
  description VARCHAR(255) NULL COMMENT '部门描述',
  create_time TIMESTAMP NOT NULL DEFAULT CURRENT_TIMESTAMP COMMENT '创建时间',
  update_time TIMESTAMP NOT NULL DEFAULT CURRENT_TIMESTAMP ON UPDATE CURRENT_TIMESTAMP COMMENT '更新时间',
  is_delete TINYINT(1) NOT NULL DEFAULT 0 COMMENT '逻辑删除：0 未删除，1 已删除',
  app_name VARCHAR(64) NOT NULL COMMENT '应用级数据隔离键',
  active_dept_name VARCHAR(64)
    GENERATED ALWAYS AS (IF(is_delete=0,dept_name,NULL)) STORED
    COMMENT '未删除部门唯一键',
  PRIMARY KEY(id),
  UNIQUE KEY uk_dept_app_name(app_name,active_dept_name),
  KEY idx_dept_app_parent(app_name,parent_id),
  KEY idx_dept_app_delete(app_name,is_delete)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COMMENT='部门表';

CREATE TABLE yak_security_message (
  id BIGINT NOT NULL AUTO_INCREMENT COMMENT '主键',
  title VARCHAR(128) NOT NULL COMMENT '消息标题',
  content VARCHAR(1024) NULL COMMENT '消息内容',
  read_tag TINYINT(1) NOT NULL DEFAULT 0 COMMENT '阅读状态：0 未读，1 已读',
  oplog_id BIGINT NULL COMMENT '操作日志主键',
  user_id BIGINT NOT NULL COMMENT '接收用户主键',
  create_time TIMESTAMP NOT NULL DEFAULT CURRENT_TIMESTAMP COMMENT '创建时间',
  update_time TIMESTAMP NOT NULL DEFAULT CURRENT_TIMESTAMP ON UPDATE CURRENT_TIMESTAMP COMMENT '更新时间',
  is_delete TINYINT(1) NOT NULL DEFAULT 0 COMMENT '逻辑删除：0 未删除，1 已删除',
  app_name VARCHAR(64) NOT NULL COMMENT '应用级数据隔离键',
  PRIMARY KEY(id),
  KEY idx_message_app_user(app_name,user_id,is_delete),
  KEY idx_message_app_oplog(app_name,oplog_id)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COMMENT='消息表';

CREATE TABLE yak_security_oplog (
  id BIGINT NOT NULL AUTO_INCREMENT COMMENT '主键',
  operator_ip VARCHAR(45) NOT NULL COMMENT '操作者 IP 地址',
  operator VARCHAR(64) NULL COMMENT '操作者账号',
  operate_page VARCHAR(64) NOT NULL COMMENT '操作页面',
  operate_type VARCHAR(64) NOT NULL COMMENT '操作类型',
  target_type VARCHAR(64) NOT NULL COMMENT '对象类型',
  target VARCHAR(128) NOT NULL COMMENT '操作对象',
  operation_methods VARCHAR(64) NOT NULL COMMENT '操作方式',
  detail TEXT NULL COMMENT '日志详情',
  create_time TIMESTAMP NOT NULL DEFAULT CURRENT_TIMESTAMP COMMENT '创建时间',
  update_time TIMESTAMP NOT NULL DEFAULT CURRENT_TIMESTAMP ON UPDATE CURRENT_TIMESTAMP COMMENT '更新时间',
  is_delete TINYINT(1) NOT NULL DEFAULT 0 COMMENT '逻辑删除：0 未删除，1 已删除',
  app_name VARCHAR(64) NOT NULL COMMENT '应用级数据隔离键',
  PRIMARY KEY(id),
  KEY idx_oplog_app_operator(app_name,operator),
  KEY idx_oplog_app_time(app_name,create_time),
  KEY idx_oplog_app_delete(app_name,is_delete)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COMMENT='操作日志表';

CREATE TABLE yak_security_oplog_extra (
  id BIGINT NOT NULL AUTO_INCREMENT COMMENT '主键',
  info VARCHAR(128) NULL COMMENT '扩展信息',
  type TINYINT NOT NULL COMMENT '信息类型：1 操作页面，2 操作类型，3 对象类型',
  create_time TIMESTAMP NOT NULL DEFAULT CURRENT_TIMESTAMP COMMENT '创建时间',
  update_time TIMESTAMP NOT NULL DEFAULT CURRENT_TIMESTAMP ON UPDATE CURRENT_TIMESTAMP COMMENT '更新时间',
  is_delete TINYINT(1) NOT NULL DEFAULT 0 COMMENT '逻辑删除：0 未删除，1 已删除',
  app_name VARCHAR(64) NOT NULL COMMENT '应用级数据隔离键',
  PRIMARY KEY(id),
  KEY idx_oplog_extra_app_type(app_name,type,is_delete)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COMMENT='操作日志扩展信息表';

CREATE TABLE yak_security_permission (
  id BIGINT NOT NULL AUTO_INCREMENT COMMENT '主键',
  permission_code VARCHAR(128) NOT NULL COMMENT '权限编码',
  permission_name VARCHAR(128) NOT NULL COMMENT '权限名称',
  parent_id BIGINT NOT NULL DEFAULT 0 COMMENT '父权限主键，0 表示根节点',
  leaf TINYINT(1) NOT NULL COMMENT '是否叶子权限：0 否，1 是',
  level INT NOT NULL COMMENT '权限层级',
  description VARCHAR(255) NULL COMMENT '权限描述',
  active TINYINT(1) NOT NULL DEFAULT 1 COMMENT '权限是否有效：0 否，1 是',
  declared TINYINT(1) NOT NULL DEFAULT 0 COMMENT '是否由声明式注册管理',
  create_time TIMESTAMP NOT NULL DEFAULT CURRENT_TIMESTAMP COMMENT '创建时间',
  update_time TIMESTAMP NOT NULL DEFAULT CURRENT_TIMESTAMP ON UPDATE CURRENT_TIMESTAMP COMMENT '更新时间',
  is_delete TINYINT(1) NOT NULL DEFAULT 0 COMMENT '逻辑删除：0 未删除，1 已删除',
  app_name VARCHAR(64) NOT NULL COMMENT '应用级数据隔离键',
  active_permission_code VARCHAR(128)
    GENERATED ALWAYS AS (IF(is_delete=0,permission_code,NULL)) STORED
    COMMENT '未删除权限唯一键',
  PRIMARY KEY(id),
  UNIQUE KEY uk_permission_app_code(app_name,active_permission_code),
  KEY idx_permission_app_parent(app_name,parent_id),
  KEY idx_permission_app_delete(app_name,is_delete),
  KEY idx_permission_app_declared(app_name,declared,active)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COMMENT='权限表';

CREATE TABLE yak_security_project (
  id BIGINT NOT NULL AUTO_INCREMENT COMMENT '主键',
  project_code VARCHAR(128) NOT NULL COMMENT '项目编码',
  project_name VARCHAR(128) NOT NULL COMMENT '项目名称',
  description VARCHAR(512) NOT NULL DEFAULT '' COMMENT '项目描述',
  dept_id BIGINT NOT NULL COMMENT '所属部门主键',
  running TINYINT(1) NOT NULL DEFAULT 1 COMMENT '运行状态：0 停用，1 启用',
  create_time TIMESTAMP NOT NULL DEFAULT CURRENT_TIMESTAMP COMMENT '创建时间',
  update_time TIMESTAMP NOT NULL DEFAULT CURRENT_TIMESTAMP ON UPDATE CURRENT_TIMESTAMP COMMENT '更新时间',
  is_delete TINYINT(1) NOT NULL DEFAULT 0 COMMENT '逻辑删除：0 未删除，1 已删除',
  app_name VARCHAR(64) NOT NULL COMMENT '应用级数据隔离键',
  active_project_code VARCHAR(128)
    GENERATED ALWAYS AS (IF(is_delete=0,project_code,NULL)) STORED
    COMMENT '未删除项目唯一键',
  PRIMARY KEY(id),
  UNIQUE KEY uk_project_app_code(app_name,active_project_code),
  KEY idx_project_app_dept(app_name,dept_id),
  KEY idx_project_app_delete(app_name,is_delete)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COMMENT='项目表';

CREATE TABLE yak_security_resource_type (
  id BIGINT NOT NULL AUTO_INCREMENT COMMENT '主键',
  type_name VARCHAR(64) NOT NULL COMMENT '资源类型名称',
  create_time TIMESTAMP NOT NULL DEFAULT CURRENT_TIMESTAMP COMMENT '创建时间',
  update_time TIMESTAMP NOT NULL DEFAULT CURRENT_TIMESTAMP ON UPDATE CURRENT_TIMESTAMP COMMENT '更新时间',
  is_delete TINYINT(1) NOT NULL DEFAULT 0 COMMENT '逻辑删除：0 未删除，1 已删除',
  app_name VARCHAR(64) NOT NULL COMMENT '应用级数据隔离键',
  active_type_name VARCHAR(64)
    GENERATED ALWAYS AS (IF(is_delete=0,type_name,NULL)) STORED
    COMMENT '未删除资源类型唯一键',
  PRIMARY KEY(id),
  UNIQUE KEY uk_resource_type_app_name(app_name,active_type_name),
  KEY idx_resource_type_app_delete(app_name,is_delete)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COMMENT='资源类型表';

CREATE TABLE yak_security_role (
  id BIGINT NOT NULL AUTO_INCREMENT COMMENT '主键',
  role_code VARCHAR(128) NOT NULL COMMENT '角色编码',
  role_name VARCHAR(128) NOT NULL COMMENT '角色名称',
  description VARCHAR(255) NULL COMMENT '角色描述',
  last_reviser VARCHAR(64) NULL COMMENT '最后修改人',
  create_time TIMESTAMP NOT NULL DEFAULT CURRENT_TIMESTAMP COMMENT '创建时间',
  update_time TIMESTAMP NOT NULL DEFAULT CURRENT_TIMESTAMP ON UPDATE CURRENT_TIMESTAMP COMMENT '更新时间',
  is_delete TINYINT(1) NOT NULL DEFAULT 0 COMMENT '逻辑删除：0 未删除，1 已删除',
  app_name VARCHAR(64) NOT NULL COMMENT '应用级数据隔离键',
  active_role_code VARCHAR(128)
    GENERATED ALWAYS AS (IF(is_delete=0,role_code,NULL)) STORED
    COMMENT '未删除角色唯一键',
  PRIMARY KEY(id),
  UNIQUE KEY uk_role_app_code(app_name,active_role_code),
  KEY idx_role_app_delete(app_name,is_delete)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COMMENT='角色表';

CREATE TABLE yak_security_user (
  id BIGINT NOT NULL AUTO_INCREMENT COMMENT '主键',
  user_name VARCHAR(64) NOT NULL COMMENT '用户账号',
  pw VARCHAR(2048) NOT NULL COMMENT '单向哈希密码',
  salt VARCHAR(64) NOT NULL DEFAULT '' COMMENT '密码盐（兼容字段）',
  real_name VARCHAR(128) NOT NULL DEFAULT '' COMMENT '真实姓名',
  phone VARCHAR(32) NOT NULL DEFAULT '' COMMENT '手机号码',
  email VARCHAR(128) NOT NULL DEFAULT '' COMMENT '电子邮箱',
  dept_id BIGINT NULL COMMENT '所属部门主键',
  status INT NOT NULL DEFAULT 1 COMMENT '用户状态：1 正常，2 禁用',
  create_time TIMESTAMP NOT NULL DEFAULT CURRENT_TIMESTAMP COMMENT '创建时间',
  update_time TIMESTAMP NOT NULL DEFAULT CURRENT_TIMESTAMP ON UPDATE CURRENT_TIMESTAMP COMMENT '更新时间',
  is_delete TINYINT(1) NOT NULL DEFAULT 0 COMMENT '逻辑删除：0 未删除，1 已删除',
  app_name VARCHAR(64) NOT NULL COMMENT '应用级数据隔离键',
  active_user_name VARCHAR(64)
    GENERATED ALWAYS AS (IF(is_delete=0,user_name,NULL)) STORED
    COMMENT '未删除用户唯一键',
  PRIMARY KEY(id),
  UNIQUE KEY uk_user_app_name(app_name,active_user_name),
  KEY idx_user_app_dept(app_name,dept_id),
  KEY idx_user_app_email(app_name,email),
  KEY idx_user_app_phone(app_name,phone),
  KEY idx_user_app_delete(app_name,is_delete)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COMMENT='用户表';

CREATE TABLE yak_security_role_permission (
  id BIGINT NOT NULL AUTO_INCREMENT COMMENT '主键',
  role_id BIGINT NOT NULL COMMENT '角色主键',
  permission_id BIGINT NOT NULL COMMENT '权限主键',
  create_time TIMESTAMP NOT NULL DEFAULT CURRENT_TIMESTAMP COMMENT '创建时间',
  update_time TIMESTAMP NOT NULL DEFAULT CURRENT_TIMESTAMP ON UPDATE CURRENT_TIMESTAMP COMMENT '更新时间',
  is_delete TINYINT(1) NOT NULL DEFAULT 0 COMMENT '逻辑删除：0 未删除，1 已删除',
  app_name VARCHAR(64) NOT NULL COMMENT '应用级数据隔离键',
  active_relation TINYINT
    GENERATED ALWAYS AS (IF(is_delete=0,0,NULL)) STORED
    COMMENT '未删除关系唯一键',
  PRIMARY KEY(id),
  UNIQUE KEY uk_role_permission_active(app_name,role_id,permission_id,active_relation),
  KEY idx_role_permission_permission(app_name,permission_id,is_delete),
  KEY idx_role_permission_role(app_name,role_id,is_delete)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COMMENT='角色权限关系表';

CREATE TABLE yak_security_user_project (
  id BIGINT NOT NULL AUTO_INCREMENT COMMENT '主键',
  user_id BIGINT NOT NULL COMMENT '用户主键',
  user_type TINYINT NOT NULL DEFAULT 0 COMMENT '项目用户类型：0 普通成员，1 项目负责人',
  project_id BIGINT NOT NULL COMMENT '项目主键',
  create_time TIMESTAMP NOT NULL DEFAULT CURRENT_TIMESTAMP COMMENT '创建时间',
  update_time TIMESTAMP NOT NULL DEFAULT CURRENT_TIMESTAMP ON UPDATE CURRENT_TIMESTAMP COMMENT '更新时间',
  is_delete TINYINT(1) NOT NULL DEFAULT 0 COMMENT '逻辑删除：0 未删除，1 已删除',
  app_name VARCHAR(64) NOT NULL COMMENT '应用级数据隔离键',
  active_relation TINYINT
    GENERATED ALWAYS AS (IF(is_delete=0,0,NULL)) STORED
    COMMENT '未删除关系唯一键',
  PRIMARY KEY(id),
  UNIQUE KEY uk_user_project_active(app_name,user_id,project_id,active_relation),
  KEY idx_user_project_project(app_name,project_id,is_delete),
  KEY idx_user_project_user(app_name,user_id,is_delete)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COMMENT='用户项目关系表';

CREATE TABLE yak_security_user_resource (
  id BIGINT NOT NULL AUTO_INCREMENT COMMENT '主键',
  user_id BIGINT NOT NULL COMMENT '用户主键',
  project_id BIGINT NOT NULL COMMENT '项目主键',
  resource_type_id BIGINT NOT NULL COMMENT '资源类型主键',
  resource_id BIGINT NOT NULL COMMENT '业务资源主键',
  control_level TINYINT NOT NULL COMMENT '管理级别：1 查看，2 管理',
  create_time TIMESTAMP NOT NULL DEFAULT CURRENT_TIMESTAMP COMMENT '创建时间',
  update_time TIMESTAMP NOT NULL DEFAULT CURRENT_TIMESTAMP ON UPDATE CURRENT_TIMESTAMP COMMENT '更新时间',
  is_delete TINYINT(1) NOT NULL DEFAULT 0 COMMENT '逻辑删除：0 未删除，1 已删除',
  app_name VARCHAR(64) NOT NULL COMMENT '应用级数据隔离键',
  active_relation TINYINT
    GENERATED ALWAYS AS (IF(is_delete=0,0,NULL)) STORED
    COMMENT '未删除关系唯一键',
  PRIMARY KEY(id),
  UNIQUE KEY uk_user_resource_active(
    app_name,user_id,project_id,resource_type_id,resource_id,active_relation
  ),
  KEY idx_user_resource_project(app_name,project_id,is_delete),
  KEY idx_user_resource_type(app_name,resource_type_id,is_delete),
  KEY idx_user_resource_resource(app_name,resource_id,is_delete),
  KEY idx_user_resource_user(app_name,user_id,is_delete)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COMMENT='用户资源关系表';

CREATE TABLE yak_security_user_role (
  id BIGINT NOT NULL AUTO_INCREMENT COMMENT '主键',
  user_id BIGINT NOT NULL COMMENT '用户主键',
  role_id BIGINT NOT NULL COMMENT '角色主键',
  create_time TIMESTAMP NOT NULL DEFAULT CURRENT_TIMESTAMP COMMENT '创建时间',
  update_time TIMESTAMP NOT NULL DEFAULT CURRENT_TIMESTAMP ON UPDATE CURRENT_TIMESTAMP COMMENT '更新时间',
  is_delete TINYINT(1) NOT NULL DEFAULT 0 COMMENT '逻辑删除：0 未删除，1 已删除',
  app_name VARCHAR(64) NOT NULL COMMENT '应用级数据隔离键',
  active_relation TINYINT
    GENERATED ALWAYS AS (IF(is_delete=0,0,NULL)) STORED
    COMMENT '未删除关系唯一键',
  PRIMARY KEY(id),
  UNIQUE KEY uk_user_role_active(app_name,user_id,role_id,active_relation),
  KEY idx_user_role_role(app_name,role_id,is_delete),
  KEY idx_user_role_user(app_name,user_id,is_delete)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COMMENT='用户角色关系表';

CREATE TABLE yak_security_config (
  id BIGINT NOT NULL AUTO_INCREMENT COMMENT '主键',
  value_group VARCHAR(100) NOT NULL DEFAULT '' COMMENT '配置分组',
  value_name VARCHAR(100) NOT NULL DEFAULT '' COMMENT '配置名称',
  value TEXT NULL COMMENT '配置值',
  edit INT NOT NULL DEFAULT 1 COMMENT '编辑策略：1 不可编辑，2 可编辑',
  status INT NOT NULL DEFAULT 1 COMMENT '配置状态：1 正常，2 禁用',
  memo VARCHAR(1000) NOT NULL DEFAULT '' COMMENT '备注',
  operator VARCHAR(64) NULL COMMENT '操作者',
  create_time TIMESTAMP NOT NULL DEFAULT CURRENT_TIMESTAMP COMMENT '创建时间',
  update_time TIMESTAMP NOT NULL DEFAULT CURRENT_TIMESTAMP ON UPDATE CURRENT_TIMESTAMP COMMENT '更新时间',
  is_delete TINYINT(1) NOT NULL DEFAULT 0 COMMENT '逻辑删除：0 未删除，1 已删除',
  app_name VARCHAR(64) NOT NULL COMMENT '应用级数据隔离键',
  active_config_name VARCHAR(100)
    GENERATED ALWAYS AS (IF(is_delete=0,value_name,NULL)) STORED
    COMMENT '未删除配置唯一键',
  PRIMARY KEY(id),
  UNIQUE KEY uk_config_app_group_name(app_name,value_group,active_config_name),
  KEY idx_config_app_status(app_name,status,is_delete)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COMMENT='安全配置表';

CREATE TABLE yak_security_menu (
  id BIGINT NOT NULL AUTO_INCREMENT COMMENT '主键',
  menu_code VARCHAR(128) NOT NULL COMMENT '稳定菜单编码，与宿主应用路由标识对应',
  menu_name VARCHAR(128) NOT NULL COMMENT '菜单名称',
  parent_code VARCHAR(128) NULL COMMENT '父菜单编码，NULL 表示根节点',
  route_path VARCHAR(255) NULL COMMENT '前端路由路径',
  icon_key VARCHAR(64) NULL COMMENT '前端图标键',
  menu_type TINYINT NOT NULL DEFAULT 2 COMMENT '菜单类型：1 目录，2 页面',
  sort_order INT NOT NULL DEFAULT 0 COMMENT '排序号',
  visible TINYINT(1) NOT NULL DEFAULT 1 COMMENT '是否显示：0 否，1 是',
  active TINYINT(1) NOT NULL DEFAULT 1 COMMENT '是否启用：0 否，1 是',
  required_permission_code VARCHAR(128) NULL COMMENT '访问菜单隐含授予的读取权限编码',
  description VARCHAR(255) NULL COMMENT '菜单说明',
  create_time TIMESTAMP NOT NULL DEFAULT CURRENT_TIMESTAMP COMMENT '创建时间',
  update_time TIMESTAMP NOT NULL DEFAULT CURRENT_TIMESTAMP ON UPDATE CURRENT_TIMESTAMP COMMENT '更新时间',
  is_delete TINYINT(1) NOT NULL DEFAULT 0 COMMENT '逻辑删除：0 未删除，1 已删除',
  app_name VARCHAR(64) NOT NULL COMMENT '应用级数据隔离键',
  active_menu_code VARCHAR(128)
    GENERATED ALWAYS AS (IF(is_delete=0,menu_code,NULL)) STORED
    COMMENT '未删除菜单唯一键',
  PRIMARY KEY(id),
  UNIQUE KEY uk_menu_app_code(app_name,active_menu_code),
  KEY idx_menu_app_parent(app_name,parent_code,is_delete),
  KEY idx_menu_app_permission(app_name,required_permission_code,is_delete),
  KEY idx_menu_app_status(app_name,active,visible,is_delete)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COMMENT='菜单定义表';

CREATE TABLE yak_security_role_menu (
  id BIGINT NOT NULL AUTO_INCREMENT COMMENT '主键',
  role_id BIGINT NOT NULL COMMENT '角色主键',
  menu_id BIGINT NOT NULL COMMENT '菜单主键',
  create_time TIMESTAMP NOT NULL DEFAULT CURRENT_TIMESTAMP COMMENT '创建时间',
  update_time TIMESTAMP NOT NULL DEFAULT CURRENT_TIMESTAMP ON UPDATE CURRENT_TIMESTAMP COMMENT '更新时间',
  is_delete TINYINT(1) NOT NULL DEFAULT 0 COMMENT '逻辑删除：0 未删除，1 已删除',
  app_name VARCHAR(64) NOT NULL COMMENT '应用级数据隔离键',
  active_relation TINYINT
    GENERATED ALWAYS AS (IF(is_delete=0,0,NULL)) STORED
    COMMENT '未删除关系唯一键',
  PRIMARY KEY(id),
  UNIQUE KEY uk_role_menu_active(app_name,role_id,menu_id,active_relation),
  KEY idx_role_menu_role(app_name,role_id,is_delete),
  KEY idx_role_menu_menu(app_name,menu_id,is_delete)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COMMENT='角色菜单关系表';

-- 仅初始化系统运行所需的资源类型；不创建用户或密码。
INSERT INTO yak_security_resource_type(type_name,app_name)
VALUES
('PROJECT','${appName}'),
('SECURITY_RESOURCE','${appName}');

-- Yak Security 自有的系统管理权限、菜单及历史授权兼容数据。
-- 宿主应用只需要在自己的迁移脚本中维护业务权限和业务菜单。

-- 1. 系统管理权限分组。
INSERT INTO yak_security_permission
(permission_code,permission_name,parent_id,leaf,level,description,active,declared,app_name)
VALUES
('security','系统管理',0,0,1,'Yak Security 系统管理权限',1,0,'${appName}')
ON DUPLICATE KEY UPDATE
permission_name=VALUES(permission_name),
parent_id=VALUES(parent_id),
leaf=VALUES(leaf),
level=VALUES(level),
description=VALUES(description),
active=VALUES(active),
declared=VALUES(declared);

-- 2. 系统管理权限叶子节点。
INSERT INTO yak_security_permission
(permission_code,permission_name,parent_id,leaf,level,description,active,declared,app_name)
SELECT item.permission_code,
       item.permission_name,
       parent.id,
       1,
       2,
       item.description,
       1,
       0,
       parent.app_name
FROM yak_security_permission parent
JOIN (
    SELECT 'security:root' permission_code,'超级管理员' permission_name,'拥有当前应用全部权限' description
    UNION ALL SELECT 'security:user:read','查看用户管理','查看用户管理页面及接口'
    UNION ALL SELECT 'security:user:create','新增用户','创建系统用户'
    UNION ALL SELECT 'security:user:update','编辑用户','修改系统用户资料'
    UNION ALL SELECT 'security:user:reset-password','重置用户密码','管理员重置用户密码'
    UNION ALL SELECT 'security:user:delete','删除用户','删除系统用户'
    UNION ALL SELECT 'security:role:read','查看角色管理','查看角色管理页面及接口'
    UNION ALL SELECT 'security:role:create','新增角色','创建系统角色'
    UNION ALL SELECT 'security:role:update','编辑角色','修改角色资料和权限'
    UNION ALL SELECT 'security:role:assign','分配角色关系','为用户分配角色或为角色分配用户'
    UNION ALL SELECT 'security:role:delete','删除角色','删除系统角色'
    UNION ALL SELECT 'security:permission:read','查看权限管理','查看权限管理页面及接口'
    UNION ALL SELECT 'security:permission:import','导入权限','导入手工权限目录'
    UNION ALL SELECT 'security:permission:delete','删除权限','删除手工权限及角色关联'
    UNION ALL SELECT 'security:department:read','查看部门管理','查看部门管理页面及接口'
    UNION ALL SELECT 'security:project:read','查看授权项目','查看 Yak Security 授权项目页面及接口'
    UNION ALL SELECT 'security:resource-permission:read','查看资源授权','查看资源授权页面及接口'
    UNION ALL SELECT 'security:config:read','查看系统配置','查看系统配置页面及接口'
    UNION ALL SELECT 'security:operation-log:read','查看操作日志','查看操作日志页面及接口'
) item
WHERE parent.permission_code='security'
  AND parent.app_name='${appName}'
  AND parent.is_delete=0
ON DUPLICATE KEY UPDATE
permission_name=VALUES(permission_name),
parent_id=VALUES(parent_id),
leaf=VALUES(leaf),
level=VALUES(level),
description=VALUES(description),
active=VALUES(active),
declared=VALUES(declared);

-- 3. Yak Security 系统管理菜单目录。
INSERT INTO yak_security_menu
(menu_code,menu_name,parent_code,route_path,icon_key,menu_type,sort_order,visible,active,required_permission_code,description,app_name)
VALUES
('system','系统管理',NULL,NULL,'system',1,60,1,1,NULL,'Yak Security 系统管理入口','${appName}'),
('system-users','用户管理','system','/system/users','system',2,10,1,1,'security:user:read','用户管理','${appName}'),
('system-roles','角色管理','system','/system/roles','system',2,20,1,1,'security:role:read','角色及授权管理','${appName}'),
('system-permissions','权限管理','system','/system/permissions','system',2,30,1,1,'security:permission:read','操作权限管理','${appName}'),
('system-departments','部门管理','system','/system/departments','system',2,40,1,1,'security:department:read','部门管理','${appName}'),
('system-security-projects','Security 授权项目','system','/system/projects','system',2,50,1,1,'security:project:read','安全项目管理','${appName}'),
('system-resource-permissions','资源授权','system','/system/resource-permissions','system',2,60,1,1,'security:resource-permission:read','资源级授权管理','${appName}'),
('system-configs','系统配置','system','/system/configs','system',2,70,1,1,'security:config:read','系统配置管理','${appName}'),
('system-operation-logs','操作日志','system','/system/oplogs','system',2,80,1,1,'security:operation-log:read','操作日志查询','${appName}')
ON DUPLICATE KEY UPDATE
menu_name=VALUES(menu_name),
parent_code=VALUES(parent_code),
route_path=VALUES(route_path),
icon_key=VALUES(icon_key),
menu_type=VALUES(menu_type),
sort_order=VALUES(sort_order),
visible=VALUES(visible),
active=VALUES(active),
required_permission_code=VALUES(required_permission_code),
description=VALUES(description);

-- 4. 为历史数据库中的系统管理员补齐 root 权限。
INSERT IGNORE INTO yak_security_role_permission(role_id,permission_id,app_name)
SELECT role_row.id,permission_row.id,role_row.app_name
FROM yak_security_role role_row
JOIN yak_security_permission permission_row
  ON permission_row.permission_code='security:root'
 AND permission_row.app_name=role_row.app_name
 AND permission_row.is_delete=0
WHERE role_row.role_name='系统管理员'
  AND role_row.app_name='${appName}'
  AND role_row.is_delete=0;

-- 5. 根据已有读取权限回填系统菜单，升级后不丢失原页面入口。
INSERT IGNORE INTO yak_security_role_menu(role_id,menu_id,app_name)
SELECT DISTINCT role_permission.role_id,menu_row.id,role_permission.app_name
FROM yak_security_role_permission role_permission
JOIN yak_security_permission permission_row
  ON permission_row.id=role_permission.permission_id
 AND permission_row.app_name=role_permission.app_name
 AND permission_row.is_delete=0
JOIN yak_security_menu menu_row
  ON menu_row.required_permission_code=permission_row.permission_code
 AND menu_row.app_name=role_permission.app_name
 AND menu_row.parent_code='system'
 AND menu_row.is_delete=0
WHERE role_permission.app_name='${appName}'
  AND role_permission.is_delete=0;

-- 6. root 角色拥有完整的 Yak Security 系统管理菜单。
INSERT IGNORE INTO yak_security_role_menu(role_id,menu_id,app_name)
SELECT DISTINCT role_permission.role_id,menu_row.id,role_permission.app_name
FROM yak_security_role_permission role_permission
JOIN yak_security_permission permission_row
  ON permission_row.id=role_permission.permission_id
 AND permission_row.app_name=role_permission.app_name
 AND permission_row.permission_code='security:root'
 AND permission_row.is_delete=0
JOIN yak_security_menu menu_row
  ON menu_row.app_name=role_permission.app_name
 AND (menu_row.menu_code='system' OR menu_row.parent_code='system')
 AND menu_row.is_delete=0
WHERE role_permission.app_name='${appName}'
  AND role_permission.is_delete=0;
