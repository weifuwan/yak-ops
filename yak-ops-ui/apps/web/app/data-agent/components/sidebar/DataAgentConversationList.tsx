import { ChevronDown, MessageSquare } from "lucide-react";

type SidebarConversation = {
  id: string;
  title: string;
  updatedAt: string;
};

type DataAgentConversationListProps = {
  conversations: SidebarConversation[];
  activeConversationId: string | null;
  activeNavigation: string;
  projectsExpanded: boolean;
  busy: boolean;
  onProjectsExpandedChange: (expanded: boolean) => void;
  onActiveNavigationChange: (value: string) => void;
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

export function DataAgentConversationList({
  conversations,
  activeConversationId,
  activeNavigation,
  projectsExpanded,
  busy,
  onProjectsExpandedChange,
  onActiveNavigationChange,
  onSelectConversation,
}: DataAgentConversationListProps) {
  return (
    <div className="mt-4 min-h-0 flex-1 overflow-y-auto px-2 pb-3 [scrollbar-width:thin]">
      <button
        type="button"
        aria-expanded={projectsExpanded}
        className="flex h-7 w-full cursor-pointer items-center justify-between rounded-md px-2 text-left text-xs font-medium text-[#777777] transition-colors hover:bg-[#e8e8e8] hover:text-[#444444]"
        onClick={() => onProjectsExpandedChange(!projectsExpanded)}
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
            onClick={() => onActiveNavigationChange("project")}
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
                      disabled={busy}
                      className={
                        "flex h-8 w-full cursor-pointer items-center gap-2 rounded-md px-2 text-left text-xs transition-colors " +
                        (active
                          ? "bg-white font-medium text-[#20201e]"
                          : "text-[#666666] hover:bg-[#e5e5e5] hover:text-[#20201e]") +
                        " disabled:cursor-not-allowed disabled:opacity-50"
                      }
                      onClick={() => onSelectConversation(conversation.id)}
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
  );
}
