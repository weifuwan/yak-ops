-- The standalone Data Service access page is now caller-centric.
-- Keep the stable menu code, route and permission so existing role assignments remain valid.

UPDATE yak_security_menu
SET menu_name = 'API 调用',
    description = '集中管理数据服务调用方、API Key、API 权限与 IP/CIDR 来源策略'
WHERE menu_code = 'data-service-access'
  AND app_name = '${appName}'
  AND is_delete = 0;

UPDATE yak_security_permission
SET permission_name = '管理数据服务 API 调用',
    description = '集中管理调用方、API Key、API 授权、调用配额与 IP/CIDR 来源策略'
WHERE permission_code = 'data-service:access'
  AND app_name = '${appName}'
  AND is_delete = 0;
