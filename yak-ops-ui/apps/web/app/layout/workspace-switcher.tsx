import {
  DropdownMenu,
  DropdownMenuContent,
  DropdownMenuGroup,
  DropdownMenuItem,
  DropdownMenuLabel,
  DropdownMenuSeparator,
  DropdownMenuTrigger,
} from "@yak-ops/yak-ui";
import { Check, ChevronDown, Monitor, Plus } from "lucide-react";
import { useState } from "react";
import { useWorkspace } from "@/hooks/use-workspace";
import WorkspaceCreateDialog from "./workspace-create-dialog";

export default function WorkspaceSwitcher() {
  const {
    workspaces,
    currentWorkspace,
    loading,
    error,
    selectWorkspace,
    refreshWorkspaces,
  } = useWorkspace();
  const [createOpen, setCreateOpen] = useState(false);
  const workspaceName = loading
    ? "加载中…"
    : (currentWorkspace?.name ?? "选择工作空间");
  const workspaceCode = currentWorkspace?.id
    ? `编码：${currentWorkspace.id}`
    : error
      ? "工作空间加载失败"
      : "请选择工作空间";

  return (
    <>
      <DropdownMenu>
        <DropdownMenuTrigger
          disabled={loading}
          aria-label={`切换工作空间，当前：${workspaceName}`}
          className="group flex h-full w-full min-w-0 cursor-pointer items-center gap-2.5 rounded-none border-0 bg-transparent px-3 text-left text-white outline-none transition-colors hover:bg-white/6 focus-visible:bg-white/6 focus-visible:ring-2 focus-visible:ring-inset focus-visible:ring-white/20 disabled:cursor-default disabled:opacity-60"
        >
          <Monitor className="h-4 w-4 shrink-0 text-white/55 transition-colors group-hover:text-white/80" />
          <span className="flex min-w-0 flex-1 flex-col gap-1 leading-none">
            <span className="truncate text-xs font-medium text-white/90">
              {workspaceName}
            </span>
            <span className="truncate text-[10px] text-white/45">{workspaceCode}</span>
          </span>
          <ChevronDown className="h-3.5 w-3.5 shrink-0 text-white/40 transition-transform group-data-[state=open]:rotate-180" />
        </DropdownMenuTrigger>

        <DropdownMenuContent
          side="bottom"
          align="start"
          className="w-72 border border-[#3b414a] bg-[#20242b] p-1.5 text-white shadow-xl"
        >
          <DropdownMenuGroup>
            <DropdownMenuLabel className="px-2 py-1.5 text-[11px] font-medium tracking-wide text-white/45">
              切换工作空间
            </DropdownMenuLabel>
            {workspaces.length ? (
              workspaces.map((workspace) => (
                <DropdownMenuItem
                  key={workspace.id}
                  onClick={() => selectWorkspace(workspace.id)}
                  className="min-h-12 cursor-pointer gap-2 rounded-sm px-2 py-2 text-white/85 outline-none focus:bg-white/8 focus:text-white data-[highlighted]:bg-white/8"
                >
                  <span className="flex min-w-0 flex-1 flex-col gap-1">
                    <span className="block truncate text-xs font-medium text-white/90">
                      {workspace.name}
                    </span>
                    <span className="flex min-w-0 items-center gap-2 text-[10px] text-white/45">
                      <span className="truncate">编码：{workspace.id}</span>
                      {workspace.roleName ? (
                        <span className="shrink-0">{workspace.roleName}</span>
                      ) : null}
                    </span>
                  </span>
                  {workspace.id === currentWorkspace?.id ? (
                    <Check className="h-4 w-4 shrink-0 text-[var(--yak-color-primary)]" />
                  ) : null}
                </DropdownMenuItem>
              ))
            ) : (
              <DropdownMenuItem disabled className="text-xs text-white/45">
                暂无工作空间
              </DropdownMenuItem>
            )}
          </DropdownMenuGroup>

          <DropdownMenuSeparator className="my-1 bg-white/10" />

          {error ? (
            <DropdownMenuItem
              onClick={() => void refreshWorkspaces().catch(() => undefined)}
              className="cursor-pointer text-xs text-white/80 focus:bg-white/8 focus:text-white data-[highlighted]:bg-white/8"
            >
              重新加载工作空间
            </DropdownMenuItem>
          ) : null}
          <DropdownMenuItem
            onClick={() => setCreateOpen(true)}
            className="cursor-pointer gap-2 text-xs text-white/80 focus:bg-white/8 focus:text-white data-[highlighted]:bg-white/8"
          >
            <Plus className="h-4 w-4 text-white/55" />
            新建工作空间
          </DropdownMenuItem>
        </DropdownMenuContent>
      </DropdownMenu>

      <WorkspaceCreateDialog open={createOpen} onClose={() => setCreateOpen(false)} />
    </>
  );
}
