import { NavLink } from "react-router-dom";

import type { ProductNavigationItem } from "./navigation";

type ProductSidebarProps = {
  productLabel: string;
  navigation: ProductNavigationItem[];
};

export default function ProductSidebar({ productLabel, navigation }: ProductSidebarProps) {
  return (
    <aside className="flex w-48 shrink-0 flex-col border-r border-[#e6e8eb] bg-[#FAFAFA]">
      <div className="px-3 pb-2 pt-4 text-[11px] font-medium text-[#8b929e]">{productLabel}</div>

      <nav className="flex flex-col gap-1">
        {navigation.map((item, index) => {
          const Icon = item.icon;
          const previousGroupLabel = index > 0 ? navigation[index - 1]?.groupLabel : undefined;
          const showGroupLabel = Boolean(item.groupLabel && item.groupLabel !== previousGroupLabel);

          return (
            <div key={item.path}>
              {showGroupLabel ? (
                <div className="px-[14px] pb-1 pt-2 text-[11px] font-medium text-[#8b929e]">
                  {item.groupLabel}
                </div>
              ) : null}
              <NavLink
                to={item.path}
                className={({ isActive }) =>
                  [
                    "flex h-8 items-center gap-3.5 border-r-2 px-[14px] text-[13px] text-[#26282c] transition-colors",
                    isActive
                      ? "border-[var(--yak-color-primary)] bg-[var(--yak-color-primary-subtle)] font-semibold"
                      : "border-transparent font-normal hover:bg-[#f2f2f2]",
                  ].join(" ")
                }
              >
                {({ isActive }) => (
                  <>
                    <Icon
                      className={`h-4 w-4 shrink-0 transition-colors ${
                        isActive ? "text-[var(--yak-color-primary)]" : "text-[#26282c]"
                      }`}
                      strokeWidth={1.8}
                    />
                    <span className="truncate">{item.label}</span>
                  </>
                )}
              </NavLink>
            </div>
          );
        })}
      </nav>
    </aside>
  );
}
