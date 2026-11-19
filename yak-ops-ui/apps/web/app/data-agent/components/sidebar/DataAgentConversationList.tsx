import { MessageSquare } from "lucide-react";

type SidebarConversation = {
  id: string;
  title: string;
  updatedAt: string;
};

type DataAgentConversationListProps = {
  conversations: SidebarConversation[];
  activeConversationId: string | null;
  busy: boolean;
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
  busy,
  onSelectConversation,
}: DataAgentConversationListProps) {
  return (
    <div className="mt-4 min-h-0 flex-1 overflow-y-auto px-2 pb-3 [scrollbar-width:thin]">
      <section aria-label="Recent conversations">
        <div className="mb-1 flex h-6 items-center px-2 text-[11px] font-medium text-[#888888]">
          Recent chats
        </div>
        {conversations.length > 0 ? (
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
        ) : (
          <p className="m-0 px-2 py-2 text-[11px] leading-5 text-[#999999]">
            No sessions yet
          </p>
        )}
      </section>
    </div>
  );
}
