import { ChevronRight } from "lucide-react";
import { Link } from "react-router-dom";

import { GLOBAL_PRODUCT_MENU } from "./navigation";

type ProductLauncherProps = {
  open: boolean;
  onClose: () => void;
};

export default function ProductLauncher({ open, onClose }: ProductLauncherProps) {
  return (
    <aside
      id="global-product-launcher"
      aria-label="全局产品一级菜单"
      aria-hidden={!open}
      className={[
        "fixed bottom-0 left-0 top-10 z-40 flex w-48 flex-col bg-[#171a1e] text-white shadow-[8px_0_24px_rgba(0,0,0,0.2)]",
        "will-change-transform transition-transform duration-200 ease-out motion-reduce:transition-none",
        open ? "translate-x-0" : "-translate-x-full pointer-events-none",
      ].join(" ")}
    >
      <nav className="space-y-1 p-2" aria-label="产品菜单">
        {GLOBAL_PRODUCT_MENU.map((item) => {
          const Icon = item.icon;
          const itemClassName =
            "flex h-10 w-full cursor-pointer items-center gap-3 rounded-md px-3 text-left text-sm text-white/68 transition-colors hover:bg-white/8 hover:text-white focus-visible:outline-none focus-visible:ring-2 focus-visible:ring-white/20";

          if (item.path) {
            return (
              <Link
                key={item.id}
                to={item.path}
                tabIndex={open ? 0 : -1}
                className={itemClassName}
                onClick={onClose}
              >
                <Icon className="h-4 w-4 shrink-0" strokeWidth={1.8} />
                <span className="min-w-0 flex-1 truncate">{item.label}</span>
                <ChevronRight className="h-4 w-4 shrink-0 text-white/28" />
              </Link>
            );
          }

          return (
            <div
              key={item.id}
              className="flex h-10 items-center gap-3 rounded-md px-3 text-sm text-white/68"
            >
              <Icon className="h-4 w-4 shrink-0" strokeWidth={1.8} />
              <span className="min-w-0 flex-1 truncate">{item.label}</span>
              <ChevronRight className="h-4 w-4 shrink-0 text-white/28" />
            </div>
          );
        })}
      </nav>
    </aside>
  );
}
