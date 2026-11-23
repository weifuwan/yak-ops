import {
  Activity,
  ArrowRightLeft,
  Bell,
  Building2,
  Boxes,
  Database,
  GitBranch,
  FolderOpen,
  LayoutDashboard,
  LayoutGrid,
  Layers,
  ListOrdered,
  Server,
  Settings,
  Settings2,
  Sparkles,
  Table2,
  Users,
  KeyRound,
  Wrench,
  type LucideIcon,
} from "lucide-react";
import type { PlanningIconName } from "../data-modeling/planning/components/icons";
import type { IndicatorIconName } from "../data-modeling/indicators/icons";
import type { StandardIconName } from "../data-modeling/standards/components/icons";

export type ProductNavigationItem = {
  id?: string;
  label: string;
  path?: string;
  icon?: LucideIcon;
  svgIcon?: PlanningIconName;
  indicatorIcon?: IndicatorIconName;
  standardIcon?: StandardIconName;
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
        svgIcon: "business-type",
        end: true,
      },
      {
        id: "dw-layer",
        label: "数仓分层",
        path: "/data-modeling/planning/dw-layer",
        svgIcon: "dw-layer",
        end: true,
      },
      {
        id: "common-layer",
        label: "公共层",
        svgIcon: "public-layer",
        children: [
          {
            id: "data-domain",
            label: "数据域",
            path: "/data-modeling/planning/common-layer/data-domain",
            svgIcon: "data-domain",
            end: true,
          },
          {
            id: "business-process",
            label: "业务过程",
            path: "/data-modeling/planning/common-layer/business-process",
            svgIcon: "business-process",
            end: true,
          },
        ],
      },
      {
        id: "application-layer",
        label: "应用层",
        svgIcon: "application-layer",
        children: [
          {
            id: "data-mart",
            label: "数据集市",
            path: "/data-modeling/planning/application-layer/data-market",
            svgIcon: "data-mart",
            end: true,
          },
          {
            id: "subject-domain",
            label: "主题域",
            path: "/data-modeling/planning/application-layer/subject-domain",
            svgIcon: "subject-domain",
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
        standardIcon: "field-standard",
        end: true,
      },
      {
        id: "code-standard",
        label: "标准代码",
        path: "/data-modeling/standards/codes",
        standardIcon: "standard-code",
        end: true,
      },
      {
        id: "unit-standard",
        label: "度量单位",
        path: "/data-modeling/standards/units",
        standardIcon: "unit-standard",
        end: true,
      },
      {
        id: "naming-dictionary",
        label: "命名词典",
        path: "/data-modeling/standards/dictionary",
        standardIcon: "naming-dictionary",
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
        indicatorIcon: "composite-indicator",
        end: true,
      },
      {
        id: "derivative-indicator",
        label: "派生指标",
        path: "/data-modeling/indicators/derivative",
        indicatorIcon: "derived-indicator",
        end: true,
      },
      {
        id: "atomic-indicator",
        label: "原子指标",
        path: "/data-modeling/indicators/atom",
        indicatorIcon: "atomic-indicator",
        end: true,
      },
      {
        id: "indicator-modifier",
        label: "修饰词",
        path: "/data-modeling/indicators/modifier",
        indicatorIcon: "indicator-modifier",
        end: true,
      },
      {
        id: "time-period",
        label: "时间周期",
        path: "/data-modeling/indicators/time-period",
        indicatorIcon: "time-period",
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
    label: "Worker 分组",
    path: "/resources/worker-groups",
    icon: Server,
  },
  {
    label: "环境管理",
    path: "/resources/environments",
    icon: Settings2,
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
    label: "运维概览",
    path: "/operations/overview",
    icon: LayoutDashboard,
  },
  {
    id: "workflow-operations",
    label: "工作流运维",
    icon: GitBranch,
    groupLabel: "任务运维",
    children: [
      {
        label: "工作流实例",
        path: "/operations/workflows/instances",
      },
      {
        label: "周期调度",
        path: "/operations/workflows/schedules",
      },
    ],
  },
  {
    id: "offline-sync-operations",
    label: "离线同步运维",
    icon: ArrowRightLeft,
    children: [
      {
        label: "离线任务",
        path: "/operations/offline/tasks",
      },
      {
        label: "离线实例",
        path: "/operations/offline/instances",
      },
    ],
  },
  {
    id: "realtime-sync-operations",
    label: "实时同步运维",
    icon: Activity,
    children: [
      {
        label: "实时任务",
        path: "/operations/realtime/tasks",
      },
      {
        label: "实时实例",
        path: "/operations/realtime/instances",
      },
    ],
  },
  {
    label: "任务告警",
    path: "/operations/alerts",
    icon: Bell,
    groupLabel: "任务监控",
  },
];

export const MANAGEMENT_NAVIGATION: ProductNavigationItem[] = [
  {
    label: "租户管理",
    path: "/management/tenants",
    icon: Building2,
  },
  {
    label: "用户管理",
    path: "/management/users",
    icon: Users,
  },
  {
    label: "Token 管理",
    path: "/management/tokens",
    icon: KeyRound,
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

const DATA_AGENT_PRODUCT: GlobalProductMenuItem = {
  id: "data-agent",
  label: "Data Agent",
  icon: Sparkles,
  path: "/data-agent",
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
  path: "/operations/overview",
};

const MANAGEMENT_PRODUCT: GlobalProductMenuItem = {
  id: "management",
  label: MANAGEMENT_PRODUCT_LABEL,
  icon: Settings,
  path: "/management/users",
};

export const GLOBAL_PRODUCT_MENU: GlobalProductMenuItem[] = [
  DATA_AGENT_PRODUCT,
  DATA_INTEGRATION_PRODUCT,
  DATA_DEVELOPMENT_PRODUCT,
  DATA_MODELING_PRODUCT,
  RESOURCE_MANAGEMENT_PRODUCT,
  OPERATIONS_PRODUCT,
  MANAGEMENT_PRODUCT,
];

export const ALL_PRODUCT_GROUPS: AllProductGroup[] = [
  {
    id: "agents",
    label: "智能体",
    products: [DATA_AGENT_PRODUCT],
  },
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
