import {
  Activity,
  ArrowRightLeft,
  Bell,
  BookOpen,
  Boxes,
  ChartNoAxesCombined,
  Database,
  GitBranch,
  FolderOpen,
  LayoutDashboard,
  LayoutGrid,
  Layers,
  ListOrdered,
  Network,
  Settings,
  Table2,
  Users,
  Wrench,
  type LucideIcon,
} from "lucide-react";

export type ProductNavigationItem = {
  id?: string;
  label: string;
  path?: string;
  icon?: LucideIcon;
  groupLabel?: string;
  end?: boolean;
  children?: ProductNavigationItem[];
};

export type ProductModuleNavigationItem = {
  id: string;
  label: string;
  path: string;
  matchPath: string;
  exact?: boolean;
  navigation: ProductNavigationItem[];
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
export const DATA_DEVELOPMENT_PRODUCT_LABEL = "数据开发";
export const DATA_MODELING_PRODUCT_LABEL = "数据建模";
export const OPERATIONS_PRODUCT_LABEL = "运维中心";
export const MANAGEMENT_PRODUCT_LABEL = "管理中心";
export const RESOURCE_MANAGEMENT_PRODUCT_LABEL = "资源管理";

export const DATA_INTEGRATION_NAVIGATION: ProductNavigationItem[] = [
  {
    label: "概览",
    path: "/overview",
    icon: LayoutDashboard,
  },
  {
    label: "数据源管理",
    path: "/data-source",
    icon: Database,
  },
  {
    label: "数据同步",
    path: "/data-sync",
    icon: ArrowRightLeft,
  },
];

export const DATA_MODELING_MODULE_NAVIGATION: ProductModuleNavigationItem[] = [
  {
    id: "modeling-home",
    label: "首页",
    path: "/data-modeling",
    matchPath: "/data-modeling",
    exact: true,
    navigation: [],
  },
  {
    id: "dw-planning",
    label: "数仓规划",
    path: "/data-modeling/planning/business-type",
    matchPath: "/data-modeling/planning",
    navigation: [
      {
        id: "business-type",
        label: "业务分类",
        path: "/data-modeling/planning/business-type",
        icon: LayoutGrid,
        end: true,
      },
      {
        id: "dw-layer",
        label: "数仓分层",
        path: "/data-modeling/planning/dw-layer",
        icon: Layers,
        end: true,
      },
      {
        id: "common-layer",
        label: "公共层",
        icon: Database,
        children: [
          {
            id: "data-domain",
            label: "数据域",
            path: "/data-modeling/planning/common-layer/data-domain",
            end: true,
          },
          {
            id: "business-process",
            label: "业务过程",
            path: "/data-modeling/planning/common-layer/business-process",
            end: true,
          },
        ],
      },
      {
        id: "application-layer",
        label: "应用层",
        icon: Boxes,
        children: [
          {
            id: "data-mart",
            label: "数据集市",
            path: "/data-modeling/planning/application-layer/data-market",
            end: true,
          },
          {
            id: "subject-domain",
            label: "主题域",
            path: "/data-modeling/planning/application-layer/subject-domain",
            end: true,
          },
        ],
      },
    ],
  },
  {
    id: "data-standards",
    label: "数据标准",
    path: "/data-modeling/standards/fields",
    matchPath: "/data-modeling/standards",
    navigation: [
      {
        id: "field-standard",
        label: "字段标准",
        path: "/data-modeling/standards/fields",
        icon: BookOpen,
        end: true,
      },
      {
        id: "code-standard",
        label: "标准代码",
        path: "/data-modeling/standards/codes",
        end: true,
      },
      {
        id: "unit-standard",
        label: "度量单位",
        path: "/data-modeling/standards/units",
        end: true,
      },
      {
        id: "naming-dictionary",
        label: "命名词典",
        path: "/data-modeling/standards/dictionary",
        end: true,
      },
    ],
  },
  {
    id: "dimension-modeling",
    label: "维度建模",
    path: "/data-modeling/models/positive",
    matchPath: "/data-modeling/models",
    navigation: [
      {
        id: "dimension-modeling-positive",
        label: "维度建模",
        path: "/data-modeling/models/positive",
        icon: Table2,
        end: true,
      },
      {
        id: "dimension-modeling-reverse",
        label: "逆向建模",
        path: "/data-modeling/models/reverse",
        end: true,
      },
    ],
  },
  {
    id: "data-indicators",
    label: "数据指标",
    path: "/data-modeling/indicators/atom",
    matchPath: "/data-modeling/indicators",
    navigation: [
      {
        id: "composite-indicator",
        label: "复合指标",
        path: "/data-modeling/indicators/compose",
        icon: ChartNoAxesCombined,
        end: true,
      },
      {
        id: "derivative-indicator",
        label: "派生指标",
        path: "/data-modeling/indicators/derivative",
        end: true,
      },
      {
        id: "atomic-indicator",
        label: "原子指标",
        path: "/data-modeling/indicators/atom",
        end: true,
      },
      {
        id: "indicator-modifier",
        label: "修饰词",
        path: "/data-modeling/indicators/modifier",
        end: true,
      },
      {
        id: "time-period",
        label: "时间周期",
        path: "/data-modeling/indicators/time-period",
        end: true,
      },
    ],
  },
];

export const RESOURCE_MANAGEMENT_NAVIGATION: ProductNavigationItem[] = [
  {
    label: "资源文件",
    path: "/resources",
    icon: FolderOpen,
    end: true,
  },
  {
    label: "任务组配置",
    path: "/resources/task-groups",
    icon: Layers,
  },
  {
    label: "任务组队列",
    path: "/resources/task-group-queue",
    icon: ListOrdered,
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
  {
    label: "告警中心",
    path: "/management/alerts",
    icon: Bell,
  },
];

const DATA_INTEGRATION_PRODUCT: GlobalProductMenuItem = {
  id: "data-integration",
  label: DATA_INTEGRATION_PRODUCT_LABEL,
  icon: Database,
  path: "/overview",
};

const DATA_DEVELOPMENT_PRODUCT: GlobalProductMenuItem = {
  id: "data-development",
  label: DATA_DEVELOPMENT_PRODUCT_LABEL,
  icon: GitBranch,
  path: "/data-development",
};

const DATA_MODELING_PRODUCT: GlobalProductMenuItem = {
  id: "data-modeling",
  label: DATA_MODELING_PRODUCT_LABEL,
  icon: Boxes,
  path: "/data-modeling",
};

const RESOURCE_MANAGEMENT_PRODUCT: GlobalProductMenuItem = {
  id: "resource-management",
  label: RESOURCE_MANAGEMENT_PRODUCT_LABEL,
  icon: FolderOpen,
  path: "/resources",
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
  DATA_DEVELOPMENT_PRODUCT,
  DATA_MODELING_PRODUCT,
  RESOURCE_MANAGEMENT_PRODUCT,
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
    id: "data-development",
    label: DATA_DEVELOPMENT_PRODUCT_LABEL,
    products: [DATA_DEVELOPMENT_PRODUCT],
  },
  {
    id: "data-modeling",
    label: DATA_MODELING_PRODUCT_LABEL,
    products: [DATA_MODELING_PRODUCT],
  },
  {
    id: "resource-management",
    label: RESOURCE_MANAGEMENT_PRODUCT_LABEL,
    products: [RESOURCE_MANAGEMENT_PRODUCT],
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
