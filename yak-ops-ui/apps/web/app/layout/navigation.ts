import {
  Activity,
  ArrowRightLeft,
  Database,
  LayoutGrid,
  Settings,
  Users,
  Wrench,
  type LucideIcon,
} from "lucide-react";

export type ProductNavigationItem = {
  label: string;
  path: string;
  icon: LucideIcon;
  groupLabel?: string;
};

type GlobalProductMenuItem = {
  id: string;
  label: string;
  icon: LucideIcon;
  path: string;
};

type AllProductGroup = {
  id: string;
  label: string;
  products: GlobalProductMenuItem[];
};

export const DATA_INTEGRATION_PRODUCT_LABEL = "数据集成";
export const OPERATIONS_PRODUCT_LABEL = "运维中心";
export const MANAGEMENT_PRODUCT_LABEL = "管理中心";

export const DATA_INTEGRATION_NAVIGATION: ProductNavigationItem[] = [
  {
    label: "数据源管理",
    path: "/data-source",
    icon: Database,
  },
  {
    label: "离线同步",
    path: "/offline-sync",
    icon: ArrowRightLeft,
  },
  {
    label: "实时同步",
    path: "/realtime-sync",
    icon: Activity,
  },
];

export const OPERATIONS_NAVIGATION: ProductNavigationItem[] = [
  {
    label: "离线任务",
    path: "/operations/offline-tasks",
    icon: ArrowRightLeft,
    groupLabel: "任务运维",
  },
  {
    label: "实时任务",
    path: "/operations/realtime-tasks",
    icon: Activity,
    groupLabel: "任务运维",
  },
];

export const MANAGEMENT_NAVIGATION: ProductNavigationItem[] = [
  {
    label: "用户管理",
    path: "/management/users",
    icon: Users,
  },
  {
    label: "工作空间管理",
    path: "/management/workspaces",
    icon: LayoutGrid,
  },
];

const DATA_INTEGRATION_PRODUCT: GlobalProductMenuItem = {
  id: "data-integration",
  label: DATA_INTEGRATION_PRODUCT_LABEL,
  icon: Database,
  path: "/data-source",
};

const OPERATIONS_PRODUCT: GlobalProductMenuItem = {
  id: "operations",
  label: OPERATIONS_PRODUCT_LABEL,
  icon: Wrench,
  path: "/operations/offline-tasks",
};

const MANAGEMENT_PRODUCT: GlobalProductMenuItem = {
  id: "management",
  label: MANAGEMENT_PRODUCT_LABEL,
  icon: Settings,
  path: "/management/users",
};

export const GLOBAL_PRODUCT_MENU: GlobalProductMenuItem[] = [
  DATA_INTEGRATION_PRODUCT,
  OPERATIONS_PRODUCT,
  MANAGEMENT_PRODUCT,
];

export const ALL_PRODUCT_GROUPS: AllProductGroup[] = [
  {
    id: "data-integration",
    label: DATA_INTEGRATION_PRODUCT_LABEL,
    products: [DATA_INTEGRATION_PRODUCT],
  },
  {
    id: "operations",
    label: "运维",
    products: [OPERATIONS_PRODUCT],
  },
  {
    id: "management",
    label: "管理",
    products: [MANAGEMENT_PRODUCT],
  },
];
