import {
  Clock3,
  MessageSquare,
  Search,
  SquarePen,
  Workflow,
} from "lucide-react";

type SidebarConversation = {
  id: string;
  title: string;
  updatedAt: string;
};

type DataAgentSidebarNavigationProps = {
  conversations: SidebarConversation[];
  busy: boolean;
  activeNavigation: string;
  searchOpen: boolean;
  searchTerm: string;
  onActiveNavigationChange: (value: string) => void;
  onSearchOpenChange: (open: boolean) => void;
  onSearchTermChange: (value: string) => void;
  onNewConversation: () => void;
  onSelectConversation: (id: string) => void;
};

function navItemClass(active: boolean) {
  return (
    "group flex h-8 w-full cursor-pointer items-center gap-2 rounded-lg px-2 text-left text-[13px] leading-none transition-colors focus-visible:outline-none focus-visible:ring-2 focus-visible:ring-inset focus-visible:ring-[#8b8b8b]/30 " +
    (active
      ? "bg-[#dedede] font-semibold text-[#20201e]"
      : "text-[#3f3f3f] hover:bg-[#e5e5e5] hover:text-[#20201e]")
  );
}

export function DataAgentSidebarNavigation({
  conversations,
  busy,
  activeNavigation,
  searchOpen,
  searchTerm,
  onActiveNavigationChange,
  onSearchOpenChange,
  onSearchTermChange,
  onNewConversation,
  onSelectConversation,
}: DataAgentSidebarNavigationProps) {
  const filteredConversations = conversations.filter((conversation) =>
    conversation.title.toLowerCase().includes(searchTerm.trim().toLowerCase()),
  );

  function selectConversation(id: string) {
    onSelectConversation(id);
    onActiveNavigationChange("chats");
    onSearchOpenChange(false);
    onSearchTermChange("");
  }

  return (
    <>
      <nav aria-label="Data Agent navigation" className="mt-2 flex shrink-0 flex-col gap-0.5">
        <button
          type="button"
          aria-current={activeNavigation === "new" ? "page" : undefined}
          disabled={busy}
          className={navItemClass(activeNavigation === "new") + " disabled:cursor-not-allowed disabled:opacity-50"}
          onClick={() => {
            onNewConversation();
            onActiveNavigationChange("new");
            onSearchOpenChange(false);
            onSearchTermChange("");
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
            onSearchOpenChange(!searchOpen);
            onActiveNavigationChange("search");
            onSearchTermChange("");
          }}
        >
          <Search size={17} strokeWidth={1.7} className="shrink-0" />
          <span>Search</span>
        </button>
        <button
          type="button"
          aria-pressed={activeNavigation === "connectors"}
          className={navItemClass(activeNavigation === "connectors")}
          onClick={() => onActiveNavigationChange("connectors")}
        >
          <Workflow size={17} strokeWidth={1.7} className="shrink-0" />
          <span>Connectors</span>
        </button>
        <button
          type="button"
          aria-pressed={activeNavigation === "automations"}
          className={navItemClass(activeNavigation === "automations")}
          onClick={() => onActiveNavigationChange("automations")}
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
            onChange={(event) => onSearchTermChange(event.target.value)}
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
                  disabled={busy}
                  className="flex h-8 w-full cursor-pointer items-center gap-2 rounded-md px-2 text-left text-xs text-[#555555] transition-colors hover:bg-[#e5e5e5] disabled:cursor-not-allowed disabled:opacity-50"
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
    </>
  );
}
