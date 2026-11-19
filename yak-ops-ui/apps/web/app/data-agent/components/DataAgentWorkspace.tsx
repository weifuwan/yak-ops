import {
  Check,
  ChevronDown,
  FolderOpen,
  Sparkles,
} from "lucide-react";
import { useEffect, useRef, useState } from "react";

import { DataAgentPromptComposer } from "./DataAgentPromptComposer";
import { DataAgentSidebar } from "./DataAgentSidebar";

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
  const normalized = content.replace(/\s+/g, " ").trim();
  return normalized.length > 26 ? normalized.slice(0, 26) + "…" : normalized;
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
      <DataAgentSidebar
        conversations={conversations}
        activeConversationId={activeConversationId}
        onNewConversation={handleNewConversation}
        onSelectConversation={(id) => {
          setActiveConversationId(id);
          setModelMenuOpen(false);
        }}
      />

      <main className="flex min-h-0 min-w-0 flex-1 bg-[#f4f4f2] p-2 max-md:p-0">
        <section className="flex min-h-0 min-w-0 flex-1 flex-col overflow-hidden rounded-2xl border border-[#e7e7e4] bg-white max-md:rounded-none max-md:border-0">


          {!hasMessages ? (
            <div className="flex min-h-0 flex-1 flex-col items-center justify-center overflow-y-auto px-6 py-10">
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
              </div>
            </div>
          ) : (
            <div className="flex min-h-0 flex-1">
              <div className="flex min-h-0 min-w-0 flex-1 flex-col">
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
              </div>

              <aside
                aria-label="Agent 工作区"
                className="hidden min-h-0 w-[46%] min-w-[420px] max-w-[760px] shrink-0 flex-col border-l border-[#eeeeec] bg-[#fcfcfb] xl:flex"
              >
                <header className="flex h-14 shrink-0 items-center justify-between border-b border-[#eeeeec] px-4">
                  <span className="text-sm font-semibold text-[#20201e]">工作区</span>
                  <span className="rounded-full bg-[#f3f3f1] px-2 py-1 text-[10px] text-[#999999]">
                    暂无执行结果
                  </span>
                </header>
                <div className="flex min-h-0 flex-1 flex-col items-center justify-center px-8 text-center">
                  <span className="mb-4 flex size-11 items-center justify-center rounded-xl bg-white text-[#999999] shadow-[0_1px_3px_rgba(0,0,0,0.05)]">
                    <FolderOpen size={21} strokeWidth={1.6} />
                  </span>
                  <h3 className="m-0 text-sm font-medium text-[#555555]">暂无工作区文件</h3>
                  <p className="mb-0 mt-2 max-w-[250px] text-xs leading-6 text-[#a0a0a0]">
                    Agent 生成的文件、数据结果和执行产物将在这里展示。
                  </p>
                </div>
              </aside>
            </div>
          )}
        </section>
      </main>
    </div>
  );
}

export default DataAgentWorkspace;
