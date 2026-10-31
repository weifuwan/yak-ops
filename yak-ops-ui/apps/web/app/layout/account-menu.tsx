import {
  DropdownMenu,
  DropdownMenuContent,
  DropdownMenuSeparator,
  DropdownMenuTrigger,
  DropdownMenuItem,
} from "@yak-ops/yak-ui";
import { ChevronDown, LogOut } from "lucide-react";
import { useState } from "react";
import { useNavigate } from "react-router-dom";
import { useAuth } from "@/hooks/use-auth";
import { useWorkspace } from "@/hooks/use-workspace";
import { logout } from "@/service/auth";
import { useLeaveGuard } from "@/context/leave-guard-context";

export default function AccountMenu() {
  const navigate = useNavigate();
  const { currentUser, clearCurrentUser } = useAuth();
  const { clearWorkspace } = useWorkspace();
  const { confirmLeave } = useLeaveGuard();
  const [loggingOut, setLoggingOut] = useState(false);
  const displayName = currentUser?.name ?? currentUser?.userName ?? "当前用户";
  const initial = displayName.slice(0, 1).toUpperCase();

  const handleLogout = async () => {
    if (loggingOut) return;
    if (!(await confirmLeave())) return;
    setLoggingOut(true);
    try {
      await logout();
    } finally {
      clearWorkspace();
      clearCurrentUser();
      navigate("/login", { replace: true });
    }
  };

  return (
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
        <DropdownMenuItem tone="danger" disabled={loggingOut} onClick={() => void handleLogout()}>
          <LogOut className="h-4 w-4" />
          {loggingOut ? "正在退出…" : "退出登录"}
        </DropdownMenuItem>
      </DropdownMenuContent>
    </DropdownMenu>
  );
}
