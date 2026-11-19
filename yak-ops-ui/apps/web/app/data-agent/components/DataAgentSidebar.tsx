import {
  Check,
  ChevronDown,
  Clock3,
  LayoutGrid,
  MessageSquare,
  Search,
  Sparkles,
  SquarePen,
  Workflow,
  Zap,
} from "lucide-react";
import {
  DropdownMenu,
  DropdownMenuContent,
  DropdownMenuItem,
  DropdownMenuLabel,
  DropdownMenuSeparator,
  DropdownMenuTrigger,
} from "@yak-ops/yak-ui";
import { useState } from "react";

import { useAuth } from "@/hooks/use-auth";
import { useWorkspace } from "@/hooks/use-workspace";

type DataAgentSidebarConversation = {
  id: string;
  title: string;
  updatedAt: string;
};

type DataAgentSidebarProps = {
  conversations: DataAgentSidebarConversation[];
  activeConversationId: string | null;
  onNewConversation: () => void;
  onSelectConversation: (id: string) => void;
};

function formatConversationTime(value: string) {
  const date = new Date(value);
  if (Number.isNaN(date.getTime())) return "";
  return new Intl.DateTimeFormat("zh-CN", {
    hour: "2-digit",
    minute: "2-digit",
  }).format(date);
}

export function DataAgentSidebar({
  conversations,
  activeConversationId,
  onNewConversation,
  onSelectConversation,
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
  const [projectsExpanded, setProjectsExpanded] = useState(true);
  const [activeNavigation, setActiveNavigation] = useState("new");

  const workspaceName = loading
    ? "加载中…"
    : currentWorkspace?.name ?? "Yak-OPS";
  const workspaceInitial = workspaceName.trim().slice(0, 1).toUpperCase() || "Y";
  const userName = currentUser?.name ?? currentUser?.userName ?? "当前用户";
  const userInitial = userName.trim().slice(0, 1).toUpperCase() || "A";
  const filteredConversations = conversations.filter((conversation) =>
    conversation.title.toLowerCase().includes(searchTerm.trim().toLowerCase()),
  );

  const navItemClass = (active: boolean) =>
    "group flex h-8 w-full cursor-pointer items-center gap-2 rounded-lg px-2 text-left text-[13px] leading-none transition-colors focus-visible:outline-none focus-visible:ring-2 focus-visible:ring-inset focus-visible:ring-[#8b8b8b]/30 " +
    (active
      ? "bg-[#dedede] font-semibold text-[#20201e]"
      : "text-[#3f3f3f] hover:bg-[#e5e5e5] hover:text-[#20201e]");

  const selectConversation = (id: string) => {
    onSelectConversation(id);
    setActiveNavigation("chats");
    setSearchOpen(false);
    setSearchTerm("");
  };

  return (
    <aside
      aria-label="Data Agent sidebar"
      className="flex h-full w-64 shrink-0 flex-col bg-[#f0f0f0] px-2 pb-2 pt-3 text-[#20201e] max-md:hidden"
    >
      <div className="flex h-6 shrink-0 items-center px-2">
        <span
          aria-hidden="true"
          className="flex size-4 items-center justify-center rounded-[4px] bg-gradient-to-br from-[#ffb454] via-[#ff5c93] to-[#6d7bff] text-white"
        >
          <Sparkles size={10} strokeWidth={2.2} />
        </span>
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

      <nav aria-label="Data Agent navigation" className="mt-2 flex shrink-0 flex-col gap-0.5">
        <button
          type="button"
          aria-current={activeNavigation === "new" ? "page" : undefined}
          className={navItemClass(activeNavigation === "new")}
          onClick={() => {
            onNewConversation();
            setActiveNavigation("new");
            setSearchOpen(false);
            setSearchTerm("");
          }}
        >
          <SquarePen size={16} strokeWidth={1.7} className="shrink-0" />
          <span>New</span>
        </button>
        <button
          type="button"
          aria-expanded={searchOpen}
          className={navItemClass(activeNavigation === "search")}
          onClick={() => {
            setSearchOpen((open) => !open);
            setActiveNavigation("search");
            setSearchTerm("");
          }}
        >
          <Search size={17} strokeWidth={1.7} className="shrink-0" />
          <span>Search</span>
        </button>
        <button
          type="button"
          aria-expanded={projectsExpanded}
          className={navItemClass(activeNavigation === "projects")}
          onClick={() => {
            setProjectsExpanded((expanded) => !expanded);
            setActiveNavigation("projects");
          }}
        >
          <LayoutGrid size={16} strokeWidth={1.7} className="shrink-0" />
          <span>Projects</span>
        </button>
        <button
          type="button"
          aria-pressed={activeNavigation === "connectors"}
          className={navItemClass(activeNavigation === "connectors")}
          onClick={() => setActiveNavigation("connectors")}
        >
          <Workflow size={16} strokeWidth={1.7} className="shrink-0" />
          <span>Connectors</span>
        </button>
        <button
          type="button"
          aria-pressed={activeNavigation === "automations"}
          className={navItemClass(activeNavigation === "automations")}
          onClick={() => setActiveNavigation("automations")}
        >
          <Clock3 size={16} strokeWidth={1.7} className="shrink-0" />
          <span>Automations</span>
        </button>
      </nav>

      {searchOpen ? (
        <div className="mt-2 shrink-0 px-1">
          <input
            type="search"
            aria-label="Search conversations"
            autoFocus
            value={searchTerm}
            onChange={(event) => setSearchTerm(event.target.value)}
            placeholder="Search conversations"
            className="h-8 w-full rounded-lg border border-[#d5d5d5] bg-white px-2.5 text-xs text-[#303030] outline-none placeholder:text-[#999999] focus:border-[#a9a9a9]"
          />
          <div className="mt-1 max-h-40 overflow-y-auto">
            {filteredConversations.length > 0 ? (
              filteredConversations.map((conversation) => (
                <button
                  key={conversation.id}
                  type="button"
                  title={conversation.title}
                  className="flex h-8 w-full items-center gap-2 rounded-md px-2 text-left text-xs text-[#555555] transition-colors hover:bg-[#e5e5e5]"
                  onClick={() => selectConversation(conversation.id)}
                >
                  <MessageSquare size={13} className="shrink-0 text-[#888888]" />
                  <span className="min-w-0 flex-1 truncate">{conversation.title}</span>
                </button>
              ))
            ) : (
              <p className="m-0 px-2 py-2 text-[11px] leading-5 text-[#999999]">
                暂无匹配的对话
              </p>
            )}
          </div>
        </div>
      ) : null}

      <div className="mt-4 min-h-0 flex-1 overflow-y-auto px-2 pb-3 [scrollbar-width:thin]">
        <button
          type="button"
          aria-expanded={projectsExpanded}
          className="flex h-7 w-full cursor-pointer items-center justify-between rounded-md px-2 text-left text-xs font-medium text-[#777777] transition-colors hover:bg-[#e8e8e8] hover:text-[#444444]"
          onClick={() => setProjectsExpanded((expanded) => !expanded)}
        >
          <span>Projects</span>
          <ChevronDown
            size={13}
            className={
              "shrink-0 transition-transform duration-150 " +
              (projectsExpanded ? "rotate-0" : "-rotate-90")
            }
          />
        </button>

        {projectsExpanded ? (
          <div className="mt-0.5 flex flex-col gap-0.5">
            <button
              type="button"
              aria-pressed={activeNavigation === "project"}
              className={
                "flex h-8 w-full cursor-pointer items-center gap-2 rounded-md px-2 text-left text-xs transition-colors " +
                (activeNavigation === "project"
                  ? "bg-[#e1e1e1] text-[#20201e]"
                  : "text-[#444444] hover:bg-[#e5e5e5]")
              }
              onClick={() => setActiveNavigation("project")}
            >
              <span className="flex size-4 shrink-0 items-center justify-center rounded-[3px] border border-[#d5d5d5] bg-[#e7e7e7] text-[8px] font-medium text-[#777777]">
                D
              </span>
              <span className="min-w-0 flex-1 truncate">Data Sync Hub</span>
            </button>

            {conversations.length > 0 ? (
              <section aria-label="Recent conversations" className="mt-4">
                <div className="mb-1 flex h-6 items-center px-2 text-[11px] font-medium text-[#888888]">
                  Recent chats
                </div>
                <nav className="flex flex-col gap-0.5">
                  {conversations.map((conversation) => {
                    const active = conversation.id === activeConversationId;
                    return (
                      <button
                        key={conversation.id}
                        type="button"
                        title={conversation.title}
                        aria-current={active ? "page" : undefined}
                        className={
                          "flex h-8 w-full cursor-pointer items-center gap-2 rounded-md px-2 text-left text-xs transition-colors " +
                          (active
                            ? "bg-white font-medium text-[#20201e]"
                            : "text-[#666666] hover:bg-[#e5e5e5] hover:text-[#20201e]")
                        }
                        onClick={() => selectConversation(conversation.id)}
                      >
                        <MessageSquare size={13} className="shrink-0 text-[#999999]" />
                        <span className="min-w-0 flex-1 truncate">{conversation.title}</span>
                        <span className="shrink-0 text-[10px] text-[#aaaaaa]">
                          {formatConversationTime(conversation.updatedAt)}
                        </span>
                      </button>
                    );
                  })}
                </nav>
              </section>
            ) : null}
          </div>
        ) : null}
      </div>

      <footer className="mt-auto shrink-0 px-1">
        <div className="flex h-[58px] items-center justify-between rounded-[14px] border border-[#ddddda] bg-[#fafafa] px-3 shadow-[0_1px_2px_rgba(0,0,0,0.025)]">
          <div className="min-w-0">
            <div className="truncate text-xs font-semibold text-[#20201e]">
              Upgrade to Pro
            </div>
            <div className="truncate text-[10px] leading-4 text-[#777777]">
              Unlock more features
            </div>
          </div>
          <span className="ml-2 flex size-8 shrink-0 items-center justify-center rounded-full bg-[#eee2ff] text-[#7541d8]">
            <Zap size={17} fill="currentColor" strokeWidth={1.8} />
          </span>
        </div>

        <div className="flex h-8 items-center px-1.5 pt-2">
          <span
            title={userName}
            aria-label={userName}
            className="relative flex size-5 items-center justify-center rounded-full bg-[#ed3f80] text-[10px] font-semibold text-white"
          >
            {userInitial}
            <span className="absolute -right-0.5 -top-0.5 size-2 rounded-full border border-[#f0f0f0] bg-[#f04452]" />
          </span>
        </div>
      </footer>
    </aside>
  );
}

export default DataAgentSidebar;
