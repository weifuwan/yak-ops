import { useMemo } from "react";
import { useLocation, useNavigate } from "react-router-dom";
import { NavigationMenu, type NavigationMenuItem } from "@yak-ops/yak-ui";

import { PlanningIcon } from "../data-modeling/planning/icons";
import { IndicatorIcon } from "../data-modeling/indicators/icons";
import type { ProductNavigationItem } from "./navigation";

type ProductSidebarProps = {
  productLabel: string;
  navigation: ProductNavigationItem[];
};

const getItemKey = (item: ProductNavigationItem) =>
  item.id || item.path || item.label;

const mapNavigationItems = (items: ProductNavigationItem[]): NavigationMenuItem[] =>
  items.map((item) => {
    const Icon = item.icon;
    return {
      key: getItemKey(item),
      label: item.label,
      value: item.path,
      groupLabel: item.groupLabel,
      icon: item.indicatorIcon
        ? <IndicatorIcon name={item.indicatorIcon} monochrome />
        : item.svgIcon
          ? <PlanningIcon name={item.svgIcon} className="text-[#262874]" />
          : Icon
            ? <Icon size={16} strokeWidth={1.8} className="text-[#262874]" />
            : undefined,
      children: item.children ? mapNavigationItems(item.children) : undefined,
    };
  });

const findActiveItem = (
  items: ProductNavigationItem[],
  pathname: string,
): ProductNavigationItem | undefined => {
  const matches: ProductNavigationItem[] = [];

  const visit = (nodes: ProductNavigationItem[]) => {
    nodes.forEach((item) => {
      if (item.path && (pathname === item.path || pathname.startsWith(item.path + "/"))) {
        matches.push(item);
      }
      if (item.children?.length) visit(item.children);
    });
  };

  visit(items);
  return matches.sort((left, right) =>
    (right.path?.length || 0) - (left.path?.length || 0),
  )[0];
};

export default function ProductSidebar({ productLabel, navigation }: ProductSidebarProps) {
  const location = useLocation();
  const navigate = useNavigate();
  const menuItems = useMemo(() => mapNavigationItems(navigation), [navigation]);
  const activeItem = useMemo(
    () => findActiveItem(navigation, location.pathname),
    [navigation, location.pathname],
  );

  return (
    <aside className="flex w-48 shrink-0 flex-col border-r border-[#e6e8eb] bg-[#FAFAFA]">
      <div className="px-3 pb-2 pt-4 text-[11px] font-medium text-[#8b929e]">
        {productLabel}
      </div>
      <NavigationMenu
        ariaLabel={productLabel}
        items={menuItems}
        activeKey={activeItem ? getItemKey(activeItem) : undefined}
        onSelect={(item) => {
          if (item.value) navigate(item.value);
        }}
      />
    </aside>
  );
}
