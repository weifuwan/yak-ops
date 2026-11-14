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
    <header className="relative z-50 flex h-10 shrink-0 items-center bg-[#14171a] text-white">
      <div className="flex h-full w-48 shrink-0 items-center gap-2 border-r border-white/10 px-2">
        <button
          type="button"
          aria-label={launcherOpen ? "关闭全部产品" : "打开全部产品"}
          aria-expanded={launcherOpen}
          aria-haspopup="menu"
          aria-controls="global-product-launcher"
          className="flex h-8 w-8 shrink-0 cursor-pointer items-center justify-center rounded-md text-white/65 transition-colors hover:bg-white/8 hover:text-white focus-visible:outline-none focus-visible:ring-2 focus-visible:ring-white/20"
          onClick={onToggleLauncher}
        >
          {launcherOpen ? <X className="h-4 w-4" /> : <Menu className="h-4 w-4" />}
        </button>
        <Link
          to={productPath}
          className="flex min-w-0 items-center"
          onClick={() => {
            if (launcherOpen) onToggleLauncher();
          }}
        >
          <img
            src="/logo.png"
            alt="Yak Ops"
            draggable={false}
            className="h-6 w-auto select-none object-contain brightness-0 invert"
          />
        </Link>
        {moduleNavigation?.length ? (
          <span className="truncate text-xs font-semibold text-white/90">{productLabel}</span>
        ) : null}
      </div>

      {moduleNavigation?.length ? (
        <>
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
                  aria-current={active ? "page" : undefined}
                  className={[
                    "relative flex h-full shrink-0 items-center px-3 text-xs font-medium transition-colors hover:bg-white/8 hover:text-white focus-visible:outline-none focus-visible:ring-2 focus-visible:ring-inset focus-visible:ring-white/25",
                    active
                      ? "bg-white/10 text-white after:absolute after:inset-x-3 after:bottom-0 after:h-0.5 after:bg-[#fe2c55]"
                      : "text-white/65",
                  ].join(" ")}
                >
                  {item.label}
                </Link>
              );
            })}
          </nav>
          <div className="flex shrink-0 items-center gap-3 px-3">
            <WorkspaceSwitcher />
            <AccountMenu />
          </div>
        </>
      ) : (
        <div className="flex min-w-0 flex-1 items-center justify-between px-4">
          <span className="truncate text-xs font-semibold text-white/90">{productLabel}</span>
          <div className="flex min-w-0 items-center gap-3">
            <WorkspaceSwitcher />
            <AccountMenu />
          </div>
        </div>
      )}
    </header>
  );
}
