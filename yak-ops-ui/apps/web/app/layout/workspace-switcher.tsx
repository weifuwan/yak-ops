import {
  DropdownMenu,
  DropdownMenuContent,
  DropdownMenuGroup,
  DropdownMenuItem,
  DropdownMenuLabel,
  DropdownMenuSeparator,
  DropdownMenuTrigger,
} from "@yak-ops/yak-ui";
import { Check, ChevronDown, Plus } from "lucide-react";
import { useState } from "react";
import { useWorkspace } from "@/hooks/use-workspace";
import WorkspaceCreateDialog from "./workspace-create-dialog";

export default function WorkspaceSwitcher() {
  const { workspaces, currentWorkspace, loading, error, selectWorkspace, refreshWorkspaces } = useWorkspace();
  const [createOpen, setCreateOpen] = useState(false);
  const workspaceLabel = loading ? "加载中…" : (currentWorkspace?.name ?? "选择工作空间");

  return (
    <>
      <DropdownMenu>
        <DropdownMenuTrigger
          disabled={loading}
          className="flex h-7 max-w-52 cursor-pointer items-center gap-1.5 rounded-md border-0 bg-transparent px-2 text-left text-white outline-none hover:bg-white/8 focus-visible:ring-2 focus-visible:ring-white/20 disabled:cursor-default disabled:opacity-60"
        >
          <span className="min-w-0 flex-1 truncate text-xs text-white/70">{workspaceLabel}</span>
          <ChevronDown className="h-3.5 w-3.5 shrink-0 text-white/35" />
        </DropdownMenuTrigger>
        <DropdownMenuContent side="bottom" align="start" className="w-64">
          <DropdownMenuGroup>
            <DropdownMenuLabel>工作空间</DropdownMenuLabel>
            {workspaces.length ? (
              workspaces.map((workspace) => (
                <DropdownMenuItem key={workspace.id} onClick={() => selectWorkspace(workspace.id)}>
                  <span className="min-w-0 flex-1">
                    <span className="block truncate">{workspace.name}</span>
                    {workspace.roleName ? (
                      <span className="mt-0.5 block text-[11px] text-black/40">
                        {workspace.roleName}
                      </span>
                    ) : null}
                  </span>
                  {workspace.id === currentWorkspace?.id ? (
                    <Check className="h-4 w-4 shrink-0 text-[var(--yak-color-primary)]" />
                  ) : null}
                </DropdownMenuItem>
              ))
            ) : (
              <DropdownMenuItem disabled>暂无工作空间</DropdownMenuItem>
            )}
          </DropdownMenuGroup>
          <DropdownMenuSeparator />
          {error ? <DropdownMenuItem onClick={() => void refreshWorkspaces()}>重新加载工作空间</DropdownMenuItem> : null}
          <DropdownMenuItem onClick={() => setCreateOpen(true)}>
            <Plus className="h-4 w-4" />
            新建工作空间
          </DropdownMenuItem>
        </DropdownMenuContent>
      </DropdownMenu>
      <WorkspaceCreateDialog open={createOpen} onClose={() => setCreateOpen(false)} />
    </>
  );
}
