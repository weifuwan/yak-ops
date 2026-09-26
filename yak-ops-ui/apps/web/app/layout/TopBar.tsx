import {
  Button,
  Dialog,
  DialogContent,
  DialogDescription,
  DialogTitle,
  DropdownMenu,
  DropdownMenuContent,
  DropdownMenuGroup,
  DropdownMenuItem,
  DropdownMenuLabel,
  DropdownMenuSeparator,
  DropdownMenuTrigger,
  Input,
  toast,
} from "@yak-ops/yak-ui";
import { Check, ChevronDown, LogOut, Menu, Plus, X } from "lucide-react";
import { useState } from "react";
import { Link, useNavigate } from "react-router-dom";

import { useAuth } from "@/hooks/use-auth";
import { useWorkspace } from "@/hooks/use-workspace";
import { logout } from "@/service/auth";

type TopBarProps = {
  launcherOpen: boolean;
  onToggleLauncher: () => void;
  productLabel: string;
  productPath: string;
  showWorkspaceSwitcher?: boolean;
};

export default function TopBar({
  launcherOpen,
  onToggleLauncher,
  productLabel,
  productPath,
  showWorkspaceSwitcher = false,
}: TopBarProps) {
  const navigate = useNavigate();
  const { currentUser, clearCurrentUser } = useAuth();
  const {
    workspaces,
    currentWorkspace,
    loading: workspaceLoading,
    selectWorkspace,
    createWorkspace,
    clearWorkspace,
  } = useWorkspace();
  const [loggingOut, setLoggingOut] = useState(false);
  const [createWorkspaceOpen, setCreateWorkspaceOpen] = useState(false);
  const [workspaceName, setWorkspaceName] = useState("");
  const [creatingWorkspace, setCreatingWorkspace] = useState(false);

  const handleLogout = async () => {
    if (loggingOut) return;
    setLoggingOut(true);
    try {
      await logout();
    } finally {
      clearWorkspace();
      clearCurrentUser();
      navigate("/login", { replace: true });
    }
  };

  const handleCreateWorkspace = async () => {
    const name = workspaceName.trim();
    if (!name || creatingWorkspace) return;

    setCreatingWorkspace(true);
    try {
      await createWorkspace({ name });
      setWorkspaceName("");
      setCreateWorkspaceOpen(false);
      toast.success("工作空间创建成功");
    } finally {
      setCreatingWorkspace(false);
    }
  };

  const displayName = currentUser?.name ?? currentUser?.userName ?? "当前用户";
  const initial = displayName.slice(0, 1).toUpperCase();
  const workspaceLabel = workspaceLoading ? "加载中…" : (currentWorkspace?.name ?? "选择工作空间");

  return (
    <>
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
        </div>

        <div className="flex min-w-0 flex-1 items-center justify-between px-4">
          <div className="flex min-w-0 items-center gap-3 text-xs">
            <span className="truncate font-semibold text-white/90">{productLabel}</span>
            {showWorkspaceSwitcher ? (
              <>
                <span className="h-3 w-px shrink-0 bg-white/15" />

                <DropdownMenu>
                  <DropdownMenuTrigger
                    disabled={workspaceLoading}
                    className="flex h-7 max-w-52 cursor-pointer items-center gap-1.5 rounded-md border-0 bg-transparent px-2 text-left text-white outline-none hover:bg-white/8 focus-visible:ring-2 focus-visible:ring-white/20 disabled:cursor-default disabled:opacity-60"
                  >
                    <span className="min-w-0 flex-1 truncate text-xs text-white/70">
                      {workspaceLabel}
                    </span>
                    <ChevronDown className="h-3.5 w-3.5 shrink-0 text-white/35" />
                  </DropdownMenuTrigger>

                  <DropdownMenuContent side="bottom" align="start" className="w-64">
                    <DropdownMenuGroup>
                      <DropdownMenuLabel>工作空间</DropdownMenuLabel>

                      {workspaces.length > 0 ? (
                        workspaces.map((workspace) => (
                          <DropdownMenuItem
                            key={workspace.id}
                            onClick={() => selectWorkspace(workspace.id)}
                          >
                            <span className="min-w-0 flex-1">
                              <span className="block truncate">{workspace.name}</span>
                              {workspace.roleName ? (
                                <span className="mt-0.5 block text-[11px] text-black/40">
                                  {workspace.roleName}
                                </span>
                              ) : null}
                            </span>
                            {workspace.id === currentWorkspace?.id ? (
                              <Check className="h-4 w-4 shrink-0 text-[#1645d1]" />
                            ) : null}
                          </DropdownMenuItem>
                        ))
                      ) : (
                        <DropdownMenuItem disabled>暂无工作空间</DropdownMenuItem>
                      )}
                    </DropdownMenuGroup>

                    <DropdownMenuSeparator />

                    <DropdownMenuItem onClick={() => setCreateWorkspaceOpen(true)}>
                      <Plus className="h-4 w-4" />
                      新建工作空间
                    </DropdownMenuItem>
                  </DropdownMenuContent>
                </DropdownMenu>
              </>
            ) : null}
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

      <Dialog
        open={createWorkspaceOpen}
        onOpenChange={(open) => {
          if (!creatingWorkspace) {
            setCreateWorkspaceOpen(open);
            if (!open) setWorkspaceName("");
          }
        }}
      >
        <DialogContent className="w-[420px]">
          <DialogTitle className="text-base font-semibold">新建工作空间</DialogTitle>
          <DialogDescription className="mt-2 text-sm leading-6 text-[#667085]">
            工作空间用于隔离数据源及后续的数据任务、质量规则等业务资源。
          </DialogDescription>

          <div className="mt-5">
            <div className="mb-1.5 text-sm font-medium text-[#344054]">工作空间名称</div>
            <Input
              autoFocus
              maxLength={128}
              value={workspaceName}
              placeholder="例如：Yak Ops"
              onChange={(event) => setWorkspaceName(event.target.value)}
              onKeyDown={(event) => {
                if (event.key === "Enter") void handleCreateWorkspace();
              }}
            />
          </div>

          <div className="mt-6 flex justify-end gap-2">
            <Button disabled={creatingWorkspace} onClick={() => setCreateWorkspaceOpen(false)}>
              取消
            </Button>
            <Button
              variant="primary"
              loading={creatingWorkspace}
              disabled={!workspaceName.trim()}
              onClick={() => void handleCreateWorkspace()}
            >
              创建
            </Button>
          </div>
        </DialogContent>
      </Dialog>
    </>
  );
}
