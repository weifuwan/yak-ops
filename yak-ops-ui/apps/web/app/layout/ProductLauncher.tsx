import { ChevronRight } from "lucide-react";
import { useEffect, useState } from "react";
import { Link } from "react-router-dom";

import AllProductMenu from "./AllProductMenu";
import { GLOBAL_PRODUCT_MENU } from "./navigation";

type ProductLauncherProps = {
  open: boolean;
  onClose: () => void;
};

export default function ProductLauncher({ open, onClose }: ProductLauncherProps) {
  const [allProductsOpen, setAllProductsOpen] = useState(false);

  useEffect(() => {
    if (!open) setAllProductsOpen(false);
  }, [open]);

  const secondLevelOpen = open && allProductsOpen;

  return (
    <>
      <aside
        id="global-product-launcher"
        aria-label="全局产品一级菜单"
        aria-hidden={!open}
        className={[
          "user-menu fixed bottom-0 left-0 top-10 z-40 flex w-[220px] flex-col bg-[#15181c] text-xs text-white",
          "box-border transform-gpu transition-transform duration-300 ease-in-out motion-reduce:transition-none",
          open ? "user-menu-active translate-x-0" : "-translate-x-[220px] pointer-events-none",
        ].join(" ")}
      >
        <button
          type="button"
          aria-expanded={secondLevelOpen}
          aria-controls="all-product-menu"
          tabIndex={open ? 0 : -1}
          className={[
            "view-all mb-[7px] flex h-10 w-full shrink-0 cursor-pointer items-center border-0 px-0 text-left text-[#d3d3d3] transition-colors",
            "hover:bg-[#282b2e] hover:text-white focus-visible:outline-none focus-visible:ring-2 focus-visible:ring-inset focus-visible:ring-white/20",
            secondLevelOpen ? "bg-[#1c1e21] text-white" : "bg-transparent",
          ].join(" ")}
          onClick={() => setAllProductsOpen((value) => !value)}
        >
          <span className="text ml-[14px] min-w-0 flex-1 truncate">全部产品</span>
          <span className="right mr-3 flex shrink-0 items-center">
            <ChevronRight
              className={[
                "h-3.5 w-3.5",
                secondLevelOpen ? "text-white" : "text-white/65",
              ].join(" ")}
              strokeWidth={1.8}
            />
          </span>
        </button>

        <div className="item-list min-h-0 flex-1 overflow-y-auto px-2 [scrollbar-width:none] [&::-webkit-scrollbar]:hidden">
          {GLOBAL_PRODUCT_MENU.map((item) => {
            const Icon = item.icon;

            return (
              <Link
                key={item.id}
                to={item.path}
                tabIndex={open ? 0 : -1}
                className="item group relative my-0.5 flex h-8 w-full cursor-pointer items-center rounded px-1.5 text-[#cbced3] transition-colors hover:bg-[#282b2e] hover:text-white focus-visible:outline-none focus-visible:ring-2 focus-visible:ring-white/20"
                onClick={onClose}
              >
                <span className="info flex min-w-0 flex-1 items-center">
                  <Icon
                    className="h-3.5 w-3.5 shrink-0 text-[#5d6064] transition-colors group-hover:text-white"
                    strokeWidth={1.8}
                  />
                  <span className="product-name ml-2 truncate">{item.label}</span>
                </span>
              </Link>
            );
          })}
        </div>
      </aside>

      <AllProductMenu open={secondLevelOpen} onNavigate={onClose} />
    </>
  );
}
