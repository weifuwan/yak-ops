import {
  type PermissionRequirement,
  satisfiesPermissionRequirement,
} from "../utils/security/permission";

export type NavigationIconKey = "database";

interface NavigationRouteBase {
  id: string;
  path: string;
  title: string;
  component: string;
  iconKey?: NavigationIconKey;
  menuGroup?: string;
  order?: number;
  hidden?: boolean;
  parentId?: string;
  quickCreateLabel?: string;
  quickCreateRequirement?: PermissionRequirement;
}

export type NavigationRoute = NavigationRouteBase &
  (PermissionRequirement | { parentId: string; mode?: never });

export interface NavigationGroup {
  id: string;
  title: string;
  iconKey: NavigationIconKey;
  section: string;
  order: number;
}

export interface NavigationGroupWithRoutes extends NavigationGroup {
  routes: NavigationRoute[];
}

export const appRoutes: readonly NavigationRoute[] = [
  {
    id: "data-source",
    mode: "public",
    path: "/data-source",
    title: "数据源管理",
    component: "./data-source",
    iconKey: "database",
    order: 10,
  },
];

export const navigationGroups: readonly NavigationGroup[] = [];

export const canAccessNavigationRoute = (
  route: NavigationRoute,
  permissionCodes?: readonly string[] | null,
  _menuCodes?: readonly string[] | null,
) =>
  !route.mode ||
  route.mode === "public" ||
  satisfiesPermissionRequirement(permissionCodes, route as PermissionRequirement);

export const getRouteMetadata = (pathname: string) =>
  pathname === "/data-source" ? appRoutes[0] : undefined;

export const getActiveNavigationId = (
  pathname: string,
  _permissionCodes?: readonly string[] | null,
  _menuCodes?: readonly string[] | null,
) => getRouteMetadata(pathname)?.id;

export const getActiveNavigationGroupId = (
  _pathname?: string,
  _permissionCodes?: readonly string[] | null,
  _menuCodes?: readonly string[] | null,
) => undefined;

export const getNavigationGroups = (
  _permissionCodes?: readonly string[] | null,
  _menuCodes?: readonly string[] | null,
) => [];
export const getMainNavigationGroups = getNavigationGroups;
export const getQuickCreateRoutes = (
  _permissionCodes?: readonly string[] | null,
  _menuCodes?: readonly string[] | null,
) => [];
export const getStandaloneNavigationRoutes = (
  _permissionCodes?: readonly string[] | null,
  _menuCodes?: readonly string[] | null,
) => [...appRoutes];
