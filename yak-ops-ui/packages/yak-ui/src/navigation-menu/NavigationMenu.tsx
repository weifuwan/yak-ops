import { useEffect, useState, type ReactNode } from "react";

import { cn } from "../cn";

export interface NavigationMenuItem {
  key: string;
  label: ReactNode;
  value?: string;
  icon?: ReactNode;
  groupLabel?: ReactNode;
  disabled?: boolean;
  children?: NavigationMenuItem[];
}

export interface NavigationMenuProps {
  items: NavigationMenuItem[];
  activeKey?: string;
  defaultExpandedKeys?: string[];
  onSelect?: (item: NavigationMenuItem) => void;
  ariaLabel?: string;
  className?: string;
}

const itemContainsActive = (item: NavigationMenuItem, activeKey?: string): boolean =>
  Boolean(activeKey && item.key === activeKey)
  || Boolean(item.children?.some((child) => itemContainsActive(child, activeKey)));

const collectActiveAncestorKeys = (
  items: NavigationMenuItem[],
  activeKey?: string,
): Set<string> => {
  const expanded = new Set<string>();

  const visit = (nodes: NavigationMenuItem[]) => {
    nodes.forEach((item) => {
      if (!item.children?.length) return;
      if (item.children.some((child) => itemContainsActive(child, activeKey))) {
        expanded.add(item.key);
      }
      visit(item.children);
    });
  };

  visit(items);
  return expanded;
};

function Chevron({ expanded }: { expanded: boolean }) {
  return (
    <svg
      aria-hidden="true"
      viewBox="0 0 16 16"
      fill="none"
      className={cn(
        "size-3.5 shrink-0 text-[#7b8490] transition-transform duration-200 ease-out motion-reduce:transition-none",
        expanded && "rotate-180",
      )}
    >
      <path
        d="m3.75 6 4.25 4.25L12.25 6"
        stroke="currentColor"
        strokeWidth="1.5"
        strokeLinecap="round"
        strokeLinejoin="round"
      />
    </svg>
  );
}

export function NavigationMenu({
  items,
  activeKey,
  defaultExpandedKeys = [],
  onSelect,
  ariaLabel,
  className,
}: NavigationMenuProps) {
  const [expandedKeys, setExpandedKeys] = useState<Set<string>>(() => {
    const initial = new Set(defaultExpandedKeys);
    collectActiveAncestorKeys(items, activeKey).forEach((key) => initial.add(key));
    return initial;
  });

  useEffect(() => {
    const activeKeys = collectActiveAncestorKeys(items, activeKey);
    setExpandedKeys((current) => {
      let changed = false;
      const next = new Set(current);
      activeKeys.forEach((key) => {
        if (!next.has(key)) {
          next.add(key);
          changed = true;
        }
      });
      return changed ? next : current;
    });
  }, [activeKey, items]);

  const toggleExpanded = (key: string) => {
    setExpandedKeys((current) => {
      const next = new Set(current);
      if (next.has(key)) {
        next.delete(key);
      } else {
        next.add(key);
      }
      return next;
    });
  };

  const renderItem = (item: NavigationMenuItem, depth = 0): ReactNode => {
    const hasChildren = Boolean(item.children?.length);
    const expanded = expandedKeys.has(item.key);
    const branchActive = hasChildren
      && Boolean(item.children?.some((child) => itemContainsActive(child, activeKey)));
    const active = item.key === activeKey;
    const itemPadding = 14 + depth * 16;
    const baseClassName =
      "flex min-h-8 w-full items-center gap-2.5 border-r-2 py-1.5 pr-3 text-left text-[13px] transition-colors duration-150";

    return (
      <div key={item.key}>
        {hasChildren ? (
          <button
            type="button"
            aria-expanded={expanded}
            disabled={item.disabled}
            onClick={() => toggleExpanded(item.key)}
            style={{ paddingLeft: itemPadding }}
            className={cn(
              baseClassName,
              "justify-between",
              branchActive
                ? "border-transparent font-medium text-[var(--yak-color-primary)]"
                : "border-transparent font-normal text-[#525b67] hover:bg-[#f2f2f2]",
              item.disabled && "cursor-not-allowed opacity-50",
            )}
          >
            <span className="flex min-w-0 items-center gap-2.5">
              {item.icon ? (
                <span className={cn("flex size-4 shrink-0 items-center justify-center", branchActive ? "text-[var(--yak-color-primary)]" : "text-[#525b67]")}>
                  {item.icon}
                </span>
              ) : (
                <span className="size-4 shrink-0" aria-hidden="true" />
              )}
              <span className="truncate">{item.label}</span>
            </span>
            <Chevron expanded={expanded} />
          </button>
        ) : (
          <button
            type="button"
            aria-current={active ? "page" : undefined}
            disabled={item.disabled}
            onClick={() => onSelect?.(item)}
            style={{ paddingLeft: itemPadding }}
            className={cn(
              baseClassName,
              active
                ? "border-[var(--yak-color-primary)] bg-[var(--yak-color-primary-subtle)] font-semibold text-[#26282c]"
                : "border-transparent font-normal text-[#525b67] hover:bg-[#f2f2f2]",
              item.disabled && "cursor-not-allowed opacity-50",
            )}
          >
            {item.icon ? (
              <span className={cn("flex size-4 shrink-0 items-center justify-center", active ? "text-[var(--yak-color-primary)]" : "text-[#525b67]")}>
                {item.icon}
              </span>
            ) : (
              <span className="size-4 shrink-0" aria-hidden="true" />
            )}
            <span className="truncate">{item.label}</span>
          </button>
        )}

        {hasChildren ? (
          <div
            aria-hidden={!expanded}
            className={cn(
              "grid transition-[grid-template-rows,opacity] duration-200 ease-out motion-reduce:transition-none",
              expanded ? "grid-rows-[1fr] opacity-100" : "grid-rows-[0fr] opacity-0",
            )}
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
    <nav
      aria-label={ariaLabel}
      className={cn("flex min-h-0 flex-1 flex-col gap-1 overflow-y-auto", className)}
    >
      {items.map((item, index) => {
        const previousGroupLabel = index > 0 ? items[index - 1]?.groupLabel : undefined;
        const showGroupLabel = Boolean(item.groupLabel && item.groupLabel !== previousGroupLabel);

        return (
          <div key={item.key}>
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
  );
}
