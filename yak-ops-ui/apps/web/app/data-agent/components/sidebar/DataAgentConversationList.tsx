import { useMemo, useState } from "react";
import { MessageSquare, MoreHorizontal, Pencil, Trash2 } from "lucide-react";
import {
  Button,
  DropdownMenu,
  DropdownMenuContent,
  DropdownMenuItem,
  DropdownMenuSeparator,
  DropdownMenuTrigger,
  Input,
  Modal,
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
  const [dialog, setDialog] = useState<
    | { type: "rename"; conversation: SidebarConversation }
    | { type: "delete"; conversation: SidebarConversation }
    | null
  >(null);
  const [renameTitle, setRenameTitle] = useState("");
  const [dialogPending, setDialogPending] = useState(false);
  const [dialogError, setDialogError] = useState("");

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

  function openRenameDialog(conversation: SidebarConversation) {
    setOpenMenuId(null);
    setRenameTitle(conversation.title);
    setDialogError("");
    setDialog({ type: "rename", conversation });
  }

  function openDeleteDialog(conversation: SidebarConversation) {
    setOpenMenuId(null);
    setDialogError("");
    setDialog({ type: "delete", conversation });
  }

  function closeDialog() {
    if (dialogPending) return;
    setDialog(null);
    setDialogError("");
  }

  async function submitRename() {
    if (dialog?.type !== "rename" || dialogPending) return;
    const nextTitle = renameTitle.trim();
    if (!nextTitle) {
      setDialogError("请输入会话名称");
      return;
    }
    if (nextTitle.length > 40) {
      setDialogError("会话名称不能超过 40 个字符");
      return;
    }
    if (nextTitle === dialog.conversation.title) {
      setDialog(null);
      return;
    }

    setDialogPending(true);
    setDialogError("");
    try {
      await onRenameConversation(dialog.conversation.id, nextTitle);
      setDialog(null);
    } catch (error) {
      setDialogError(errorMessage(error));
    } finally {
      setDialogPending(false);
    }
  }

  async function confirmDelete() {
    if (dialog?.type !== "delete" || dialogPending) return;
    setDialogPending(true);
    setDialogError("");
    try {
      await onDeleteConversation(dialog.conversation.id);
      setDialog(null);
    } catch (error) {
      setDialogError(errorMessage(error));
    } finally {
      setDialogPending(false);
    }
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
                            "flex h-9 w-full cursor-pointer items-center gap-2 rounded-md px-2 pr-8 text-left transition-colors " +
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
                          <MessageSquare size={13} className="shrink-0 text-[#999999]" />
                          <span className="min-w-0 flex-1 truncate text-xs leading-5">
                            {conversation.title}
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
                              onClick={() => openRenameDialog(conversation)}
                              className="min-h-9 rounded-md px-2.5 py-2 text-[13px] text-[#333333] data-[highlighted]:bg-[#f1f1f1]"
                            >
                              <Pencil size={14} className="shrink-0 text-[#666666]" />
                              重命名
                            </DropdownMenuItem>
                            <DropdownMenuSeparator className="my-1 bg-[#eeeeec]" />
                            <DropdownMenuItem
                              disabled={busy}
                              tone="danger"
                              onClick={() => openDeleteDialog(conversation)}
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

      <Modal
        open={dialog?.type === "rename"}
        centered
        width={420}
        title="重命名会话"
        onClose={closeDialog}
        footer={
          <>
            <Button size="sm" variant="secondary" isDisabled={dialogPending} onClick={closeDialog}>
              取消
            </Button>
            <Button
              size="sm"
              variant="primary"
              isPending={dialogPending}
              isDisabled={!renameTitle.trim()}
              onClick={() => void submitRename()}
            >
              保存
            </Button>
          </>
        }
      >
        <div className="space-y-2">
          <label htmlFor="data-agent-session-rename" className="block text-xs text-[var(--yak-components-panel-text)]">
            会话名称
          </label>
          <Input
            id="data-agent-session-rename"
            size="small"
            variant="outlined"
            value={renameTitle}
            maxLength={40}
            disabled={dialogPending}
            placeholder="请输入会话名称"
            onChange={(event) => {
              setRenameTitle(event.target.value);
              setDialogError("");
            }}
          />
          {dialogError ? (
            <p role="alert" className="m-0 text-xs text-[var(--yak-components-danger-text)]">
              {dialogError}
            </p>
          ) : null}
        </div>
      </Modal>

      <Modal
        open={dialog?.type === "delete"}
        centered
        width={420}
        title="删除会话"
        onClose={closeDialog}
        footer={
          <>
            <Button size="sm" variant="secondary" isDisabled={dialogPending} onClick={closeDialog}>
              取消
            </Button>
            <Button size="sm" variant="danger" isPending={dialogPending} onClick={() => void confirmDelete()}>
              删除
            </Button>
          </>
        }
      >
        <div className="space-y-2">
          <p className="m-0 text-sm leading-6 text-[var(--yak-components-muted-text)]">
            确定删除会话“{dialog?.type === "delete" ? dialog.conversation.title : ""}”吗？删除后无法恢复。
          </p>
          {dialogError ? (
            <p role="alert" className="m-0 text-xs text-[var(--yak-components-danger-text)]">
              {dialogError}
            </p>
          ) : null}
        </div>
      </Modal>
    </div>
  );
}
