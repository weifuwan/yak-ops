-- Yak Ops 自有业务权限与菜单初始化。
-- Yak Security 的用户、角色、系统管理权限和系统菜单由 yak-framework 维护。
-- 本目录只保留一个最终状态迁移，避免先创建历史目录再通过后续版本修正。

-- 1. 清理早期开发阶段已经移除的 Yak Ops 业务目录和授权关系。
UPDATE yak_security_role_menu role_menu
JOIN yak_security_menu menu_row
  ON menu_row.id = role_menu.menu_id
 AND menu_row.app_name = role_menu.app_name
SET role_menu.is_delete = 1
WHERE role_menu.app_name = '${appName}'
  AND menu_row.menu_code IN (
    'realtime-link-up',
    'workflow',
    'workflow-project',
    'workflow-management',
    'workflow-instance',
    'client',
    'connector',
    'quality',
    'data-quality',
    'data-quality-report',
    'operations',
    'metrics',
    'alarm',
    'knowledge-management'
  );

UPDATE yak_security_menu
SET active = 0,
    visible = 0,
    is_delete = 1
WHERE app_name = '${appName}'
  AND menu_code IN (
    'realtime-link-up',
    'workflow',
    'workflow-project',
    'workflow-management',
    'workflow-instance',
    'client',
    'connector',
    'quality',
    'data-quality',
    'data-quality-report',
    'operations',
    'metrics',
    'alarm',
    'knowledge-management'
  );

UPDATE yak_security_role_permission role_permission
JOIN yak_security_permission permission_row
  ON permission_row.id = role_permission.permission_id
 AND permission_row.app_name = role_permission.app_name
SET role_permission.is_delete = 1
WHERE role_permission.app_name = '${appName}'
  AND (
    permission_row.permission_code IN ('workflow', 'quality', 'operations', 'knowledge')
    OR permission_row.permission_code LIKE 'workflow:%'
    OR permission_row.permission_code LIKE 'task:realtime:%'
    OR permission_row.permission_code LIKE 'quality:%'
    OR permission_row.permission_code LIKE 'operations:%'
    OR permission_row.permission_code LIKE 'knowledge:%'
    OR permission_row.permission_code LIKE 'resource:client:%'
    OR permission_row.permission_code LIKE 'resource:connector:%'
  );

UPDATE yak_security_permission
SET active = 0,
    is_delete = 1
WHERE app_name = '${appName}'
  AND (
    permission_code IN ('workflow', 'quality', 'operations', 'knowledge')
    OR permission_code LIKE 'workflow:%'
    OR permission_code LIKE 'task:realtime:%'
    OR permission_code LIKE 'quality:%'
    OR permission_code LIKE 'operations:%'
    OR permission_code LIKE 'knowledge:%'
    OR permission_code LIKE 'resource:client:%'
    OR permission_code LIKE 'resource:connector:%'
  );

-- 2. Yak Ops 业务权限分组。
-- SQL 管理的权限 declared=0，避免声明式注册器在移除 Java Provider 后将其停用。
INSERT INTO yak_security_permission
(permission_code, permission_name, parent_id, leaf, level, description,
 active, declared, menu_code, app_name)
VALUES
('task', '数据集成', 0, 0, 1, 'Yak Ops 数据集成权限',
 1, 0, NULL, '${appName}'),
('datasource', '数据源管理兼容权限', 0, 0, 1, 'Yak Ops 旧版数据源权限',
 1, 0, NULL, '${appName}'),
('job', '任务管理兼容权限', 0, 0, 1, 'Yak Ops 旧版任务权限',
 1, 0, NULL, '${appName}'),
('resource', '资源管理', 0, 0, 1, 'Yak Ops 数据源与文件资源权限',
 1, 0, NULL, '${appName}')
ON DUPLICATE KEY UPDATE
permission_name = VALUES(permission_name),
parent_id = VALUES(parent_id),
leaf = VALUES(leaf),
level = VALUES(level),
description = VALUES(description),
active = VALUES(active),
declared = VALUES(declared),
menu_code = VALUES(menu_code),
is_delete = 0;

-- 3. 业务权限叶子节点直接绑定最终菜单，不再通过后续迁移补关系。
INSERT INTO yak_security_permission
(permission_code, permission_name, parent_id, leaf, level, description,
 active, declared, menu_code, app_name)
SELECT item.permission_code,
       item.permission_name,
       parent.id,
       1,
       2,
       item.description,
       1,
       0,
       item.menu_code,
       parent.app_name
FROM yak_security_permission parent
JOIN (
    SELECT 'task' parent_code,
           'task:batch:read' permission_code,
           '查看离线同步' permission_name,
           '查看离线同步页面及接口' description,
           'batch-link-up' menu_code
    UNION ALL SELECT 'task', 'task:batch:create', '新建离线同步',
                     '创建离线同步任务', 'batch-link-up'

    UNION ALL SELECT 'datasource', 'datasource:view', '查看数据源',
                     '旧版数据源查看权限', 'data-source'
    UNION ALL SELECT 'datasource', 'datasource:create', '新增数据源',
                     '旧版数据源新增权限', 'data-source'
    UNION ALL SELECT 'datasource', 'datasource:update', '编辑数据源',
                     '旧版数据源编辑权限', 'data-source'
    UNION ALL SELECT 'datasource', 'datasource:delete', '删除数据源',
                     '旧版数据源删除权限', 'data-source'
    UNION ALL SELECT 'datasource', 'datasource:test', '测试数据源连接',
                     '旧版数据源连接测试权限', 'data-source'

    UNION ALL SELECT 'job', 'job:view', '查看任务',
                     '旧版离线任务查看权限', 'batch-link-up'
    UNION ALL SELECT 'job', 'job:create', '新增任务',
                     '旧版离线任务新增权限', 'batch-link-up'
    UNION ALL SELECT 'job', 'job:update', '编辑任务',
                     '旧版离线任务编辑权限', 'batch-link-up'
    UNION ALL SELECT 'job', 'job:delete', '删除任务',
                     '旧版离线任务删除权限', 'batch-link-up'
    UNION ALL SELECT 'job', 'job:execute', '执行任务',
                     '旧版离线任务执行权限', 'batch-link-up'
    UNION ALL SELECT 'job', 'job:stop', '停止任务',
                     '旧版离线任务停止权限', 'batch-link-up'

    UNION ALL SELECT 'resource', 'resource:data-source:read', '查看数据源管理',
                     '查看数据源管理页面及接口', 'data-source'
    UNION ALL SELECT 'resource', 'resource:view', '查看文件资源',
                     '查看文件资源页面及接口', 'resource-management'
    UNION ALL SELECT 'resource', 'resource:upload', '上传文件资源',
                     '上传文件资源', 'resource-management'
    UNION ALL SELECT 'resource', 'resource:download', '下载文件资源',
                     '下载文件资源', 'resource-management'
    UNION ALL SELECT 'resource', 'resource:update', '编辑文件资源',
                     '编辑文件资源', 'resource-management'
    UNION ALL SELECT 'resource', 'resource:delete', '删除文件资源',
                     '删除文件资源', 'resource-management'
) item ON item.parent_code = parent.permission_code
WHERE parent.app_name = '${appName}'
  AND parent.is_delete = 0
ON DUPLICATE KEY UPDATE
permission_name = VALUES(permission_name),
parent_id = VALUES(parent_id),
leaf = VALUES(leaf),
level = VALUES(level),
description = VALUES(description),
active = VALUES(active),
declared = VALUES(declared),
menu_code = VALUES(menu_code),
is_delete = 0;

-- 4. Yak Ops 最终业务菜单。
INSERT INTO yak_security_menu
(menu_code, menu_name, parent_code, route_path, icon_key, menu_type,
 sort_order, visible, active, required_permission_code, description, app_name)
VALUES
('home', '首页', NULL, '/', 'home', 2,
 0, 1, 1, NULL, 'Yak Ops 首页', '${appName}'),
('integration', '数据集成', NULL, NULL, 'sync', 1,
 10, 1, 1, NULL, '数据同步任务入口', '${appName}'),
('batch-link-up', '离线同步', 'integration', '/sync/batch-link-up', 'sync', 2,
 10, 1, 1, 'task:batch:read', '离线数据同步管理', '${appName}'),
('resources', '资源管理', NULL, NULL, 'database', 1,
 20, 1, 1, NULL, '数据源与文件资源入口', '${appName}'),
('data-source', '数据源管理', 'resources', '/data-source', 'database', 2,
 10, 1, 1, 'resource:data-source:read', '数据源管理', '${appName}'),
('resource-management', '文件资源', 'resources', '/resource-management', 'database', 2,
 20, 1, 1, 'resource:view', '文件资源管理', '${appName}')
ON DUPLICATE KEY UPDATE
menu_name = VALUES(menu_name),
parent_code = VALUES(parent_code),
route_path = VALUES(route_path),
icon_key = VALUES(icon_key),
menu_type = VALUES(menu_type),
sort_order = VALUES(sort_order),
visible = VALUES(visible),
active = VALUES(active),
required_permission_code = VALUES(required_permission_code),
description = VALUES(description),
is_delete = 0;

-- 5. 已有角色根据业务权限自动获得对应菜单。
INSERT INTO yak_security_role_menu(role_id, menu_id, app_name)
SELECT DISTINCT role_permission.role_id,
                menu_row.id,
                role_permission.app_name
FROM yak_security_role_permission role_permission
JOIN yak_security_permission permission_row
  ON permission_row.id = role_permission.permission_id
 AND permission_row.app_name = role_permission.app_name
 AND permission_row.is_delete = 0
 AND permission_row.active = 1
 AND permission_row.menu_code IS NOT NULL
JOIN yak_security_menu menu_row
  ON menu_row.menu_code = permission_row.menu_code
 AND menu_row.app_name = role_permission.app_name
 AND menu_row.is_delete = 0
 AND menu_row.active = 1
WHERE role_permission.app_name = '${appName}'
  AND role_permission.is_delete = 0
ON DUPLICATE KEY UPDATE is_delete = 0;

-- 6. 已有角色自动获得二级菜单所属的一级目录。
INSERT INTO yak_security_role_menu(role_id, menu_id, app_name)
SELECT DISTINCT role_menu.role_id,
                parent_menu.id,
                role_menu.app_name
FROM yak_security_role_menu role_menu
JOIN yak_security_menu child_menu
  ON child_menu.id = role_menu.menu_id
 AND child_menu.app_name = role_menu.app_name
 AND child_menu.is_delete = 0
JOIN yak_security_menu parent_menu
  ON parent_menu.menu_code = child_menu.parent_code
 AND parent_menu.app_name = role_menu.app_name
 AND parent_menu.is_delete = 0
 AND parent_menu.active = 1
WHERE role_menu.app_name = '${appName}'
  AND role_menu.is_delete = 0
  AND child_menu.parent_code IS NOT NULL
ON DUPLICATE KEY UPDATE is_delete = 0;

-- 7. root 角色保留全部 Yak Ops 业务菜单。
INSERT INTO yak_security_role_menu(role_id, menu_id, app_name)
SELECT DISTINCT role_permission.role_id,
                menu_row.id,
                role_permission.app_name
FROM yak_security_role_permission role_permission
JOIN yak_security_permission permission_row
  ON permission_row.id = role_permission.permission_id
 AND permission_row.app_name = role_permission.app_name
 AND permission_row.permission_code = 'security:root'
 AND permission_row.is_delete = 0
JOIN yak_security_menu menu_row
  ON menu_row.app_name = role_permission.app_name
 AND menu_row.is_delete = 0
 AND menu_row.active = 1
WHERE role_permission.app_name = '${appName}'
  AND role_permission.is_delete = 0
ON DUPLICATE KEY UPDATE is_delete = 0;
