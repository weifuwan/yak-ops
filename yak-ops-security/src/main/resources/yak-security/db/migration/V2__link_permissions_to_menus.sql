-- Yak Security 基础表结构迁移必须先于宿主应用的业务权限目录执行。
-- 为权限增加所属菜单编码，明确“菜单控制页面、按钮控制操作”的包含关系。
ALTER TABLE yak_security_permission
  ADD COLUMN menu_code VARCHAR(128) NULL COMMENT '所属稳定菜单编码；按钮权限会自动包含该菜单访问能力' AFTER description;

CREATE INDEX idx_permission_app_menu
  ON yak_security_permission(app_name, menu_code, active, is_delete);

-- Yak Security 自有系统权限与系统菜单绑定。
UPDATE yak_security_permission
SET menu_code='system-users'
WHERE app_name='${appName}'
  AND is_delete=0
  AND permission_code LIKE 'security:user:%';

UPDATE yak_security_permission
SET menu_code='system-roles'
WHERE app_name='${appName}'
  AND is_delete=0
  AND permission_code LIKE 'security:role:%';

UPDATE yak_security_permission
SET menu_code='system-permissions'
WHERE app_name='${appName}'
  AND is_delete=0
  AND permission_code LIKE 'security:permission:%';

UPDATE yak_security_permission
SET menu_code='system-departments'
WHERE app_name='${appName}'
  AND is_delete=0
  AND permission_code LIKE 'security:department:%';

UPDATE yak_security_permission
SET menu_code='system-security-projects'
WHERE app_name='${appName}'
  AND is_delete=0
  AND permission_code LIKE 'security:project:%';

UPDATE yak_security_permission
SET menu_code='system-resource-permissions'
WHERE app_name='${appName}'
  AND is_delete=0
  AND permission_code LIKE 'security:resource-permission:%';

UPDATE yak_security_permission
SET menu_code='system-configs'
WHERE app_name='${appName}'
  AND is_delete=0
  AND permission_code LIKE 'security:config:%';

UPDATE yak_security_permission
SET menu_code='system-operation-logs'
WHERE app_name='${appName}'
  AND is_delete=0
  AND permission_code LIKE 'security:operation-log:%';

-- 已有角色如果具备页面下任一按钮权限，自动补齐对应菜单。
INSERT IGNORE INTO yak_security_role_menu(role_id,menu_id,app_name)
SELECT DISTINCT role_permission.role_id,menu_row.id,role_permission.app_name
FROM yak_security_role_permission role_permission
JOIN yak_security_permission permission_row
  ON permission_row.id=role_permission.permission_id
 AND permission_row.app_name=role_permission.app_name
 AND permission_row.is_delete=0
 AND permission_row.active=1
 AND permission_row.menu_code IS NOT NULL
JOIN yak_security_menu menu_row
  ON menu_row.menu_code=permission_row.menu_code
 AND menu_row.app_name=role_permission.app_name
 AND menu_row.is_delete=0
 AND menu_row.active=1
WHERE role_permission.app_name='${appName}'
  AND role_permission.is_delete=0;

-- 补齐按钮所属菜单的父级目录。
INSERT IGNORE INTO yak_security_role_menu(role_id,menu_id,app_name)
SELECT DISTINCT role_menu.role_id,parent_menu.id,role_menu.app_name
FROM yak_security_role_menu role_menu
JOIN yak_security_menu child_menu
  ON child_menu.id=role_menu.menu_id
 AND child_menu.app_name=role_menu.app_name
 AND child_menu.is_delete=0
JOIN yak_security_menu parent_menu
  ON parent_menu.menu_code=child_menu.parent_code
 AND parent_menu.app_name=role_menu.app_name
 AND parent_menu.is_delete=0
 AND parent_menu.active=1
WHERE role_menu.app_name='${appName}'
  AND role_menu.is_delete=0
  AND child_menu.parent_code IS NOT NULL;
