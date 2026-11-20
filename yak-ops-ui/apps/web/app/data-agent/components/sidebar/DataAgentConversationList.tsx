import { useMemo, useState } from "react";
import { MessageSquare, MoreHorizontal, Pencil, Trash2 } from "lucide-react";
import {
  DropdownMenu,
  DropdownMenuContent,
  DropdownMenuItem,
  DropdownMenuSeparator,
  DropdownMenuTrigger,
} from "@yak-ops/yak-ui";

type SidebarConversation = {
  id: string;
  title: string;
  preview?: string;
  updatedAt: string;
};

type DataAgentConversationListProps = {
  conversations: SidebarConversation[];
  activeConversationId: string | null;
  busy: boolean;
  onSelectConversation: (id: string) => void;
  onRenameConversation: (id: string, title: string) => Promise<void>;
  onDeleteConversation: (id: string) => Promise<void>;
};

type ConversationGroup = {
  id: "today" | "yesterday" | "week" | "older";
  label: string;
  conversations: SidebarConversation[];
};

function startOfDay(value: Date) {
  return new Date(value.getFullYear(), value.getMonth(), value.getDate()).getTime();
}

function groupId(value: string): ConversationGroup["id"] {
  const date = new Date(value);
  if (Number.isNaN(date.getTime())) return "older";
  const days = Math.floor((startOfDay(new Date()) - startOfDay(date)) / 86_400_000);
  if (days <= 0) return "today";
  if (days === 1) return "yesterday";
  if (days <= 7) return "week";
  return "older";
}

function formatConversationTime(value: string) {
  const date = new Date(value);
  if (Number.isNaN(date.getTime())) return "";
  const group = groupId(value);
  if (group === "today") {
    return new Intl.DateTimeFormat("zh-CN", {
      hour: "2-digit",
      minute: "2-digit",
    }).format(date);
  }
  if (group === "yesterday") return "昨天";
  if (group === "week") {
    return new Intl.DateTimeFormat("zh-CN", { weekday: "short" }).format(date);
  }
  return new Intl.DateTimeFormat("zh-CN", {
    month: "numeric",
    day: "numeric",
  }).format(date);
}

function errorMessage(error: unknown) {
  return error instanceof Error ? error.message : "操作失败，请稍后重试";
}

export function DataAgentConversationList({
  conversations,
  activeConversationId,
  busy,
  onSelectConversation,
  onRenameConversation,
  onDeleteConversation,
}: DataAgentConversationListProps) {
  const [openMenuId, setOpenMenuId] = useState<string | null>(null);

  const groups = useMemo<ConversationGroup[]>(() => {
    const definitions: ConversationGroup[] = [
      { id: "today", label: "Today", conversations: [] },
      { id: "yesterday", label: "Yesterday", conversations: [] },
      { id: "week", label: "Previous 7 days", conversations: [] },
      { id: "older", label: "Older", conversations: [] },
    ];
    const groupMap = new Map(definitions.map((group) => [group.id, group]));
    conversations.forEach((conversation) => {
      groupMap.get(groupId(conversation.updatedAt))?.conversations.push(conversation);
    });
    return definitions.filter((group) => group.conversations.length > 0);
  }, [conversations]);

  function rename(conversation: SidebarConversation) {
    setOpenMenuId(null);
    const nextTitle = window.prompt("重命名会话", conversation.title);
    if (!nextTitle?.trim()) return;
    void onRenameConversation(conversation.id, nextTitle.trim()).catch((error: unknown) => {
      window.alert(errorMessage(error));
    });
  }

  function remove(conversation: SidebarConversation) {
    setOpenMenuId(null);
    if (!window.confirm("确定删除会话“" + conversation.title + "”吗？删除后无法恢复。")) {
      return;
    }
    void onDeleteConversation(conversation.id).catch((error: unknown) => {
      window.alert(errorMessage(error));
    });
  }

  return (
    <div
      className="mt-4 min-h-0 flex-1 overflow-y-auto px-2 pb-3 [scrollbar-width:thin]"
      style={{ scrollbarColor: "var(--yak-color-scrollbar) transparent" }}
    >
      <section aria-label="Recent conversations">
        <div
          className="mb-1 flex h-6 items-center"
          style={{ fontSize: "0.875rem", lineHeight: "1.5", color: "oklch(50% .001 107)" }}
        >
          Recent chats
        </div>
        {groups.length > 0 ? (
          <div className="flex flex-col gap-3">
            {groups.map((group) => (
              <section key={group.id} aria-label={group.label}>
                <h3 className="mb-1 mt-0 px-2 text-[10px] font-medium uppercase tracking-[0.04em] text-[#999999]">
                  {group.label}
                </h3>
                <nav className="flex flex-col gap-0.5">
                  {group.conversations.map((conversation) => {
                    const active = conversation.id === activeConversationId;
                    const menuOpen = openMenuId === conversation.id;
                    return (
                      <div key={conversation.id} className="group relative">
                        <button
                          type="button"
                          title={conversation.title}
                          aria-current={active ? "page" : undefined}
                          disabled={busy}
                          className={
                            "flex min-h-10 w-full cursor-pointer items-center gap-2 rounded-md px-2 py-1.5 pr-8 text-left transition-colors " +
                            (active
                              ? "bg-white text-[#20201e]"
                              : "text-[#666666] hover:bg-[#e5e5e5] hover:text-[#20201e]") +
                            " disabled:cursor-not-allowed disabled:opacity-50"
                          }
                          onClick={() => {
                            setOpenMenuId(null);
                            onSelectConversation(conversation.id);
                          }}
                        >
                          <MessageSquare size={13} className="mt-0.5 shrink-0 text-[#999999]" />
                          <span className="flex min-w-0 flex-1 flex-col gap-0.5">
                            <span className="truncate text-xs leading-4">{conversation.title}</span>
                            <span className="truncate text-[10px] leading-3 text-[#a0a0a0]">
                              {conversation.preview || "暂无消息预览"}
                            </span>
                          </span>
                          <span className="shrink-0 self-start pt-0.5 text-[10px] text-[#aaaaaa]">
                            {formatConversationTime(conversation.updatedAt)}
                          </span>
                        </button>

                        <DropdownMenu
                          open={menuOpen}
                          onOpenChange={(open) => setOpenMenuId(open ? conversation.id : null)}
                        >
                          <DropdownMenuTrigger
                            type="button"
                            aria-label={"会话操作：" + conversation.title}
                            title="会话操作"
                            disabled={busy}
                            className={
                              "absolute right-1 top-1.5 flex size-6 cursor-pointer items-center justify-center rounded-md text-[#858585] outline-none transition-colors hover:bg-[#e5e5e5] hover:text-[#20201e] focus-visible:ring-2 focus-visible:ring-[#888888]/30 " +
                              (menuOpen ? "bg-[#e5e5e5] opacity-100 " : "opacity-0 group-hover:opacity-100 group-focus-within:opacity-100 ") +
                              "disabled:cursor-not-allowed disabled:opacity-40"
                            }
                          >
                            <MoreHorizontal size={15} />
                          </DropdownMenuTrigger>

                          <DropdownMenuContent
                            side="right"
                            align="start"
                            sideOffset={8}
                            className="w-48 rounded-xl border border-[#dededb] bg-white p-1.5 text-[#20201e] shadow-[0_8px_24px_rgba(0,0,0,0.12)]"
                          >
                            <DropdownMenuItem
                              disabled={busy}
                              onClick={() => rename(conversation)}
                              className="min-h-9 rounded-md px-2.5 py-2 text-[13px] text-[#333333] data-[highlighted]:bg-[#f1f1f1]"
                            >
                              <Pencil size={14} className="shrink-0 text-[#666666]" />
                              重命名
                            </DropdownMenuItem>
                            <DropdownMenuSeparator className="my-1 bg-[#eeeeec]" />
                            <DropdownMenuItem
                              disabled={busy}
                              tone="danger"
                              onClick={() => remove(conversation)}
                              className="min-h-9 rounded-md px-2.5 py-2 text-[13px] text-[#c44747] data-[highlighted]:bg-[#fff1f1]"
                            >
                              <Trash2 size={14} className="shrink-0" />
                              删除会话
                            </DropdownMenuItem>
                          </DropdownMenuContent>
                        </DropdownMenu>
                      </div>
                    );
                  })}
                </nav>
              </section>
            ))}
          </div>
        ) : (
          <p className="m-0 px-2 py-2 text-[11px] leading-5 text-[#999999]">
            No sessions yet
          </p>
        )}
      </section>
    </div>
  );
}
