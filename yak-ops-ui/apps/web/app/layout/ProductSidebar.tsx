import { NavLink } from "react-router-dom";

import { CURRENT_PRODUCT_LABEL, PRODUCT_NAVIGATION } from "./navigation";

export default function ProductSidebar() {
  return (
    <aside className="flex w-48 shrink-0 flex-col border-r border-[#e6e8eb] bg-[#FAFAFA]">
      <div className="px-3 pb-2 pt-4 text-[11px] font-medium text-[#8b929e]">
        {CURRENT_PRODUCT_LABEL}
      </div>

      <nav>
        {PRODUCT_NAVIGATION.map((item) => {
          const Icon = item.icon;

          return (
            <NavLink
              key={item.path}
              to={item.path}
              className={({ isActive }) =>
                [
                  "flex h-8 items-center gap-2.5 border-r-2 px-[14px] text-sm font-semibold text-[#26282c] transition-colors",
                  isActive
                    ? "border-[#1645d1] bg-[#dfe6fa]"
                    : "border-transparent hover:bg-[#f2f2f2]",
                ].join(" ")
              }
            >
              <Icon className="h-4 w-4 shrink-0 text-[#1645d1]" strokeWidth={1.8} />
              <span className="truncate">{item.label}</span>
            </NavLink>
          );
        })}
      </nav>
    </aside>
  );
}
