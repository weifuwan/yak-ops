import { Database, type LucideIcon } from "lucide-react";

type ProductNavigationItem = {
  label: string;
  path: string;
  icon: LucideIcon;
};

type ProductGroup = {
  id: string;
  label: string;
  items: ProductNavigationItem[];
};

export const CURRENT_PRODUCT_LABEL = "数据集成";

export const PRODUCT_NAVIGATION: ProductNavigationItem[] = [
  {
    label: "数据源管理",
    path: "/data-source",
    icon: Database,
  },
];

export const PRODUCT_GROUPS: ProductGroup[] = [
  {
    id: "data-integration",
    label: CURRENT_PRODUCT_LABEL,
    items: PRODUCT_NAVIGATION,
  },
];
