import { Database, type LucideIcon } from "lucide-react";

type ProductNavigationItem = {
  label: string;
  path: string;
  icon: LucideIcon;
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

export const CURRENT_PRODUCT_LABEL = "数据集成";

export const PRODUCT_NAVIGATION: ProductNavigationItem[] = [
  {
    label: "数据源管理",
    path: "/data-source",
    icon: Database,
  },
];

export const GLOBAL_PRODUCT_MENU: GlobalProductMenuItem[] = [
  {
    id: "data-integration",
    label: CURRENT_PRODUCT_LABEL,
    icon: Database,
    path: "/data-source",
  },
];

export const ALL_PRODUCT_GROUPS: AllProductGroup[] = [
  {
    id: "data-integration",
    label: CURRENT_PRODUCT_LABEL,
    products: GLOBAL_PRODUCT_MENU,
  },
];
