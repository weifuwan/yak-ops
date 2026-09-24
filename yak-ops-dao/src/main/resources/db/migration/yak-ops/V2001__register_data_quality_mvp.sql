-- 数据质量第一阶段：按表配置、质量监控、手动执行、执行记录。

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
JOIN (
    SELECT 'quality:monitor:read' AS permission_code,
           '查看质量监控' AS permission_name,
           '查看按表配置及质量监控详情' AS description,
           'data-quality-table-config' AS menu_code
    UNION ALL
    SELECT 'quality:monitor:create', '创建质量监控', '创建表级质量监控', NULL
    UNION ALL
    SELECT 'quality:monitor:update', '更新质量监控', '更新质量监控及其规则', NULL
    UNION ALL
    SELECT 'quality:monitor:delete', '删除质量监控', '删除质量监控', NULL
    UNION ALL
    SELECT 'quality:monitor:run', '运行质量监控', '手动运行质量监控', NULL
    UNION ALL
    SELECT 'quality:execution:read', '查看质量运行记录', '查看质量监控执行记录与结果',
           'data-quality-execution'
) permissions
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

INSERT INTO yak_security_menu
(menu_code, menu_name, parent_code, route_path, icon_key, menu_type,
 sort_order, visible, active, required_permission_code, description, app_name)
VALUES
('data-quality-table-config', '按表配置', 'data-quality',
 '/data-quality/table-config', 'quality', 2,
 10, 1, 1, 'quality:monitor:read', '按数据源和数据表创建质量监控', '${appName}'),
('data-quality-execution', '运行记录', 'data-quality',
 '/data-quality/execution', 'report', 2,
 20, 1, 1, 'quality:execution:read', '数据质量手动执行记录', '${appName}')
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

UPDATE yak_security_menu
SET sort_order = 30,
    is_delete = 0,
    active = 1
WHERE menu_code = 'data-quality-rule-template'
  AND app_name = '${appName}';

-- 已拥有对应读取权限的角色自动获得菜单。
INSERT INTO yak_security_role_menu(role_id, menu_id, app_name)
SELECT DISTINCT role_permission.role_id,
                menu_row.id,
                role_permission.app_name
FROM yak_security_role_permission role_permission
JOIN yak_security_permission permission_row
  ON permission_row.id = role_permission.permission_id
 AND permission_row.app_name = role_permission.app_name
 AND permission_row.permission_code IN (
      'quality:monitor:read',
      'quality:execution:read')
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

-- 补齐数据质量父目录菜单。
INSERT INTO yak_security_role_menu(role_id, menu_id, app_name)
SELECT DISTINCT role_menu.role_id,
                parent_menu.id,
                role_menu.app_name
FROM yak_security_role_menu role_menu
JOIN yak_security_menu child_menu
  ON child_menu.id = role_menu.menu_id
 AND child_menu.parent_code = 'data-quality'
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

-- root 角色获得新增权限和菜单。
INSERT INTO yak_security_role_permission(role_id, permission_id, app_name)
SELECT DISTINCT root_permission.role_id,
                quality_permission.id,
                root_permission.app_name
FROM yak_security_role_permission root_permission
JOIN yak_security_permission root_row
  ON root_row.id = root_permission.permission_id
 AND root_row.permission_code = 'security:root'
 AND root_row.app_name = root_permission.app_name
 AND root_row.is_delete = 0
JOIN yak_security_permission quality_permission
  ON quality_permission.permission_code LIKE 'quality:%'
 AND quality_permission.app_name = root_permission.app_name
 AND quality_permission.is_delete = 0
 AND quality_permission.active = 1
WHERE root_permission.app_name = '${appName}'
  AND root_permission.is_delete = 0
ON DUPLICATE KEY UPDATE is_delete = 0;

INSERT INTO yak_security_role_menu(role_id, menu_id, app_name)
SELECT DISTINCT root_permission.role_id,
                menu_row.id,
                root_permission.app_name
FROM yak_security_role_permission root_permission
JOIN yak_security_permission root_row
  ON root_row.id = root_permission.permission_id
 AND root_row.permission_code = 'security:root'
 AND root_row.app_name = root_permission.app_name
 AND root_row.is_delete = 0
JOIN yak_security_menu menu_row
  ON menu_row.menu_code IN (
      'data-quality',
      'data-quality-table-config',
      'data-quality-execution',
      'data-quality-rule-template')
 AND menu_row.app_name = root_permission.app_name
 AND menu_row.is_delete = 0
 AND menu_row.active = 1
WHERE root_permission.app_name = '${appName}'
  AND root_permission.is_delete = 0
ON DUPLICATE KEY UPDATE is_delete = 0;
