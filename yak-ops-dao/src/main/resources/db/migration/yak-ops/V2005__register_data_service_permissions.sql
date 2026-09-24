-- Data Service Stage 2: Project membership and RBAC are independent gates for the management plane.
-- The external /api/v1/data-service/runtime/** invocation plane is deliberately not a Yak console menu/permission.

INSERT INTO yak_security_permission
(permission_code, permission_name, parent_id, leaf, level, description,
 active, declared, menu_code, app_name)
VALUES
('data-service', '数据服务', 0, 0, 1, 'Yak Ops 数据服务管理权限',
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
    SELECT 'data-service:read' AS permission_code,
           '查看数据服务' AS permission_name,
           '查看 API 集市、服务详情、契约文档与 OpenAPI' AS description,
           'data-service-api' AS menu_code
    UNION ALL SELECT 'data-service:publish', '发布数据服务',
                     '查询可发布来源、发布和重新发布 Data Service',
                     'data-service-api'
    UNION ALL SELECT 'data-service:manage', '管理数据服务',
                     '修改服务配置、启停状态和可编辑契约文档',
                     'data-service-api'
    UNION ALL SELECT 'data-service:delete', '删除数据服务',
                     '删除非 source-managed Data Service',
                     'data-service-api'
    UNION ALL SELECT 'data-service:access', '管理数据服务访问控制',
                     '配置鉴权模式以及创建、轮换、启停和删除 API Key',
                     'data-service-api'
    UNION ALL SELECT 'data-service:runtime', '管理数据服务 Runtime',
                     '查看或修改 Runtime 策略并执行控制台调试',
                     'data-service-debug'
    UNION ALL SELECT 'data-service:observe', '查看数据服务运行观测',
                     '查看运行概览和调用记录',
                     'data-service-overview'
) permissions
WHERE parent.permission_code = 'data-service'
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

-- Only root administrators retain implicit Data Service console access at cutover.
INSERT INTO yak_security_role_permission(role_id, permission_id, app_name)
SELECT DISTINCT root_permission.role_id,
                data_service_permission.id,
                root_permission.app_name
FROM yak_security_role_permission root_permission
JOIN yak_security_permission root_row
  ON root_row.id = root_permission.permission_id
 AND root_row.permission_code = 'security:root'
 AND root_row.app_name = root_permission.app_name
 AND root_row.is_delete = 0
JOIN yak_security_permission data_service_permission
  ON data_service_permission.permission_code LIKE 'data-service:%'
 AND data_service_permission.app_name = root_permission.app_name
 AND data_service_permission.is_delete = 0
 AND data_service_permission.active = 1
WHERE root_permission.app_name = '${appName}'
  AND root_permission.is_delete = 0
ON DUPLICATE KEY UPDATE is_delete = 0;

-- Leaf permissions grant the primary page menu associated with that capability.
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
 AND permission_row.permission_code LIKE 'data-service:%'
 AND permission_row.menu_code IS NOT NULL
JOIN yak_security_menu menu_row
  ON menu_row.menu_code = permission_row.menu_code
 AND menu_row.app_name = role_permission.app_name
 AND menu_row.is_delete = 0
 AND menu_row.active = 1
WHERE role_permission.app_name = '${appName}'
  AND role_permission.is_delete = 0
ON DUPLICATE KEY UPDATE is_delete = 0;

-- Observe grants both overview and call-log pages.
INSERT INTO yak_security_role_menu(role_id, menu_id, app_name)
SELECT DISTINCT role_permission.role_id,
                menu_row.id,
                role_permission.app_name
FROM yak_security_role_permission role_permission
JOIN yak_security_permission permission_row
  ON permission_row.id = role_permission.permission_id
 AND permission_row.permission_code = 'data-service:observe'
 AND permission_row.app_name = role_permission.app_name
 AND permission_row.is_delete = 0
 AND permission_row.active = 1
JOIN yak_security_menu menu_row
  ON menu_row.menu_code = 'data-service-logs'
 AND menu_row.app_name = role_permission.app_name
 AND menu_row.is_delete = 0
 AND menu_row.active = 1
WHERE role_permission.app_name = '${appName}'
  AND role_permission.is_delete = 0
ON DUPLICATE KEY UPDATE is_delete = 0;

-- Ensure the Data Service parent group is visible when any child page is available.
INSERT INTO yak_security_role_menu(role_id, menu_id, app_name)
SELECT DISTINCT child_role_menu.role_id,
                parent_menu.id,
                child_role_menu.app_name
FROM yak_security_role_menu child_role_menu
JOIN yak_security_menu child_menu
  ON child_menu.id = child_role_menu.menu_id
 AND child_menu.app_name = child_role_menu.app_name
 AND child_menu.parent_code = 'data-service'
 AND child_menu.is_delete = 0
JOIN yak_security_menu parent_menu
  ON parent_menu.menu_code = 'data-service'
 AND parent_menu.app_name = child_role_menu.app_name
 AND parent_menu.is_delete = 0
 AND parent_menu.active = 1
WHERE child_role_menu.app_name = '${appName}'
  AND child_role_menu.is_delete = 0
ON DUPLICATE KEY UPDATE is_delete = 0;
