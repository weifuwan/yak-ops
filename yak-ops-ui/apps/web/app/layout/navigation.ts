import { Database, type LucideIcon } from "lucide-react";

type ProductNavigationItem = {
  label: string;
  path: string;
  icon: LucideIcon;
};

export const PRODUCT_NAVIGATION: ProductNavigationItem[] = [
  {
    label: "数据源管理",
    path: "/data-source",
    icon: Database,
  },
];
