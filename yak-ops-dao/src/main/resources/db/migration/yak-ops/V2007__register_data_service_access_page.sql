-- Give Data Service access management its own visible capability page.
--
-- V2005/V2006 already define data-service:access, but historically bind it to API Marketplace.
-- Keep those migrations immutable and repair the catalog with this forward migration.

INSERT INTO yak_security_menu
(menu_code, menu_name, parent_code, route_path, icon_key, menu_type,
 sort_order, visible, active, required_permission_code, description, app_name)
VALUES
('data-service-access', '访问控制', 'data-service', '/data-service/access', 'api', 2,
 20, 1, 1, 'data-service:access', '数据服务认证、API Key 与 IP/CIDR 来源访问控制', '${appName}')
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

UPDATE yak_security_permission
SET permission_name = '管理数据服务访问控制',
    description = '配置认证方式、API Key、调用配额与 IP/CIDR 来源访问策略',
    menu_code = 'data-service-access',
    active = 1,
    is_delete = 0
WHERE permission_code = 'data-service:access'
  AND app_name = '${appName}';

-- Keep visible Data Service children in the same order as frontend navigation.
UPDATE yak_security_menu
SET sort_order = CASE menu_code
    WHEN 'data-service-debug' THEN 30
    WHEN 'data-service-overview' THEN 40
    WHEN 'data-service-logs' THEN 50
    ELSE sort_order
END
WHERE app_name = '${appName}'
  AND parent_code = 'data-service'
  AND menu_code IN ('data-service-debug', 'data-service-overview', 'data-service-logs')
  AND is_delete = 0;

-- Existing roles that already own data-service:access receive the new page automatically.
INSERT INTO yak_security_role_menu(role_id, menu_id, app_name)
SELECT DISTINCT role_permission.role_id,
                access_menu.id,
                role_permission.app_name
FROM yak_security_role_permission role_permission
JOIN yak_security_permission permission_row
  ON permission_row.id = role_permission.permission_id
 AND permission_row.permission_code = 'data-service:access'
 AND permission_row.app_name = role_permission.app_name
 AND permission_row.is_delete = 0
 AND permission_row.active = 1
JOIN yak_security_menu access_menu
  ON access_menu.menu_code = 'data-service-access'
 AND access_menu.app_name = role_permission.app_name
 AND access_menu.is_delete = 0
 AND access_menu.active = 1
WHERE role_permission.app_name = '${appName}'
  AND role_permission.is_delete = 0
ON DUPLICATE KEY UPDATE is_delete = 0;

-- A role with a Data Service child page must also be able to see the parent group.
INSERT INTO yak_security_role_menu(role_id, menu_id, app_name)
SELECT DISTINCT child_role_menu.role_id,
                parent_menu.id,
                child_role_menu.app_name
FROM yak_security_role_menu child_role_menu
JOIN yak_security_menu child_menu
  ON child_menu.id = child_role_menu.menu_id
 AND child_menu.menu_code = 'data-service-access'
 AND child_menu.app_name = child_role_menu.app_name
 AND child_menu.is_delete = 0
JOIN yak_security_menu parent_menu
  ON parent_menu.menu_code = 'data-service'
 AND parent_menu.app_name = child_role_menu.app_name
 AND parent_menu.is_delete = 0
 AND parent_menu.active = 1
WHERE child_role_menu.app_name = '${appName}'
  AND child_role_menu.is_delete = 0
ON DUPLICATE KEY UPDATE is_delete = 0;
