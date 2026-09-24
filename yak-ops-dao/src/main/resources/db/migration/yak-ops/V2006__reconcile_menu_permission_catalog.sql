-- Reconcile the Yak Ops menu/permission catalog with the current product navigation.
--
-- V1000 is the consolidated baseline. Existing installations that already passed the
-- historical V13xx security migrations must still receive the current baseline catalog,
-- so this forward migration intentionally re-upserts the baseline rows instead of editing
-- historical migrations. Existing permission/menu rows keep their IDs through duplicate-key
-- updates, and existing role grants are repaired in place.

-- 1. Re-register the baseline permission groups and leaves that current protected pages use.
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
is_delete = 0;

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

-- 2. Bind every current business action to the page that owns it.
-- Once all children of a compatibility/group permission are menu-bound, Yak Security's
-- unified capability tree prunes that empty standalone permission group automatically.
UPDATE yak_security_permission
SET menu_code = CASE permission_code
    WHEN 'task:batch:read' THEN 'batch-link-up'
    WHEN 'task:batch:create' THEN 'batch-link-up'
    WHEN 'job:view' THEN 'batch-link-up'
    WHEN 'job:create' THEN 'batch-link-up'
    WHEN 'job:update' THEN 'batch-link-up'
    WHEN 'job:delete' THEN 'batch-link-up'
    WHEN 'job:execute' THEN 'batch-link-up'
    WHEN 'job:stop' THEN 'batch-link-up'

    WHEN 'task:realtime:read' THEN 'realtime-sync'
    WHEN 'task:realtime:create' THEN 'realtime-sync'
    WHEN 'task:realtime:update' THEN 'realtime-sync'
    WHEN 'task:realtime:delete' THEN 'realtime-sync'
    WHEN 'task:realtime:execute' THEN 'realtime-sync'

    WHEN 'datasource:view' THEN 'data-source'
    WHEN 'datasource:create' THEN 'data-source'
    WHEN 'datasource:update' THEN 'data-source'
    WHEN 'datasource:delete' THEN 'data-source'
    WHEN 'datasource:test' THEN 'data-source'
    WHEN 'resource:data-source:read' THEN 'data-source'

    WHEN 'resource:view' THEN 'resource-management'
    WHEN 'resource:upload' THEN 'resource-management'
    WHEN 'resource:download' THEN 'resource-management'
    WHEN 'resource:update' THEN 'resource-management'
    WHEN 'resource:delete' THEN 'resource-management'

    WHEN 'quality:monitor:read' THEN 'data-quality-table-config'
    WHEN 'quality:monitor:create' THEN 'data-quality-table-config'
    WHEN 'quality:monitor:update' THEN 'data-quality-table-config'
    WHEN 'quality:monitor:delete' THEN 'data-quality-table-config'
    WHEN 'quality:monitor:run' THEN 'data-quality-table-config'
    WHEN 'quality:execution:read' THEN 'data-quality-execution'
    WHEN 'quality:template:read' THEN 'data-quality-rule-template'
    WHEN 'quality:template:create' THEN 'data-quality-rule-template'
    WHEN 'quality:template:update' THEN 'data-quality-rule-template'
    WHEN 'quality:template:delete' THEN 'data-quality-rule-template'

    WHEN 'data-development:read' THEN 'data-development'
    WHEN 'data-development:edit' THEN 'data-development'
    WHEN 'data-development:delete' THEN 'data-development'
    WHEN 'data-development:execute' THEN 'data-development-execution'
    WHEN 'data-development:publish' THEN 'data-development-release'
    WHEN 'data-development:release' THEN 'data-development-release'

    WHEN 'data-service:read' THEN 'data-service-api'
    WHEN 'data-service:publish' THEN 'data-service-api'
    WHEN 'data-service:manage' THEN 'data-service-api'
    WHEN 'data-service:delete' THEN 'data-service-api'
    WHEN 'data-service:access' THEN 'data-service-api'
    WHEN 'data-service:runtime' THEN 'data-service-debug'
    WHEN 'data-service:observe' THEN 'data-service-overview'
    ELSE menu_code
END
WHERE app_name = '${appName}'
  AND is_delete = 0
  AND permission_code IN (
      'task:batch:read', 'task:batch:create',
      'job:view', 'job:create', 'job:update', 'job:delete', 'job:execute', 'job:stop',
      'task:realtime:read', 'task:realtime:create', 'task:realtime:update',
      'task:realtime:delete', 'task:realtime:execute',
      'datasource:view', 'datasource:create', 'datasource:update', 'datasource:delete',
      'datasource:test', 'resource:data-source:read',
      'resource:view', 'resource:upload', 'resource:download', 'resource:update',
      'resource:delete',
      'quality:monitor:read', 'quality:monitor:create', 'quality:monitor:update',
      'quality:monitor:delete', 'quality:monitor:run', 'quality:execution:read',
      'quality:template:read', 'quality:template:create', 'quality:template:update',
      'quality:template:delete',
      'data-development:read', 'data-development:edit', 'data-development:delete',
      'data-development:execute', 'data-development:publish', 'data-development:release',
      'data-service:read', 'data-service:publish', 'data-service:manage',
      'data-service:delete', 'data-service:access', 'data-service:runtime',
      'data-service:observe');

-- 3. Upsert the complete set of current visible Yak Ops menus.
-- Hidden detail/editor/redirect routes are intentionally excluded; they inherit their parent route.
-- System management menus are owned by yak-framework/yak-security and are not duplicated here.
INSERT INTO yak_security_menu
(menu_code, menu_name, parent_code, route_path, icon_key, menu_type,
 sort_order, visible, active, required_permission_code, description, app_name)
VALUES
('home', '首页', NULL, '/home', 'home', 2,
 0, 1, 1, NULL, 'Yak Ops 首页', '${appName}'),

('integration', '数据集成', NULL, NULL, 'sync', 1,
 10, 1, 1, NULL, '数据同步任务入口', '${appName}'),
('batch-link-up', '离线同步', 'integration', '/sync/batch-link-up', 'sync', 2,
 10, 1, 1, 'task:batch:read', '离线数据同步管理', '${appName}'),
('realtime-sync', '实时同步', 'integration', '/sync/realtime', 'realtime', 2,
 20, 1, 1, 'task:realtime:read', 'MySQL CDC 实时同步管理', '${appName}'),

('development', '数据开发', NULL, NULL, 'api', 1,
 20, 1, 1, NULL, '数据开发工作台入口', '${appName}'),
('data-development', '开发任务', 'development', '/data-development', 'api', 2,
 10, 1, 1, 'data-development:read', '数据开发工作台', '${appName}'),
('data-development-release', '发布中心', 'development', '/data-development/releases', 'report', 2,
 20, 1, 1, 'data-development:read', '数据开发发布中心', '${appName}'),
('data-development-execution', '运行记录', 'development', '/data-development/executions', 'report', 2,
 30, 1, 1, 'data-development:read', '数据开发运行记录', '${appName}'),

('workflow', '工作流', NULL, NULL, 'workflow', 1,
 30, 1, 1, NULL, '工作流定义与实例入口', '${appName}'),
('workflow-definition', '工作流定义', 'workflow', '/workflow/definitions', 'workflow', 2,
 10, 1, 1, NULL, '工作流定义', '${appName}'),
('workflow-instances', '工作流实例', 'workflow', '/workflow/instances', 'instance', 2,
 30, 1, 1, NULL, '工作流实例', '${appName}'),

-- 数据源管理当前是独立可见页面，不再作为“资源管理”的子菜单。
('data-source', '数据源管理', NULL, '/data-source', 'database', 2,
 10, 1, 1, 'resource:data-source:read', '数据源管理', '${appName}'),
('resources', '资源管理', NULL, NULL, 'database', 1,
 20, 1, 1, NULL, '文件资源入口', '${appName}'),
('resource-management', '文件资源', 'resources', '/resource-management', 'database', 2,
 10, 1, 1, 'resource:view', '文件资源管理', '${appName}'),

('data-quality', '数据质量', NULL, NULL, 'quality', 1,
 30, 1, 1, NULL, '数据质量页面入口', '${appName}'),
('data-quality-overview', '质量总览', 'data-quality', '/data-quality/overview', 'monitor', 2,
 5, 1, 1, 'quality:execution:read', '数据质量总览', '${appName}'),
('data-quality-table-config', '数据表监控', 'data-quality', '/data-quality/table-config', 'quality', 2,
 10, 1, 1, 'quality:monitor:read', '按数据源和数据表创建质量监控', '${appName}'),
('data-quality-execution', '运行记录', 'data-quality', '/data-quality/execution', 'report', 2,
 20, 1, 1, 'quality:execution:read', '数据质量运行记录', '${appName}'),
('data-quality-rule-template', '规则模板库', 'data-quality', '/data-quality/rule-template', 'quality', 2,
 30, 1, 1, 'quality:template:read', '数据质量规则模板库', '${appName}'),

('data-analysis', '数据消费', NULL, NULL, 'insight', 1,
 40, 1, 1, NULL, '数据消费页面入口', '${appName}'),
('dashboard', '仪表盘', 'data-analysis', '/dashboard', 'insight', 2,
 10, 1, 1, NULL, '仪表盘', '${appName}'),
('dataset-management', '数据集', 'data-analysis', '/dataset', 'database', 2,
 20, 1, 1, NULL, '数据集管理', '${appName}'),
('data-analysis-lineage', '数据血缘', 'data-analysis', '/data-analysis/lineage', 'workflow', 2,
 25, 1, 1, NULL, '数据血缘', '${appName}'),
('digital-screen', '数字化大屏', 'data-analysis', '/digital-screen', 'insight', 2,
 30, 1, 1, NULL, '数字化大屏', '${appName}'),

('data-service', '数据服务', NULL, NULL, 'api', 1,
 50, 1, 1, NULL, '数据服务管理入口', '${appName}'),
('data-service-api', 'API 集市', 'data-service', '/data-service', 'api', 2,
 10, 1, 1, 'data-service:read', '数据服务 API 集市', '${appName}'),
('data-service-debug', 'API 调试', 'data-service', '/data-service/debug', 'api', 2,
 20, 1, 1, 'data-service:runtime', '数据服务 API 调试', '${appName}'),
('data-service-overview', '运行概览', 'data-service', '/data-service/overview', 'monitor', 2,
 30, 1, 1, 'data-service:observe', '数据服务运行概览', '${appName}'),
('data-service-logs', '调用记录', 'data-service', '/data-service/logs', 'report', 2,
 40, 1, 1, 'data-service:observe', '数据服务调用记录', '${appName}')
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

-- 4. Existing action grants continue to imply their owning page menu after reconciliation.
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

-- A page's declared read/runtime requirement also repairs role-menu relations. This covers
-- pages that intentionally share one read permission, such as quality overview/execution,
-- Data Development read-only pages, and Data Service overview/logs.
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
JOIN yak_security_menu menu_row
  ON menu_row.required_permission_code = permission_row.permission_code
 AND menu_row.app_name = role_permission.app_name
 AND menu_row.is_delete = 0
 AND menu_row.active = 1
WHERE role_permission.app_name = '${appName}'
  AND role_permission.is_delete = 0
ON DUPLICATE KEY UPDATE is_delete = 0;

-- 5. Any granted child menu implies its current parent group.
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

-- 6. Root administrators retain the complete reconciled Yak Ops business menu catalog.
INSERT INTO yak_security_role_menu(role_id, menu_id, app_name)
SELECT DISTINCT root_permission.role_id,
                menu_row.id,
                root_permission.app_name
FROM yak_security_role_permission root_permission
JOIN yak_security_permission root_row
  ON root_row.id = root_permission.permission_id
 AND root_row.app_name = root_permission.app_name
 AND root_row.permission_code = 'security:root'
 AND root_row.is_delete = 0
JOIN yak_security_menu menu_row
  ON menu_row.app_name = root_permission.app_name
 AND menu_row.is_delete = 0
 AND menu_row.active = 1
WHERE root_permission.app_name = '${appName}'
  AND root_permission.is_delete = 0
ON DUPLICATE KEY UPDATE is_delete = 0;
