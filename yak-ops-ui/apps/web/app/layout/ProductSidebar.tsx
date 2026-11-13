import { useEffect, useState, type ReactNode } from "react";
import { ChevronDown } from "lucide-react";
import { NavLink, useLocation } from "react-router-dom";

import type { ProductNavigationItem } from "./navigation";

type ProductSidebarProps = {
  productLabel: string;
  navigation: ProductNavigationItem[];
};

const getItemKey = (item: ProductNavigationItem) =>
  item.id || item.path || item.label;

const pathMatches = (pathname: string, path: string) =>
  pathname === path || pathname.startsWith(path + "/");

const itemContainsPath = (item: ProductNavigationItem, pathname: string): boolean =>
  Boolean(item.path && pathMatches(pathname, item.path))
  || Boolean(item.children?.some((child) => itemContainsPath(child, pathname)));

const collectExpandedKeys = (items: ProductNavigationItem[], pathname: string) => {
  const expanded = new Set<string>();

  const visit = (nodes: ProductNavigationItem[]) => {
    nodes.forEach((item) => {
      if (!item.children?.length) return;
      if (item.children.some((child) => itemContainsPath(child, pathname))) {
        expanded.add(getItemKey(item));
      }
      visit(item.children);
    });
  };

  visit(items);
  return expanded;
};

export default function ProductSidebar({ productLabel, navigation }: ProductSidebarProps) {
  const location = useLocation();
  const [expandedItems, setExpandedItems] = useState<Set<string>>(() =>
    collectExpandedKeys(navigation, location.pathname),
  );

  useEffect(() => {
    const activeKeys = collectExpandedKeys(navigation, location.pathname);
    setExpandedItems((current) => {
      const next = new Set(current);
      activeKeys.forEach((key) => next.add(key));
      return next;
    });
  }, [location.pathname, navigation]);

  const toggleExpanded = (key: string) => {
    setExpandedItems((current) => {
      const next = new Set(current);
      if (next.has(key)) {
        next.delete(key);
      } else {
        next.add(key);
      }
      return next;
    });
  };

  const renderItem = (item: ProductNavigationItem, depth = 0): ReactNode => {
    const key = getItemKey(item);
    const Icon = item.icon;
    const hasChildren = Boolean(item.children?.length);
    const expanded = expandedItems.has(key);
    const branchActive = hasChildren
      && Boolean(item.children?.some((child) => itemContainsPath(child, location.pathname)));
    const itemPadding = 14 + depth * 16;

    return (
      <div key={key}>
        {hasChildren ? (
          <button
            type="button"
            aria-expanded={expanded}
            onClick={() => toggleExpanded(key)}
            style={{ paddingLeft: itemPadding }}
            className={[
              "flex min-h-8 w-full items-center justify-between gap-2.5 rounded-md py-1.5 pr-3 text-left text-[13px] transition-colors duration-150",
              branchActive
                ? "font-medium text-[var(--yak-color-primary)]"
                : "font-normal text-[#525b67] hover:bg-[#f2f2f2]",
            ].join(" ")}
          >
            <span className="flex min-w-0 items-center gap-2.5">
              {Icon ? (
                <Icon
                  className={[
                    "h-4 w-4 shrink-0 transition-colors",
                    branchActive ? "text-[var(--yak-color-primary)]" : "text-[#525b67]",
                  ].join(" ")}
                  strokeWidth={1.8}
                />
              ) : (
                <span className="h-4 w-4 shrink-0" aria-hidden="true" />
              )}
              <span className="truncate">{item.label}</span>
            </span>
            <ChevronDown
              size={14}
              aria-hidden="true"
              className={[
                "shrink-0 text-[#7b8490] transition-transform duration-200 ease-out",
                expanded ? "rotate-180" : "",
              ].join(" ")}
            />
          </button>
        ) : item.path ? (
          <NavLink
            to={item.path}
            end={item.end}
            style={{ paddingLeft: itemPadding }}
            className={({ isActive }) =>
              [
                "flex min-h-8 items-center gap-2.5 rounded-md py-1.5 pr-3 text-[13px] transition-colors duration-150",
                isActive
                  ? "bg-[var(--yak-color-primary-subtle)] font-semibold text-[var(--yak-color-primary)]"
                  : "font-normal text-[#525b67] hover:bg-[#f2f2f2]",
              ].join(" ")
            }
          >
            {({ isActive }) => (
              <>
                {Icon ? (
                  <Icon
                    className={[
                      "h-4 w-4 shrink-0 transition-colors",
                      isActive ? "text-[var(--yak-color-primary)]" : "text-[#525b67]",
                    ].join(" ")}
                    strokeWidth={1.8}
                  />
                ) : (
                  <span className="h-4 w-4 shrink-0" aria-hidden="true" />
                )}
                <span className="truncate">{item.label}</span>
              </>
            )}
          </NavLink>
        ) : null}

        {hasChildren ? (
          <div
            aria-hidden={!expanded}
            className={[
              "grid transition-[grid-template-rows,opacity] duration-200 ease-out",
              expanded ? "grid-rows-[1fr] opacity-100" : "grid-rows-[0fr] opacity-0",
            ].join(" ")}
          >
            <div className="min-h-0 overflow-hidden">
              <div className="flex flex-col gap-0.5">
                {item.children?.map((child) => renderItem(child, depth + 1))}
              </div>
            </div>
          </div>
        ) : null}
      </div>
    );
  };

  return (
    <aside className="flex w-48 shrink-0 flex-col border-r border-[#e6e8eb] bg-[#FAFAFA]">
      <div className="px-3 pb-2 pt-4 text-[11px] font-medium text-[#8b929e]">
        {productLabel}
      </div>

      <nav aria-label={productLabel} className="flex min-h-0 flex-1 flex-col gap-1 overflow-y-auto">
        {navigation.map((item, index) => {
          const previousGroupLabel = index > 0 ? navigation[index - 1]?.groupLabel : undefined;
          const showGroupLabel = Boolean(item.groupLabel && item.groupLabel !== previousGroupLabel);

          return (
            <div key={getItemKey(item)}>
              {showGroupLabel ? (
                <div className="px-[14px] pb-1 pt-2 text-[11px] font-medium text-[#8b929e]">
                  {item.groupLabel}
                </div>
              ) : null}
              {renderItem(item)}
            </div>
          );
        })}
      </nav>
    </aside>
  );
}
