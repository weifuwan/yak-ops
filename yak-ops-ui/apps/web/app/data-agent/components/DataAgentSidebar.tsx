import {
  Check,
  ChevronDown,
} from "lucide-react";
import {
  DropdownMenu,
  DropdownMenuContent,
  DropdownMenuGroup,
  DropdownMenuItem,
  DropdownMenuLabel,
  DropdownMenuSeparator,
  DropdownMenuTrigger,
} from "@yak-ops/yak-ui";
import { useState } from "react";

import { useAuth } from "@/hooks/use-auth";
import { useWorkspace } from "@/hooks/use-workspace";

import { DataAgentConversationList } from "./sidebar/DataAgentConversationList";
import { DataAgentSidebarFooter } from "./sidebar/DataAgentSidebarFooter";
import { DataAgentSidebarNavigation } from "./sidebar/DataAgentSidebarNavigation";

type DataAgentSidebarConversation = {
  id: string;
  title: string;
  updatedAt: string;
  preview?: string;
};

type DataAgentSidebarProps = {
  conversations: DataAgentSidebarConversation[];
  activeConversationId: string | null;
  onNewConversation: () => void;
  onSelectConversation: (id: string) => void;
  onRenameConversation: (id: string, title: string) => Promise<void>;
  onDeleteConversation: (id: string) => Promise<void>;
  busy?: boolean;
};

export function DataAgentSidebar({
  conversations,
  activeConversationId,
  onNewConversation,
  onSelectConversation,
  onRenameConversation,
  onDeleteConversation,
  busy = false,
}: DataAgentSidebarProps) {
  const {
    workspaces,
    currentWorkspace,
    loading,
    error,
    selectWorkspace,
    refreshWorkspaces,
  } = useWorkspace();
  const { currentUser } = useAuth();
  const [searchOpen, setSearchOpen] = useState(false);
  const [searchTerm, setSearchTerm] = useState("");
  const [activeNavigation, setActiveNavigation] = useState("new");

  const workspaceName = loading
    ? "加载中…"
    : currentWorkspace?.name ?? "Yak-OPS";
  const workspaceInitial = workspaceName.trim().slice(0, 1).toUpperCase() || "Y";
  const userName = currentUser?.name ?? currentUser?.userName ?? "当前用户";
  const userInitial = userName.trim().slice(0, 1).toUpperCase() || "A";

  function selectConversation(id: string) {
    onSelectConversation(id);
    setActiveNavigation("chats");
    setSearchOpen(false);
    setSearchTerm("");
  }

  return (
    <aside
      aria-label="Data Agent sidebar"
      className="flex h-full w-full min-w-0 shrink-0 flex-col bg-[#f0f0f0] px-2 pb-2 pt-3 text-[#20201e] max-md:hidden"
      style={{ paddingRight: 0 }}
    >
      <div className="flex h-6 shrink-0 items-center px-2">
        <img
          src="/yakable_logo.png"
          alt="Yakable"
          className="size-4 shrink-0 object-contain"
        />
      </div>

      <DropdownMenu>
        <DropdownMenuTrigger
          disabled={loading}
          aria-label={"切换工作空间，当前：" + workspaceName}
          className="mt-2 flex h-8 w-full min-w-0 cursor-pointer items-center gap-2 rounded-[10px] border border-[#bdbdbd] bg-[#f5f5f5] px-1.5 text-left text-[13px] text-[#303030] outline-none transition-colors hover:bg-white focus-visible:ring-2 focus-visible:ring-[#777777]/25 disabled:cursor-default disabled:opacity-60"
        >
          <span className="flex size-5 shrink-0 items-center justify-center rounded-[5px] bg-[#7944cf] text-[10px] font-semibold text-white">
            {workspaceInitial}
          </span>
          <span className="min-w-0 flex-1 truncate">{workspaceName}</span>
          <ChevronDown
            size={13}
            className="shrink-0 text-[#666666] transition-transform group-data-[state=open]:rotate-180"
          />
        </DropdownMenuTrigger>

        <DropdownMenuContent
          side="bottom"
          align="start"
          className="w-60 border border-[#dededb] bg-white p-1.5 text-[#20201e] shadow-[0_8px_24px_rgba(0,0,0,0.12)]"
        >
          <DropdownMenuGroup>
            <DropdownMenuLabel className="px-2 py-1.5 text-[11px] font-medium text-[#888888]">
              工作空间
            </DropdownMenuLabel>
            {workspaces.length > 0 ? (
              workspaces.map((workspace) => {
                const selected = workspace.id === currentWorkspace?.id;
                return (
                  <DropdownMenuItem
                    key={workspace.id}
                    onClick={() => selectWorkspace(workspace.id)}
                    className="min-h-9 cursor-pointer gap-2 rounded-md px-2 py-2 text-xs text-[#333333] outline-none data-[highlighted]:bg-[#f1f1f1]"
                  >
                    <span className="flex min-w-0 flex-1 flex-col gap-0.5">
                      <span className="truncate font-medium">{workspace.name}</span>
                      <span className="truncate text-[10px] text-[#999999]">
                        {workspace.id}
                      </span>
                    </span>
                    {selected ? <Check size={14} className="shrink-0 text-[#555555]" /> : null}
                  </DropdownMenuItem>
                );
              })
            ) : (
              <DropdownMenuItem disabled className="text-xs text-[#999999]">
                暂无工作空间
              </DropdownMenuItem>
            )}
          </DropdownMenuGroup>
          {error ? (
            <>
              <DropdownMenuSeparator className="my-1 bg-[#eeeeec]" />
              <DropdownMenuItem
                onClick={() => void refreshWorkspaces().catch(() => undefined)}
                className="cursor-pointer rounded-md px-2 py-2 text-xs text-[#555555] data-[highlighted]:bg-[#f1f1f1]"
              >
                重新加载工作空间
              </DropdownMenuItem>
            </>
          ) : null}
        </DropdownMenuContent>
      </DropdownMenu>

      <DataAgentSidebarNavigation
        conversations={conversations}
        busy={busy}
        activeNavigation={activeNavigation}
        searchOpen={searchOpen}
        searchTerm={searchTerm}
        onActiveNavigationChange={setActiveNavigation}
        onSearchOpenChange={setSearchOpen}
        onSearchTermChange={setSearchTerm}
        onNewConversation={onNewConversation}
        onSelectConversation={selectConversation}
        onRenameConversation={onRenameConversation}
        onDeleteConversation={onDeleteConversation}
      />

      <DataAgentConversationList
        conversations={conversations}
        activeConversationId={activeConversationId}
        busy={busy}
        onSelectConversation={selectConversation}
      />

      <DataAgentSidebarFooter
        userName={userName}
        userInitial={userInitial}
      />
    </aside>
  );
}

export default DataAgentSidebar;
