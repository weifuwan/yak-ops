import {
  DATA_DEVELOPMENT_PRODUCT_LABEL,
  DATA_INTEGRATION_NAVIGATION,
  DATA_MODELING_MODULE_NAVIGATION,
  MANAGEMENT_NAVIGATION,
  OPERATIONS_NAVIGATION,
  RESOURCE_MANAGEMENT_NAVIGATION,
} from "./navigation.ts";
import type { ProductNavigationItem } from "./navigation.ts";

const findNavigationLabel = (
  items: ProductNavigationItem[],
  pathname: string,
): string | undefined => {
  for (const item of items) {
    const childLabel = item.children
      ? findNavigationLabel(item.children, pathname)
      : undefined;

    if (childLabel) return childLabel;
    if (item.path === pathname) return item.label;
  }

  return undefined;
};

const normalizePathname = (pathname: string) =>
  pathname.length > 1 ? pathname.replace(/\/+$/, "") : pathname;

export function resolveDocumentTitle(pathname: string, search = ""): string {
  const path = normalizePathname(pathname);

  if (path === "/login") return "登录";
  if (path === "/data-development") {
    return new URLSearchParams(search).get("section") === "catalog"
      ? "数据目录"
      : DATA_DEVELOPMENT_PRODUCT_LABEL;
  }
  if (path.startsWith("/data-development/")) return DATA_DEVELOPMENT_PRODUCT_LABEL;
  if (path === "/data-agent/particle-preview") return "粒子效果预览";
  if (path === "/data-agent" || path.startsWith("/data-agent/")) {
    return "Data Agent";
  }

  if (path === "/data-sync/new") return "新建数据同步任务";
  if (path === "/data-sync/new/database") return "新建数据库同步任务";
  if (path === "/data-sync/new/multi-table") return "新建多表同步任务";
  if (path === "/data-sync/new/single-table") return "新建单表同步任务";
  if (/^\/data-sync\/[^/]+\/edit$/.test(path)) return "编辑数据同步任务";
  if (/^\/data-sync\/[^/]+\/detail$/.test(path)) return "数据同步任务详情";

  if (path === "/operations") return "运维概览";
  if (path === "/operations/offline-tasks") return "离线任务";
  if (path === "/operations/realtime-tasks") return "实时任务";
  if (/^\/operations\/offline-tasks\/instances\//.test(path)) {
    return "离线实例详情";
  }
  if (/^\/operations\/realtime-tasks\/instances\//.test(path)) {
    return "实时实例详情";
  }
  if (path === "/management") return "用户管理";

  const modelingModule = DATA_MODELING_MODULE_NAVIGATION.filter((item) =>
    item.exact
      ? path === item.matchPath
      : path === item.matchPath || path.startsWith(item.matchPath + "/"),
  ).sort((left, right) => right.matchPath.length - left.matchPath.length)[0];

  if (modelingModule) {
    return (
      findNavigationLabel(modelingModule.navigation, path) ??
      modelingModule.label
    );
  }

  const navigationGroups = [
    DATA_INTEGRATION_NAVIGATION,
    OPERATIONS_NAVIGATION,
    MANAGEMENT_NAVIGATION,
    RESOURCE_MANAGEMENT_NAVIGATION,
  ];

  for (const group of navigationGroups) {
    const label = findNavigationLabel(group, path);
    if (label) return label;
  }

  return "Yak Ops";
}
