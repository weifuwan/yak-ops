-- 注册数据质量规则模板库页面及其最小读取权限。
-- 当前阶段只提供前端静态页面，不恢复历史数据质量执行与报告模块。

-- 1. 恢复独立的数据质量权限分组。
INSERT INTO yak_security_permission
(permission_code, permission_name, parent_id, leaf, level, description,
 active, declared, menu_code, app_name)
VALUES
('quality', '数据质量', 0, 0, 1, 'Yak Ops 数据质量页面权限',
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

-- 2. 规则模板库只注册页面读取权限。
INSERT INTO yak_security_permission
(permission_code, permission_name, parent_id, leaf, level, description,
 active, declared, menu_code, app_name)
SELECT 'quality:template:read',
       '查看规则模板库',
       parent.id,
       1,
       2,
       '查看数据质量规则模板库页面',
       1,
       0,
       'data-quality-rule-template',
       parent.app_name
FROM yak_security_permission parent
WHERE parent.permission_code = 'quality'
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

-- 3. 菜单层级：数据质量 -> 规则模板库。
-- 数据质量与资源管理同为一级目录。
INSERT INTO yak_security_menu
(menu_code, menu_name, parent_code, route_path, icon_key, menu_type,
 sort_order, visible, active, required_permission_code, description, app_name)
VALUES
('data-quality', '数据质量', NULL, NULL, 'quality', 1,
 30, 1, 1, NULL, '数据质量页面入口', '${appName}'),
('data-quality-rule-template', '规则模板库', 'data-quality',
 '/data-quality/rule-template', 'quality', 2,
 10, 1, 1, 'quality:template:read', '数据质量规则模板库', '${appName}')
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

-- 4. 已拥有规则模板读取权限的角色自动获得页面菜单。
INSERT INTO yak_security_role_menu(role_id, menu_id, app_name)
SELECT DISTINCT role_permission.role_id,
                menu_row.id,
                role_permission.app_name
FROM yak_security_role_permission role_permission
JOIN yak_security_permission permission_row
  ON permission_row.id = role_permission.permission_id
 AND permission_row.app_name = role_permission.app_name
 AND permission_row.permission_code = 'quality:template:read'
 AND permission_row.is_delete = 0
 AND permission_row.active = 1
JOIN yak_security_menu menu_row
  ON menu_row.menu_code = 'data-quality-rule-template'
 AND menu_row.app_name = role_permission.app_name
 AND menu_row.is_delete = 0
 AND menu_row.active = 1
WHERE role_permission.app_name = '${appName}'
  AND role_permission.is_delete = 0
ON DUPLICATE KEY UPDATE is_delete = 0;

-- 5. 回填规则模板库所属的数据质量一级目录。
INSERT INTO yak_security_role_menu(role_id, menu_id, app_name)
SELECT DISTINCT role_menu.role_id,
                parent_menu.id,
                role_menu.app_name
FROM yak_security_role_menu role_menu
JOIN yak_security_menu child_menu
  ON child_menu.id = role_menu.menu_id
 AND child_menu.menu_code = 'data-quality-rule-template'
 AND child_menu.app_name = role_menu.app_name
 AND child_menu.is_delete = 0
JOIN yak_security_menu parent_menu
  ON parent_menu.menu_code = 'data-quality'
 AND parent_menu.app_name = role_menu.app_name
 AND parent_menu.is_delete = 0
 AND parent_menu.active = 1
WHERE role_menu.app_name = '${appName}'
  AND role_menu.is_delete = 0
ON DUPLICATE KEY UPDATE is_delete = 0;

-- 6. root 角色直接获得新增的数据质量目录和页面菜单。
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
  ON menu_row.menu_code IN ('data-quality', 'data-quality-rule-template')
 AND menu_row.app_name = role_permission.app_name
 AND menu_row.is_delete = 0
 AND menu_row.active = 1
WHERE role_permission.app_name = '${appName}'
  AND role_permission.is_delete = 0
ON DUPLICATE KEY UPDATE is_delete = 0;
