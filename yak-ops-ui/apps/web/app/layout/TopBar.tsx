import { Menu, X } from "lucide-react";
import { Link } from "react-router-dom";

import type { ProductModuleNavigationItem } from "./navigation";

import AccountMenu from "./account-menu";
import WorkspaceSwitcher from "./workspace-switcher";

type TopBarProps = {
  launcherOpen: boolean;
  onToggleLauncher: () => void;
  productLabel: string;
  productPath: string;
  moduleNavigation?: ProductModuleNavigationItem[];
  activeModuleId?: string;
};

/** The header composes product navigation and account actions. */
export default function TopBar({
  launcherOpen,
  onToggleLauncher,
  productLabel,
  productPath,
  moduleNavigation,
  activeModuleId,
}: TopBarProps) {
  return (
    <header className="relative z-50 flex h-[42px] shrink-0 items-center bg-[#14171a] text-white">
      <div className="flex h-full w-[260px] shrink-0 items-center border-r border-white/10">
        <button
          type="button"
          aria-label={launcherOpen ? "关闭全部产品" : "打开全部产品"}
          aria-expanded={launcherOpen}
          aria-haspopup="menu"
          aria-controls="global-product-launcher"
          className="flex h-full w-11 shrink-0 cursor-pointer items-center justify-center text-white/65 transition-colors hover:bg-white/8 hover:text-white focus-visible:outline-none focus-visible:ring-2 focus-visible:ring-inset focus-visible:ring-white/20"
          onClick={onToggleLauncher}
        >
          {launcherOpen ? <X className="h-4 w-4" /> : <Menu className="h-4 w-4" />}
        </button>
        <Link
          to={productPath}
          className="flex h-full min-w-0 flex-1 items-center gap-2 px-3"
          onClick={() => {
            if (launcherOpen) onToggleLauncher();
          }}
        >
          <img
            src="/logo.png"
            alt="Yak Ops"
            draggable={false}
            className="h-7 w-auto max-w-[112px] shrink-0 select-none object-contain brightness-0 invert"
          />
          {moduleNavigation?.length ? (
            <span className="min-w-0 truncate text-[13px] font-semibold text-white/90">
              {productLabel}
            </span>
          ) : null}
        </Link>
      </div>

      <div className="flex h-full w-[208px] shrink-0 items-center border-r border-white/10">
        <WorkspaceSwitcher />
      </div>

      {moduleNavigation?.length ? (
        <nav
          aria-label="数据建模模块"
          className="flex h-full min-w-0 flex-1 items-center gap-0 overflow-x-auto px-2 [scrollbar-width:none] [&::-webkit-scrollbar]:hidden"
        >
          {moduleNavigation.map((item) => {
            const active = item.id === activeModuleId;
            return (
              <Link
                key={item.id}
                to={item.path}
                target="_blank"
                rel="noopener noreferrer"
                aria-current={active ? "page" : undefined}
                className="flex h-full shrink-0 items-center px-3 text-xs font-medium text-white/65 transition-colors hover:bg-white/8 hover:text-white focus-visible:outline-none focus-visible:ring-2 focus-visible:ring-inset focus-visible:ring-white/25"
              >
                {item.label}
              </Link>
            );
          })}
        </nav>
      ) : (
        <div className="flex min-w-0 flex-1 items-center px-4">
          <span className="truncate text-xs font-semibold text-white/90">{productLabel}</span>
        </div>
      )}

      <div className="flex h-full shrink-0 items-center px-3">
        <AccountMenu />
      </div>
    </header>
  );
}
