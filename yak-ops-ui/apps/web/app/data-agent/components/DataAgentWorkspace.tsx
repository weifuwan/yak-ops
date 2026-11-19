import {
  Check,
  ChevronDown,
  Clock3,
  MessageSquare,
  Plus,
  Sparkles,
} from "lucide-react";
import { useEffect, useRef, useState } from "react";

import { DataAgentPromptComposer } from "./DataAgentPromptComposer";

type LocalMessage = {
  id: string;
  content: string;
  createdAt: string;
};

type LocalConversation = {
  id: string;
  title: string;
  updatedAt: string;
  messages: LocalMessage[];
};

function conversationTitle(content: string) {
  const normalized = content.replace(/\\s+/g, " ").trim();
  return normalized.length > 26 ? normalized.slice(0, 26) + "…" : normalized;
}

function formatConversationTime(value: string) {
  const date = new Date(value);
  if (Number.isNaN(date.getTime())) return "";
  return new Intl.DateTimeFormat("zh-CN", {
    hour: "2-digit",
    minute: "2-digit",
  }).format(date);
}

function ModelSelector({
  value,
  open,
  onToggle,
  onSelect,
}: {
  value: string;
  open: boolean;
  onToggle: () => void;
  onSelect: (value: string) => void;
}) {
  return (
    <div className="relative">
      <button
        type="button"
        aria-haspopup="menu"
        aria-expanded={open}
        className="flex h-8 cursor-pointer items-center gap-1.5 rounded-full px-2.5 text-xs font-medium text-[#626262] transition-colors hover:bg-black/[0.045] hover:text-[#20201e] focus-visible:outline-none focus-visible:ring-2 focus-visible:ring-[#fe2c55]/30"
        onClick={onToggle}
      >
        <Sparkles size={13} strokeWidth={1.8} />
        <span>{value}</span>
        <ChevronDown size={13} />
      </button>

      {open && (
        <div
          role="menu"
          aria-label="选择模型"
          className="absolute bottom-full right-0 z-30 mb-2 w-56 rounded-xl border border-[#e8e8e5] bg-white p-1.5 shadow-[0_8px_28px_rgba(0,0,0,0.12)]"
        >
          <button
            type="button"
            role="menuitemradio"
            aria-checked={value === "DeepSeek"}
            className="flex w-full cursor-pointer items-center justify-between rounded-lg px-3 py-2 text-left text-sm text-[#20201e] hover:bg-[#f5f5f3]"
            onClick={() => onSelect("DeepSeek")}
          >
            <span>DeepSeek</span>
            {value === "DeepSeek" ? <Check size={15} className="text-[#fe2c55]" /> : null}
          </button>
          <p className="m-0 px-3 pb-2 pt-1 text-[11px] leading-5 text-[#999999]">
            模型服务将在后端接入后启用
          </p>
        </div>
      )}
    </div>
  );
}

export function DataAgentWorkspace() {
  const [conversations, setConversations] = useState<LocalConversation[]>([]);
  const [activeConversationId, setActiveConversationId] = useState<string | null>(null);
  const [selectedModel, setSelectedModel] = useState("DeepSeek");
  const [modelMenuOpen, setModelMenuOpen] = useState(false);
  const messageScrollRef = useRef<HTMLDivElement>(null);

  const activeConversation =
    conversations.find((conversation) => conversation.id === activeConversationId) ?? null;
  const messages = activeConversation?.messages ?? [];
  const hasMessages = messages.length > 0;

  useEffect(() => {
    const container = messageScrollRef.current;
    if (!container || !hasMessages) return;
    container.scrollTop = container.scrollHeight;
  }, [activeConversationId, hasMessages, messages.length]);

  function handleNewConversation() {
    setActiveConversationId(null);
    setModelMenuOpen(false);
  }

  function handleSubmit(content: string) {
    const now = new Date().toISOString();
    const message: LocalMessage = {
      id: globalThis.crypto.randomUUID(),
      content,
      createdAt: now,
    };

    if (activeConversationId) {
      setConversations((current) =>
        current.map((conversation) =>
          conversation.id === activeConversationId
            ? {
                ...conversation,
                title:
                  conversation.messages.length === 0
                    ? conversationTitle(content)
                    : conversation.title,
                updatedAt: now,
                messages: [...conversation.messages, message],
              }
            : conversation,
        ),
      );
      return true;
    }

    const id = globalThis.crypto.randomUUID();
    setConversations((current) => [
      {
        id,
        title: conversationTitle(content),
        updatedAt: now,
        messages: [message],
      },
      ...current,
    ]);
    setActiveConversationId(id);
    return true;
  }

  const modelSelector = (
    <ModelSelector
      value={selectedModel}
      open={modelMenuOpen}
      onToggle={() => setModelMenuOpen((current) => !current)}
      onSelect={(value) => {
        setSelectedModel(value);
        setModelMenuOpen(false);
      }}
    />
  );

  return (
    <div className="flex min-h-0 min-w-0 flex-1 overflow-hidden bg-[#f4f4f2] text-[#20201e]">
      <aside className="flex h-full w-[252px] shrink-0 flex-col bg-[#f4f4f2] px-3 pb-3 max-md:hidden">
        <div className="flex h-14 shrink-0 items-center gap-2 px-2">
          <span className="flex size-8 items-center justify-center rounded-xl bg-white text-[#fe2c55] shadow-[0_1px_3px_rgba(0,0,0,0.06)]">
            <Sparkles size={18} strokeWidth={1.7} />
          </span>
          <div className="min-w-0">
            <div className="truncate text-sm font-semibold tracking-[-0.02em] text-[#20201e]">
              Data Agent
            </div>
            <div className="text-[11px] leading-4 text-[#999999]">Yak-OPS 智能助手</div>
          </div>
        </div>

        <button
          type="button"
          className="mt-2 flex h-9 w-full cursor-pointer items-center justify-center gap-2 rounded-xl bg-[#20201e] px-3 text-[13px] font-medium text-white transition-colors hover:bg-[#343431] focus-visible:outline-none focus-visible:ring-2 focus-visible:ring-[#20201e]/25"
          onClick={handleNewConversation}
        >
          <Plus size={15} />
          新建对话
        </button>

        <section className="mt-6 min-h-0 flex-1 overflow-y-auto scrollbar-subtle">
          <div className="mb-2 flex items-center gap-1.5 px-2 text-xs font-medium text-[#888888]">
            <Clock3 size={13} />
            最近对话
          </div>

          {conversations.length === 0 ? (
            <p className="m-0 px-2 py-2 text-xs leading-5 text-[#aaaaaa]">
              暂无对话，输入问题后会显示在这里
            </p>
          ) : (
            <nav aria-label="最近对话" className="flex flex-col gap-1">
              {conversations.map((conversation) => {
                const active = conversation.id === activeConversationId;
                return (
                  <button
                    key={conversation.id}
                    type="button"
                    title={conversation.title}
                    aria-current={active ? "page" : undefined}
                    className={
                      "flex h-9 w-full cursor-pointer items-center gap-2 rounded-lg px-2 text-left text-xs transition-colors " +
                      (active
                        ? "bg-white font-medium text-[#20201e] shadow-[0_1px_2px_rgba(0,0,0,0.035)]"
                        : "text-[#666666] hover:bg-white/70 hover:text-[#20201e]")
                    }
                    onClick={() => {
                      setActiveConversationId(conversation.id);
                      setModelMenuOpen(false);
                    }}
                  >
                    <MessageSquare size={14} className="shrink-0 text-[#999999]" />
                    <span className="min-w-0 flex-1 truncate">{conversation.title}</span>
                    <span className="shrink-0 text-[10px] text-[#aaaaaa]">
                      {formatConversationTime(conversation.updatedAt)}
                    </span>
                  </button>
                );
              })}
            </nav>
          )}
        </section>

        <div className="shrink-0 border-t border-[#e7e7e4] px-2 pb-1 pt-3">
          <div className="flex items-center gap-2 text-xs font-medium text-[#666666]">
            <span className="size-1.5 rounded-full bg-[#d99a3d]" />
            前端预览模式
          </div>
          <p className="mb-0 mt-1 text-[11px] leading-5 text-[#999999]">
            对话仅保存在当前页面，尚未连接后端
          </p>
        </div>
      </aside>

      <main className="flex min-h-0 min-w-0 flex-1 bg-[#f4f4f2] p-2 max-md:p-0">
        <section className="flex min-h-0 min-w-0 flex-1 flex-col overflow-hidden rounded-2xl border border-[#e7e7e4] bg-white max-md:rounded-none max-md:border-0">
          <header className="flex h-14 shrink-0 items-center justify-between border-b border-[#eeeeec] px-5">
            <div className="flex min-w-0 items-center gap-2">
              <span className="truncate text-sm font-semibold tracking-[-0.01em] text-[#20201e]">
                {activeConversation?.title ?? "新对话"}
              </span>
              <ChevronDown size={14} className="shrink-0 text-[#aaaaaa]" />
            </div>
            <div className="flex items-center gap-2">
              <span className="rounded-full bg-[#f5f5f3] px-2.5 py-1 text-[11px] font-medium text-[#888888]">
                前端预览
              </span>
              <button
                type="button"
                className="hidden h-8 cursor-pointer items-center gap-1.5 rounded-lg px-2 text-xs text-[#666666] hover:bg-[#f5f5f3] max-md:flex"
                onClick={handleNewConversation}
              >
                <Plus size={14} />
                新对话
              </button>
            </div>
          </header>

          {!hasMessages ? (
            <div className="flex min-h-0 flex-1 flex-col items-center justify-center overflow-y-auto px-6 py-10">
              <section
                aria-labelledby="data-agent-title"
                className="mb-7 flex w-full max-w-2xl flex-col items-center text-center"
              >
                <div className="mb-5 flex size-12 items-center justify-center rounded-2xl bg-[#fff5f7] text-[#fe2c55]">
                  <Sparkles size={23} strokeWidth={1.65} />
                </div>
                <h1
                  id="data-agent-title"
                  className="m-0 text-[28px] font-semibold tracking-[-0.035em] text-[#171717] max-sm:text-2xl"
                >
                  你好，我是 Yak-OPS Data Agent
                </h1>
                <p className="mb-0 mt-3 text-sm leading-7 text-[#666666]">
                  我可以帮助你理解数据、构建任务，并协助分析数据开发中的问题。
                </p>
                <p className="mb-0 mt-1 text-xs leading-6 text-[#a0a0a0]">
                  智能对话与工具调用能力正在建设中，当前仅提供前端交互预览。
                </p>
              </section>

              <div className="w-full max-w-2xl">
                <DataAgentPromptComposer
                  onSubmit={handleSubmit}
                  trailingActions={modelSelector}
                  placeholderPrefix="告诉 Data Agent，"
                  placeholderSuggestions={[
                    "分析这张表的结构和字段含义",
                    "帮我排查数据同步任务失败的原因",
                    "根据业务需求编写 SQL",
                    "梳理一条数据开发工作流",
                  ]}
                />
                <p className="mb-0 mt-3 text-center text-[11px] text-[#aaaaaa]">
                  Enter 发送 · Shift + Enter 换行
                </p>
              </div>
            </div>
          ) : (
            <>
              <div
                ref={messageScrollRef}
                className="min-h-0 flex-1 overflow-y-auto overscroll-contain scrollbar-subtle"
              >
                <div className="mx-auto flex w-full max-w-3xl flex-col gap-6 px-6 py-8 max-sm:px-4">
                  {messages.map((message) => (
                    <article key={message.id} className="flex w-full justify-end">
                      <div className="max-w-[82%]">
                        <div className="rounded-[22px] rounded-br-md border border-[#e5e5e2] bg-[#f5f5f3] px-4 py-3 text-[15px] leading-7 text-[#20201e]">
                          <p className="m-0 whitespace-pre-wrap break-words">{message.content}</p>
                        </div>
                        <time
                          dateTime={message.createdAt}
                          className="mt-1 block text-right text-[11px] text-[#aaaaaa]"
                        >
                          {formatConversationTime(message.createdAt)}
                        </time>
                      </div>
                    </article>
                  ))}

                  <div
                    role="status"
                    className="flex items-start gap-2 rounded-xl border border-[#f0e5d7] bg-[#fffaf3] px-3.5 py-3 text-xs leading-5 text-[#92734c]"
                  >
                    <Sparkles size={14} className="mt-0.5 shrink-0" />
                    <span>
                      消息已在当前页面本地展示。Data Agent 后端接口尚未接入，因此暂时不会生成 AI 回复。
                    </span>
                  </div>
                </div>
              </div>

              <div className="shrink-0 px-6 pb-5 pt-3 max-sm:px-4">
                <div className="mx-auto w-full max-w-3xl">
                  <DataAgentPromptComposer
                    onSubmit={handleSubmit}
                    trailingActions={modelSelector}
                    placeholder="继续向 Data Agent 提问..."
                  />
                  <p className="mb-0 mt-2 text-center text-[11px] text-[#aaaaaa]">
                    当前为本地交互预览，刷新页面后对话不会保留
                  </p>
                </div>
              </div>
            </>
          )}
        </section>
      </main>
    </div>
  );
}

export default DataAgentWorkspace;
