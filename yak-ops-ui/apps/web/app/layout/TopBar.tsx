import {
  DropdownMenu,
  DropdownMenuContent,
  DropdownMenuItem,
  DropdownMenuSeparator,
  DropdownMenuTrigger,
} from "@yak-ops/yak-ui";
import { ChevronDown, LogOut, Menu, X } from "lucide-react";
import { useState } from "react";
import { Link, useNavigate } from "react-router-dom";

import { useAuth } from "@/hooks/use-auth";
import { logout } from "@/service/auth";

import { CURRENT_PRODUCT_LABEL } from "./navigation";

type TopBarProps = {
  launcherOpen: boolean;
  onToggleLauncher: () => void;
};

export default function TopBar({ launcherOpen, onToggleLauncher }: TopBarProps) {
  const navigate = useNavigate();
  const { currentUser, clearCurrentUser } = useAuth();
  const [loggingOut, setLoggingOut] = useState(false);

  const handleLogout = async () => {
    if (loggingOut) return;
    setLoggingOut(true);
    try {
      await logout();
    } finally {
      clearCurrentUser();
      navigate("/login", { replace: true });
    }
  };

  const displayName = currentUser?.name ?? currentUser?.userName ?? "当前用户";
  const initial = displayName.slice(0, 1).toUpperCase();

  return (
    <header className="relative z-50 flex h-10 shrink-0 items-center bg-[#14171a] text-white">
      <div className="flex h-full w-48 shrink-0 items-center gap-2 border-r border-white/10 px-2">
        <button
          type="button"
          aria-label={launcherOpen ? "关闭全部产品" : "打开全部产品"}
          aria-expanded={launcherOpen}
          aria-haspopup="menu"
          aria-controls="global-product-launcher"
          className="flex h-8 w-8 shrink-0 items-center justify-center rounded-md text-white/65 transition-colors hover:bg-white/8 hover:text-white focus-visible:outline-none focus-visible:ring-2 focus-visible:ring-white/20"
          onClick={onToggleLauncher}
        >
          {launcherOpen ? <X className="h-4 w-4" /> : <Menu className="h-4 w-4" />}
        </button>

        <Link
          to="/data-source"
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
      </div>

      <div className="flex min-w-0 flex-1 items-center justify-between px-4">
        <div className="flex min-w-0 items-center gap-3 text-xs">
          <span className="truncate font-semibold text-white/90">{CURRENT_PRODUCT_LABEL}</span>
          <span className="h-3 w-px shrink-0 bg-white/15" />
          <span className="truncate text-white/45">统一工作空间</span>
        </div>

        <DropdownMenu>
          <DropdownMenuTrigger className="flex h-8 max-w-52 cursor-pointer items-center gap-2 rounded-md border-0 bg-transparent px-2 text-left text-white outline-none hover:bg-white/8 focus-visible:ring-2 focus-visible:ring-white/20">
            <span className="flex h-6 w-6 shrink-0 items-center justify-center rounded-full bg-white/12 text-[11px] font-semibold">
              {initial}
            </span>
            <span className="min-w-0 flex-1 truncate text-xs text-white/80">{displayName}</span>
            <ChevronDown className="h-3.5 w-3.5 shrink-0 text-white/40" />
          </DropdownMenuTrigger>

          <DropdownMenuContent side="bottom" align="end" className="w-56">
            <div className="px-2.5 py-2">
              <div className="font-semibold text-[#161823]">{displayName}</div>
              {currentUser?.email ? (
                <div className="mt-0.5 text-xs text-black/45">{currentUser.email}</div>
              ) : null}
            </div>

            <DropdownMenuSeparator />

            <DropdownMenuItem
              tone="danger"
              disabled={loggingOut}
              onClick={() => void handleLogout()}
            >
              <LogOut className="h-4 w-4" />
              {loggingOut ? "正在退出…" : "退出登录"}
            </DropdownMenuItem>
          </DropdownMenuContent>
        </DropdownMenu>
      </div>
    </header>
  );
}
