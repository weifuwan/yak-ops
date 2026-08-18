import {
  type PermissionRequirement,
  satisfiesPermissionRequirement,
} from '../utils/security/permission';

export type NavigationIconKey =
  | 'home' | 'database' | 'sync' | 'realtime' | 'client' | 'connector'
  | 'workflow' | 'project' | 'instance' | 'quality' | 'report' | 'monitor' | 'alarm'
  | 'knowledge' | 'api' | 'insight' | 'system';
export type NavigationSectionKey = 'task' | 'management' | 'system';

interface NavigationRouteBase {
  id: string; path: string; title: string; component: string;
  iconKey?: NavigationIconKey; menuGroup?: string; order?: number;
  hidden?: boolean; parentId?: string; quickCreateLabel?: string;
  quickCreateRequirement?: PermissionRequirement;
}
export type NavigationRoute = NavigationRouteBase &
  (PermissionRequirement | { parentId: string; mode?: never });
export interface NavigationGroup {
  id: string; title: string; iconKey: NavigationIconKey;
  section: NavigationSectionKey; order: number;
}
export interface NavigationGroupWithRoutes extends NavigationGroup {
  routes: NavigationRoute[];
}

export const navigationGroups: readonly NavigationGroup[] = [
  { id: 'integration', title: '数据集成', iconKey: 'sync', section: 'task', order: 10 },
  { id: 'development', title: '数据开发', iconKey: 'api', section: 'task', order: 20 },
  { id: 'workflow', title: '工作流', iconKey: 'workflow', section: 'task', order: 30 },
  { id: 'resources', title: '资源管理', iconKey: 'database', section: 'management', order: 20 },
  { id: 'data-quality', title: '数据质量', iconKey: 'quality', section: 'management', order: 30 },
  { id: 'data-analysis', title: '数据消费', iconKey: 'insight', section: 'management', order: 40 },
  { id: 'data-service', title: '数据服务', iconKey: 'api', section: 'management', order: 50 },
  { id: 'system', title: '系统管理', iconKey: 'system', section: 'system', order: 40 },
];

export const appRoutes: readonly NavigationRoute[] = [
  { id: 'home', mode: 'public', path: '/home', title: '首页', component: './home', iconKey: 'home', order: 0 },
  { id: 'data-source', mode: 'one', permission: 'resource:data-source:read', path: '/data-source', title: '数据源管理', component: './data-source', iconKey: 'database', order: 10 },
  { id: 'dashboard', mode: 'public', path: '/dashboard', title: '仪表盘', component: './dashboard', iconKey: 'insight', menuGroup: 'data-analysis', order: 10 },
  { id: 'dashboard-new', path: '/dashboard/new', title: '新建仪表盘', component: './dashboard/editor', hidden: true, parentId: 'dashboard' },
  { id: 'dashboard-editor', path: '/dashboard/:id/edit', title: '仪表盘编辑', component: './dashboard/editor', hidden: true, parentId: 'dashboard' },
  { id: 'dashboard-viewer', path: '/dashboard/:id', title: '仪表盘查看', component: './dashboard/viewer', hidden: true, parentId: 'dashboard' },
  { id: 'data-analysis-catalog', mode: 'public', path: '/data-analysis/data-catalog', title: '数据目录', component: './data-analysis/data-catalog', iconKey: 'database', menuGroup: 'data-analysis', order: 20 },
  { id: 'digital-screen', mode: 'public', path: '/digital-screen', title: '数字化大屏', component: './digital-screen', iconKey: 'insight', menuGroup: 'data-analysis', order: 30 },
  { id: 'digital-screen-new', path: '/digital-screen/new', title: '新建数字化大屏', component: './digital-screen/new', hidden: true, parentId: 'digital-screen' },
  { id: 'digital-screen-editor', path: '/digital-screen/:id/edit', title: '数字化大屏编辑', component: './digital-screen/editor', hidden: true, parentId: 'digital-screen' },
  { id: 'digital-screen-viewer', path: '/digital-screen/:id', title: '数字化大屏预览', component: './digital-screen/viewer', hidden: true, parentId: 'digital-screen' },
  { id: 'data-analysis-chart', path: '/data-analysis/chart-analysis', title: '图表分析', component: './data-analysis/chart-analysis-redirect', hidden: true, parentId: 'dashboard' },
  { id: 'data-service-api', mode: 'public', path: '/data-service', title: 'API 集市', component: './data-service', iconKey: 'api', menuGroup: 'data-service', order: 10 },
  { id: 'data-service-api-detail', path: '/data-service/api/:id', title: 'API 详情', component: './data-service/detail', hidden: true, parentId: 'data-service-api' },
  { id: 'data-service-debug', mode: 'public', path: '/data-service/debug', title: 'API 调试', component: './data-service/debug', iconKey: 'api', menuGroup: 'data-service', order: 20 },
  { id: 'data-service-overview', mode: 'public', path: '/data-service/overview', title: '运行概览', component: './data-service/overview', iconKey: 'monitor', menuGroup: 'data-service', order: 30 },
  { id: 'data-service-logs', mode: 'public', path: '/data-service/logs', title: '调用记录', component: './data-service/logs', iconKey: 'report', menuGroup: 'data-service', order: 40 },
  { id: 'settings', mode: 'public', path: '/settings', title: '设置', component: './settings', hidden: true, order: 30 },
  {
    id: 'batch-link-up', mode: 'one', permission: 'task:batch:read',
    path: '/sync/batch-link-up', title: '离线同步', component: './batch-link-up',
    iconKey: 'sync', menuGroup: 'integration', order: 10,
    quickCreateRequirement: { mode: 'one', permission: 'task:batch:create' },
    quickCreateLabel: '新建离线同步',
  },
  { id: 'batch-link-up-detail', path: '/sync/batch-link-up/:id/detail', title: '离线同步详情', component: './batch-link-up/detail', hidden: true, parentId: 'batch-link-up' },
  { id: 'batch-link-up-single', path: '/sync/batch-link-up/:id/config/single', title: '单表同步配置', component: './batch-link-up/config/single', hidden: true, parentId: 'batch-link-up' },
  { id: 'batch-link-up-multi', path: '/sync/batch-link-up/:id/config/multi', title: '多表同步配置', component: './batch-link-up/config/multi', hidden: true, parentId: 'batch-link-up' },
  { id: 'batch-link-up-script', path: '/sync/batch-link-up/:id/config/script', title: '脚本同步配置', component: './batch-link-up/config/script', hidden: true, parentId: 'batch-link-up' },
  { id: 'data-development', mode: 'public', path: '/data-development', title: '开发任务', component: './data-development', iconKey: 'api', menuGroup: 'development', order: 10 },
  { id: 'data-development-release', mode: 'public', path: '/data-development/releases', title: '发布中心', component: './data-development/releases', iconKey: 'report', menuGroup: 'development', order: 20 },
  { id: 'data-development-execution', mode: 'public', path: '/data-development/executions', title: '运行记录', component: './data-development/executions', iconKey: 'report', menuGroup: 'development', order: 30 },
  { id: 'data-development-task-new', path: '/data-development/task/new', title: '新建开发任务', component: './data-development/task', hidden: true, parentId: 'data-development' },
  { id: 'data-development-task', path: '/data-development/task/:id', title: '开发任务配置', component: './data-development/task', hidden: true, parentId: 'data-development' },
  { id: 'workflow-definition', mode: 'public', path: '/workflow/definitions', title: '工作流定义', component: './workflow/management', iconKey: 'workflow', menuGroup: 'workflow', order: 10 },
  { id: 'workflow-definition-editor', path: '/workflow/definition/:id', title: '工作流配置', component: './workflow/definition', hidden: true, parentId: 'workflow-definition' },
  { id: 'workflow-schedules', path: '/workflow/schedules', title: '调度配置', component: './workflow/schedules', hidden: true, parentId: 'workflow-definition' },
  { id: 'workflow-instances', mode: 'public', path: '/workflow/instances', title: '工作流实例', component: './workflow/instances', iconKey: 'instance', menuGroup: 'workflow', order: 30 },
  { id: 'workflow-instance-detail', path: '/workflow/instances/:executionId', title: '工作流实例详情', component: './workflow/instances/detail', hidden: true, parentId: 'workflow-instances' },
  { id: 'resource-management', mode: 'one', permission: 'resource:view', path: '/resource-management', title: '文件资源', component: './resource-management', iconKey: 'database', menuGroup: 'resources', order: 10 },
  { id: 'data-quality-table-config', mode: 'one', permission: 'quality:monitor:read', path: '/data-quality/table-config', title: '数据表监控', component: './data-quality/table-config', iconKey: 'quality', menuGroup: 'data-quality', order: 10 },
  { id: 'data-quality-monitor-create', path: '/data-quality/monitor/create', title: '新增监控', component: './data-quality/monitor/editor', hidden: true, parentId: 'data-quality-table-config' },
  { id: 'data-quality-monitor-detail', path: '/data-quality/monitor/:id', title: '规则管理', component: './data-quality/monitor/detail', hidden: true, parentId: 'data-quality-table-config' },
  { id: 'data-quality-monitor-edit', path: '/data-quality/monitor/:id/edit', title: '编辑监控', component: './data-quality/monitor/editor', hidden: true, parentId: 'data-quality-table-config' },
  { id: 'data-quality-execution', mode: 'one', permission: 'quality:execution:read', path: '/data-quality/execution', title: '运行记录', component: './data-quality/execution', iconKey: 'report', menuGroup: 'data-quality', order: 20 },
  { id: 'data-quality-execution-detail', path: '/data-quality/execution/:executionNo', title: '运行记录详情', component: './data-quality/execution/detail', hidden: true, parentId: 'data-quality-execution' },
  { id: 'data-quality-rule-template', mode: 'one', permission: 'quality:template:read', path: '/data-quality/rule-template', title: '规则模板库', component: './data-quality/rule-template', iconKey: 'quality', menuGroup: 'data-quality', order: 30 },
  { id: 'system-users', mode: 'one', permission: 'security:user:read', path: '/system/users', title: '用户管理', component: './system/users', iconKey: 'system', menuGroup: 'system', order: 10 },
  { id: 'system-roles', mode: 'one', permission: 'security:role:read', path: '/system/roles', title: '角色管理', component: './system/roles', iconKey: 'system', menuGroup: 'system', order: 20 },
  { id: 'system-permissions', mode: 'one', permission: 'security:permission:read', path: '/system/permissions', title: '权限管理', component: './system/permissions', iconKey: 'system', menuGroup: 'system', order: 30 },
  { id: 'system-departments', mode: 'one', permission: 'security:department:read', path: '/system/departments', title: '部门管理', component: './system/departments', iconKey: 'system', menuGroup: 'system', order: 40 },
  { id: 'system-security-projects', mode: 'one', permission: 'security:project:read', path: '/system/projects', title: 'Security 授权项目', component: './system/security-projects', iconKey: 'system', menuGroup: 'system', order: 50 },
  { id: 'system-resource-permissions', mode: 'one', permission: 'security:resource-permission:read', path: '/system/resource-permissions', title: '资源授权', component: './system/resource-permissions', iconKey: 'system', menuGroup: 'system', order: 60 },
  { id: 'system-configs', mode: 'one', permission: 'security:config:read', path: '/system/configs', title: '系统配置', component: './system/configs', iconKey: 'system', menuGroup: 'system', order: 70 },
  { id: 'system-operation-logs', mode: 'one', permission: 'security:operation-log:read', path: '/system/oplogs', title: '操作日志', component: './system/oplogs', iconKey: 'system', menuGroup: 'system', order: 80 },
  { id: 'system-messages', mode: 'public', path: '/system/messages', title: '消息中心', component: './system/messages', iconKey: 'system', hidden: true, order: 90 },
];

const sortByOrder = <T extends { order?: number }>(left: T, right: T) =>
  (left.order ?? 0) - (right.order ?? 0);
const navigationSectionOrder: Record<NavigationSectionKey, number> = {
  task: 10,
  management: 20,
  system: 30,
};
const sortNavigationGroups = (left: NavigationGroup, right: NavigationGroup) =>
  navigationSectionOrder[left.section] - navigationSectionOrder[right.section]
  || sortByOrder(left, right);
const routeMap = new Map(appRoutes.map((route) => [route.id, route]));

export const canAccessNavigationRoute = (
  route: NavigationRoute,
  permissionCodes: readonly string[] | null | undefined,
) => {
  const visited = new Set<string>();
  let candidate: NavigationRoute | undefined = route;
  while (candidate && !visited.has(candidate.id)) {
    visited.add(candidate.id);
    if (candidate.mode && !satisfiesPermissionRequirement(
      permissionCodes,
      candidate as PermissionRequirement,
    )) return false;
    candidate = candidate.parentId ? routeMap.get(candidate.parentId) : undefined;
  }
  return true;
};

export const getNavigationGroups = (
  permissionCodes?: readonly string[] | null,
): NavigationGroupWithRoutes[] =>
  navigationGroups
    .map((group) => ({
      ...group,
      routes: appRoutes
        .filter((route) => route.menuGroup === group.id && !route.hidden
          && canAccessNavigationRoute(route, permissionCodes))
        .sort(sortByOrder),
    }))
    .filter((group) => group.routes.length > 0)
    .sort(sortNavigationGroups);
export const getMainNavigationGroups = getNavigationGroups;
export const getQuickCreateRoutes = (
  permissionCodes?: readonly string[] | null,
) => appRoutes
  .filter((route) => Boolean(route.quickCreateLabel)
    && canAccessNavigationRoute(route, permissionCodes)
    && (!route.quickCreateRequirement || satisfiesPermissionRequirement(
      permissionCodes,
      route.quickCreateRequirement,
    )))
  .sort(sortByOrder);
export const getStandaloneNavigationRoutes = (
  permissionCodes?: readonly string[] | null,
) => appRoutes
  .filter((route) => !route.menuGroup && !route.hidden
    && canAccessNavigationRoute(route, permissionCodes))
  .sort(sortByOrder);

const normalizePath = (path: string) =>
  path.split(/[?#]/, 1)[0].replace(/\/+$/, '') || '/';
const matchesRoute = (pattern: string, pathname: string) => {
  const patternParts = normalizePath(pattern).split('/').filter(Boolean);
  const pathParts = normalizePath(pathname).split('/').filter(Boolean);
  return patternParts.length === pathParts.length
    && patternParts.every((part, index) => part.startsWith(':') || part === pathParts[index]);
};
export const getRouteMetadata = (pathname: string) =>
  appRoutes.find((route) => matchesRoute(route.path, pathname));
export const getActiveNavigationId = (
  pathname: string,
  permissionCodes?: readonly string[] | null,
) => {
  const route = getRouteMetadata(pathname);
  if (!route || !canAccessNavigationRoute(route, permissionCodes)) return undefined;
  return route.parentId ?? route.id;
};
export const getActiveNavigationGroupId = (
  pathname: string,
  permissionCodes?: readonly string[] | null,
) => {
  const activeId = getActiveNavigationId(pathname, permissionCodes);
  return activeId ? routeMap.get(activeId)?.menuGroup : undefined;
};