import { Bot, Plus, Send, X } from "lucide-react";
import { useEffect, useRef, useState } from "react";

import { ChatMessageList } from "./ChatMessageList";
import { useAgentContext } from "./agent-context";
import { useAgentChat } from "./useAgentChat";

type AgentDrawerProps = {
  open: boolean;
  onClose: () => void;
};

export default function AgentDrawer({ open, onClose }: AgentDrawerProps) {
  const context = useAgentContext();
  const { items, sending, reset, send } = useAgentChat();
  const [draft, setDraft] = useState("");
  const listRef = useRef<HTMLDivElement | null>(null);

  useEffect(() => {
    const list = listRef.current;
    if (list) {
      list.scrollTop = list.scrollHeight;
    }
  }, [items]);

  useEffect(() => {
    if (!open) {
      return;
    }
    const onKeyDown = (event: KeyboardEvent) => {
      if (event.key === "Escape") {
        onClose();
      }
    };
    window.addEventListener("keydown", onKeyDown);
    return () => window.removeEventListener("keydown", onKeyDown);
  }, [onClose, open]);

  if (!open && items.length === 0) {
    // Nothing to preserve yet: skip mounting the panel entirely.
    return null;
  }

  const submit = () => {
    const value = draft.trim();
    if (!value || sending) {
      return;
    }
    setDraft("");
    void send(value);
  };

  return (
    <aside
      aria-hidden={!open}
      className={
        open
          ? "fixed bottom-0 right-0 top-[42px] z-40 flex w-[420px] flex-col border-l border-[#e3e6ea] bg-white"
          : "hidden"
      }
    >
      <header className="flex h-11 shrink-0 items-center justify-between border-b border-[#eef0f2] px-3">
        <div className="flex items-center gap-2">
          <Bot className="h-4 w-4 text-[#185FA5]" />
          <span className="text-[13px] font-semibold text-[#161823]">运维助手</span>
          <span className="rounded bg-[#eef4fb] px-1.5 py-0.5 text-[11px] text-[#185FA5]">
            数据同步
          </span>
        </div>
        <div className="flex items-center gap-1">
          <button
            type="button"
            onClick={reset}
            className="flex h-7 items-center gap-1 rounded px-2 text-[12px] text-[#5c6270] transition-colors hover:bg-[#f2f4f7] hover:text-[#161823]"
          >
            <Plus className="h-3.5 w-3.5" />
            新对话
          </button>
          <button
            type="button"
            onClick={onClose}
            aria-label="关闭助手"
            className="flex h-7 w-7 items-center justify-center rounded text-[#8a8f99] transition-colors hover:bg-[#f2f4f7] hover:text-[#161823]"
          >
            <X className="h-4 w-4" />
          </button>
        </div>
      </header>

      {context ? (
        <div className="flex shrink-0 items-center gap-1 border-b border-[#eef0f2] bg-[#fafbfc] px-3 py-1.5 text-[11px] text-[#5c6270]">
          <span className="text-[#98a1ab]">当前上下文</span>
          <span className="rounded bg-white px-1.5 py-0.5 text-[#185FA5] ring-1 ring-[#d9e6f5]">
            {context.label}
          </span>
        </div>
      ) : null}

      <div ref={listRef} className="min-h-0 flex-1 overflow-y-auto px-3 py-3">
        {items.length === 0 ? (
          <div className="mt-12 space-y-3 px-4 text-center">
            <Bot className="mx-auto h-8 w-8 text-[#c6ccd4]" />
            <p className="text-[13px] text-[#8a8f99]">我可以帮你排查数据同步任务，例如：</p>
            <div className="space-y-1.5 text-[12px] text-[#5c6270]">
              <p className="rounded-lg border border-[#eef0f2] bg-[#fafbfc] px-3 py-2">
                昨天 orders 同步任务为什么失败？
              </p>
              <p className="rounded-lg border border-[#eef0f2] bg-[#fafbfc] px-3 py-2">
                现在有哪些同步任务在运行？
              </p>
              <p className="rounded-lg border border-[#eef0f2] bg-[#fafbfc] px-3 py-2">
                orders 同步任务最近一次成功是什么时候？
              </p>
            </div>
          </div>
        ) : (
          <ChatMessageList items={items} />
        )}
      </div>

      <div className="shrink-0 border-t border-[#eef0f2] p-3">
        <div className="flex items-end gap-2">
          <textarea
            value={draft}
            onChange={(event) => setDraft(event.target.value)}
            onKeyDown={(event) => {
              if (event.key === "Enter" && !event.shiftKey) {
                event.preventDefault();
                submit();
              }
            }}
            rows={2}
            placeholder="描述你的问题，Enter 发送，Shift+Enter 换行"
            className="max-h-32 min-h-[44px] flex-1 resize-none rounded-lg border border-[#e3e6ea] px-3 py-2 text-[13px] leading-relaxed text-[#161823] outline-none placeholder:text-[#a9b1bb] focus:border-[#185FA5]"
          />
          <button
            type="button"
            onClick={submit}
            disabled={sending || !draft.trim()}
            aria-label="发送"
            className="flex h-9 w-9 shrink-0 items-center justify-center rounded-lg bg-[#185FA5] text-white transition-colors hover:bg-[#134d86] disabled:cursor-not-allowed disabled:bg-[#c3ccd6]"
          >
            <Send className="h-4 w-4" />
          </button>
        </div>
        <p className="mt-1.5 text-[11px] text-[#a9b1bb]">只读查询，不会修改任何任务</p>
      </div>
    </aside>
  );
}
