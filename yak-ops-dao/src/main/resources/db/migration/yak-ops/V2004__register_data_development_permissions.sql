-- Data Development becomes an explicit RBAC capability in Stage 2.
-- Keep this catalog independent from Project Space membership: both checks must pass.

INSERT INTO yak_security_permission
(permission_code, permission_name, parent_id, leaf, level, description,
 active, declared, menu_code, app_name)
VALUES
('data-development', '数据开发', 0, 0, 1, 'Yak Ops 数据开发权限',
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

INSERT INTO yak_security_permission
(permission_code, permission_name, parent_id, leaf, level, description,
 active, declared, menu_code, app_name)
SELECT permissions.permission_code,
       permissions.permission_name,
       parent.id,
       1,
       2,
       permissions.description,
       1,
       0,
       permissions.menu_code,
       parent.app_name
FROM yak_security_permission parent
CROSS JOIN (
    SELECT 'data-development:read' AS permission_code,
           '查看数据开发' AS permission_name,
           '查看开发目录、节点、草稿、版本、运行记录和发布中心' AS description,
           'data-development' AS menu_code
    UNION ALL SELECT 'data-development:edit', '编辑数据开发',
                     '创建、重命名和编辑开发目录、节点、草稿、Dataset、Data Service 与编辑器设置',
                     'data-development'
    UNION ALL SELECT 'data-development:delete', '删除数据开发资源',
                     '删除数据开发目录和节点', 'data-development'
    UNION ALL SELECT 'data-development:execute', '执行数据开发任务',
                     '运行任务、执行 Dataset/Data Service 预览以及取消和重试运行实例',
                     'data-development-execution'
    UNION ALL SELECT 'data-development:publish', '发布数据开发版本',
                     '发布不可变 Task/Data Service Revision', 'data-development-release'
    UNION ALL SELECT 'data-development:release', '管理数据开发上线状态',
                     '上线、下线和切换已发布任务版本', 'data-development-release'
) permissions
WHERE parent.permission_code = 'data-development'
  AND parent.app_name = '${appName}'
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

INSERT INTO yak_security_menu
(menu_code, menu_name, parent_code, route_path, icon_key, menu_type,
 sort_order, visible, active, required_permission_code, description, app_name)
VALUES
('development', '数据开发', NULL, NULL, 'api', 1,
 20, 1, 1, NULL, '数据开发工作台入口', '${appName}'),
('data-development', '开发任务', 'development', '/data-development', 'api', 2,
 10, 1, 1, 'data-development:read', '数据开发工作台', '${appName}'),
('data-development-release', '发布中心', 'development', '/data-development/releases', 'report', 2,
 20, 1, 1, 'data-development:read', '数据开发发布中心', '${appName}'),
('data-development-execution', '运行记录', 'development', '/data-development/executions', 'report', 2,
 30, 1, 1, 'data-development:read', '数据开发运行记录', '${appName}')
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

-- Only root administrators keep implicit access during the cutover. Other roles must receive
-- Data Development permissions explicitly through the existing Role & Permission UI.
INSERT INTO yak_security_role_permission(role_id, permission_id, app_name)
SELECT DISTINCT root_permission.role_id,
                development_permission.id,
                root_permission.app_name
FROM yak_security_role_permission root_permission
JOIN yak_security_permission root_row
  ON root_row.id = root_permission.permission_id
 AND root_row.permission_code = 'security:root'
 AND root_row.app_name = root_permission.app_name
 AND root_row.is_delete = 0
JOIN yak_security_permission development_permission
  ON development_permission.permission_code LIKE 'data-development:%'
 AND development_permission.app_name = root_permission.app_name
 AND development_permission.is_delete = 0
 AND development_permission.active = 1
WHERE root_permission.app_name = '${appName}'
  AND root_permission.is_delete = 0
ON DUPLICATE KEY UPDATE is_delete = 0;

-- Any role with a Data Development leaf permission receives the corresponding page menu.
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
 AND permission_row.permission_code LIKE 'data-development:%'
 AND permission_row.menu_code IS NOT NULL
JOIN yak_security_menu menu_row
  ON menu_row.menu_code = permission_row.menu_code
 AND menu_row.app_name = role_permission.app_name
 AND menu_row.is_delete = 0
 AND menu_row.active = 1
WHERE role_permission.app_name = '${appName}'
  AND role_permission.is_delete = 0
ON DUPLICATE KEY UPDATE is_delete = 0;

-- Ensure the Data Development parent group is visible for roles that can see one child page.
INSERT INTO yak_security_role_menu(role_id, menu_id, app_name)
SELECT DISTINCT child_role_menu.role_id,
                parent_menu.id,
                child_role_menu.app_name
FROM yak_security_role_menu child_role_menu
JOIN yak_security_menu child_menu
  ON child_menu.id = child_role_menu.menu_id
 AND child_menu.app_name = child_role_menu.app_name
 AND child_menu.parent_code = 'development'
 AND child_menu.is_delete = 0
JOIN yak_security_menu parent_menu
  ON parent_menu.menu_code = 'development'
 AND parent_menu.app_name = child_role_menu.app_name
 AND parent_menu.is_delete = 0
 AND parent_menu.active = 1
WHERE child_role_menu.app_name = '${appName}'
  AND child_role_menu.is_delete = 0
ON DUPLICATE KEY UPDATE is_delete = 0;
