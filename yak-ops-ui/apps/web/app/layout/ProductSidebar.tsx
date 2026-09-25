import { NavLink } from "react-router-dom";

import { CURRENT_PRODUCT_LABEL, PRODUCT_NAVIGATION } from "./navigation";

export default function ProductSidebar() {
  return (
    <aside className="flex w-48 shrink-0 flex-col border-r border-[#e6e8eb] bg-[#f6f7f8]">
      <div className="px-3 pb-2 pt-4 text-[11px] font-medium text-[#8b929e]">
        {CURRENT_PRODUCT_LABEL}
      </div>

      <nav className="px-2">
        {PRODUCT_NAVIGATION.map((item) => {
          const Icon = item.icon;
          return (
            <NavLink
              key={item.path}
              to={item.path}
              className={({ isActive }) =>
                [
                  "flex h-9 items-center gap-2.5 rounded-md px-3 text-sm transition-colors",
                  isActive
                    ? "bg-[#e8edff] font-medium text-[#1d4ed8]"
                    : "text-[#4f5663] hover:bg-black/[0.035] hover:text-[#161823]",
                ].join(" ")
              }
            >
              <Icon className="h-4 w-4 shrink-0" strokeWidth={1.8} />
              <span className="truncate">{item.label}</span>
            </NavLink>
          );
        })}
      </nav>
    </aside>
  );
}
