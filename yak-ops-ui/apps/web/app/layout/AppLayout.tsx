import {
  DropdownMenu,
  DropdownMenuContent,
  DropdownMenuItem,
  DropdownMenuSeparator,
  DropdownMenuTrigger,
} from "@yak-ops/yak-ui";
import { ChevronDown, Database, LogOut } from "lucide-react";
import { Link, Outlet, useNavigate } from "react-router-dom";
import { useState } from "react";

import { logout } from "../../service/auth";
import { useAuth } from "@/hooks/use-auth";

export default function AppLayout() {
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

  return (
    <div className="flex h-screen overflow-hidden bg-[#f7f8f9] text-[#161823]">
      <aside className="flex w-52 shrink-0 flex-col bg-[#f3f3f5] px-5 py-4">
        <Link to="/data-source" className="flex h-12 items-center">
          <img
            src="/logo.png"
            alt="Yak Ops"
            draggable={false}
            className="h-8 w-auto select-none object-contain object-left"
          />
        </Link>

        <nav className="mt-6">
          <Link
            to="/data-source"
            className="flex h-10 items-center gap-3 rounded-lg bg-white px-3 text-sm font-semibold text-[#161823] shadow-[0_1px_4px_rgba(0,0,0,0.05)]"
          >
            <Database className="h-[17px] w-[17px]" strokeWidth={1.8} />
            <span>数据源管理</span>
          </Link>
        </nav>

        <div className="mt-auto">
          <DropdownMenu>
            <DropdownMenuTrigger className="flex h-11 w-full cursor-pointer items-center gap-2 rounded-lg border-0 bg-transparent px-2 text-left outline-none hover:bg-white/70 focus-visible:ring-2 focus-visible:ring-black/10">
              <span className="flex h-8 w-8 items-center justify-center rounded-full bg-white text-sm font-semibold shadow-sm">
                {(currentUser?.name ?? currentUser?.userName ?? "Y").slice(0, 1).toUpperCase()}
              </span>
              <span className="min-w-0 flex-1 truncate text-sm">
                {currentUser?.name ?? currentUser?.userName ?? "当前用户"}
              </span>
              <ChevronDown className="h-4 w-4 shrink-0 text-black/45" />
            </DropdownMenuTrigger>

            <DropdownMenuContent side="top" align="start" className="w-56">
              <div className="px-2.5 py-2">
                <div className="font-semibold text-[#161823]">
                  {currentUser?.name ?? currentUser?.userName ?? "当前用户"}
                </div>
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
      </aside>

      <main className="min-w-0 flex-1 overflow-y-auto">
        <Outlet />
      </main>
    </div>
  );
}
