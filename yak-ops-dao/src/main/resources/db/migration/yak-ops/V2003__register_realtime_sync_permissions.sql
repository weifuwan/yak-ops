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
       'realtime-sync',
       parent.app_name
FROM yak_security_permission parent
JOIN (
    SELECT 'task:realtime:read' AS permission_code,
           '查看实时同步' AS permission_name,
           '查看实时同步页面、任务和运行信息' AS description
    UNION ALL SELECT 'task:realtime:create', '新建实时同步', '创建实时同步任务草稿'
    UNION ALL SELECT 'task:realtime:update', '编辑实时同步', '编辑、校验和发布实时同步任务'
    UNION ALL SELECT 'task:realtime:delete', '删除实时同步', '删除已停止的实时同步任务'
    UNION ALL SELECT 'task:realtime:execute', '执行实时同步', '启动、停止和重启实时同步任务'
) permissions
WHERE parent.permission_code = 'task'
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
('realtime-sync', '实时同步', 'integration', '/sync/realtime', 'realtime', 2,
 20, 1, 1, 'task:realtime:read', 'MySQL CDC 实时同步管理', '${appName}')
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

INSERT INTO yak_security_role_permission(role_id, permission_id, app_name)
SELECT DISTINCT root_permission.role_id,
                realtime_permission.id,
                root_permission.app_name
FROM yak_security_role_permission root_permission
JOIN yak_security_permission root_row
  ON root_row.id = root_permission.permission_id
 AND root_row.permission_code = 'security:root'
 AND root_row.app_name = root_permission.app_name
 AND root_row.is_delete = 0
JOIN yak_security_permission realtime_permission
  ON realtime_permission.permission_code LIKE 'task:realtime:%'
 AND realtime_permission.app_name = root_permission.app_name
 AND realtime_permission.is_delete = 0
 AND realtime_permission.active = 1
WHERE root_permission.app_name = '${appName}'
  AND root_permission.is_delete = 0
ON DUPLICATE KEY UPDATE is_delete = 0;

INSERT INTO yak_security_role_menu(role_id, menu_id, app_name)
SELECT DISTINCT root_permission.role_id,
                realtime_menu.id,
                root_permission.app_name
FROM yak_security_role_permission root_permission
JOIN yak_security_permission root_row
  ON root_row.id = root_permission.permission_id
 AND root_row.permission_code = 'security:root'
 AND root_row.app_name = root_permission.app_name
 AND root_row.is_delete = 0
JOIN yak_security_menu realtime_menu
  ON realtime_menu.menu_code = 'realtime-sync'
 AND realtime_menu.app_name = root_permission.app_name
 AND realtime_menu.is_delete = 0
WHERE root_permission.app_name = '${appName}'
  AND root_permission.is_delete = 0
ON DUPLICATE KEY UPDATE is_delete = 0;
