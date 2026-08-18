import { appRoutes } from '../src/config/navigation';

const fullscreenRouteIds = new Set([
  'dashboard-new',
  'dashboard-editor',
  'dashboard-viewer',
  'digital-screen-editor',
  'digital-screen-viewer',
  'workflow-definition-editor',
]);

const toProtectedRoute = ({
  path,
  component,
  hidden,
}: (typeof appRoutes)[number]) => ({
  path,
  component,
  access: 'isAuthenticated',
  wrappers: ['@/components/security/RouteAccessBoundary'],
  ...(hidden ? { hideInMenu: true, hideInBreadcrumb: true } : {}),
});

const fullscreenRoutes = appRoutes
  .filter((route) => fullscreenRouteIds.has(route.id))
  .map((route) => ({
    ...toProtectedRoute(route),
    layout: false,
  }));

const siteRoutes = appRoutes
  .filter((route) => !fullscreenRouteIds.has(route.id))
  .map(toProtectedRoute);

/**
 * 普通业务页面进入自定义 SiteLayout；需要沉浸式创作空间的编辑器则使用
 * 独立 fullscreen workspace，不继承 Yak 左侧菜单和全局 Header。
 */
export default [
  {
    name: 'Login',
    path: '/login',
    component: './login',
    layout: false,
    hideInMenu: true,
  },
  ...fullscreenRoutes,
  {
    path: '/',
    layout: false,
    component: '@/layouts/SiteLayout',
    routes: [
      {
        path: '/',
        redirect: '/home',
      },
      ...siteRoutes,
    ],
  },
  {
    path: '/403',
    component: './403',
    layout: false,
    hideInMenu: true,
  },
  {
    path: '*',
    layout: false,
    component: './404',
  },
];
