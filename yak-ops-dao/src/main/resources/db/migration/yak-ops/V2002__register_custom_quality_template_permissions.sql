-- 自定义规则模板目录与模板的写权限。

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
       NULL,
       parent.app_name
FROM yak_security_permission parent
JOIN (
    SELECT 'quality:template:create' AS permission_code,
           '创建自定义规则模板' AS permission_name,
           '创建自定义规则模板及模板目录' AS description
    UNION ALL
    SELECT 'quality:template:update',
           '更新自定义规则模板',
           '编辑、复制和移动自定义规则模板及模板目录'
    UNION ALL
    SELECT 'quality:template:delete',
           '删除自定义规则模板',
           '删除自定义规则模板及空目录'
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
is_delete = 0;

-- root 角色始终获得新增权限。
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
  ON quality_permission.permission_code IN (
      'quality:template:create',
      'quality:template:update',
      'quality:template:delete')
 AND quality_permission.app_name = root_permission.app_name
 AND quality_permission.is_delete = 0
 AND quality_permission.active = 1
WHERE root_permission.app_name = '${appName}'
  AND root_permission.is_delete = 0
ON DUPLICATE KEY UPDATE is_delete = 0;
