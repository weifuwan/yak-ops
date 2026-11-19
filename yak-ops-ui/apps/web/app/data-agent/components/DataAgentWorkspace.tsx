import {
  DropdownMenu,
  DropdownMenuContent,
  DropdownMenuItem,
  DropdownMenuTrigger,
} from "@yak-ops/yak-ui";
import {
  Activity,
  Check,
  ChevronDown,
  Code2,
  Database,
  PanelLeft,
  Sparkles,
  Workflow,
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

const QUICK_ACTIONS = [
  {
    id: "table",
    label: "分析数据表",
    prompt: "请帮我分析这张数据表的结构、字段含义和潜在问题。",
    Icon: Database,
    iconClass: "bg-[#fff0ed] text-[#df795c]",
  },
  {
    id: "sql",
    label: "生成 SQL",
    prompt: "请根据我的需求生成 SQL，并解释查询逻辑和关键字段。",
    Icon: Code2,
    iconClass: "bg-[#eaf2ff] text-[#5685c8]",
  },
  {
    id: "task",
    label: "排查任务异常",
    prompt: "请帮我分析任务运行日志，定位失败原因并给出修复建议。",
    Icon: Activity,
    iconClass: "bg-[#fff5df] text-[#c58a35]",
  },
  {
    id: "workflow",
    label: "梳理数据开发流程",
    prompt: "请根据我的业务需求，帮我梳理数据集成与工作流开发方案。",
    Icon: Workflow,
    iconClass: "bg-[#eaf6ef] text-[#4f9a70]",
  },
] as const;

function conversationTitle(content: string) {
  const normalized = content.replace(/\s+/g, " ").trim();
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
  onOpenChange,
  onSelect,
}: {
  value: string;
  open: boolean;
  onOpenChange: (open: boolean) => void;
  onSelect: (value: string) => void;
}) {
  return (
    <DropdownMenu open={open} onOpenChange={onOpenChange}>
      <DropdownMenuTrigger
        aria-label="选择模型"
        className={
          "flex h-8 cursor-pointer items-center gap-1.5 rounded-full px-2.5 text-xs font-medium transition-colors hover:bg-black/[0.045] hover:text-[#20201e] focus-visible:outline-none focus-visible:ring-2 focus-visible:ring-[#fe2c55]/30 " +
          (open ? "bg-[#f4f4f2] text-[#20201e]" : "text-[#626262]")
        }
      >
        <Sparkles size={13} strokeWidth={1.8} />
        <span>{value}</span>
        <ChevronDown
          size={13}
          className={"transition-transform duration-150 " + (open ? "rotate-180" : "")}
        />
      </DropdownMenuTrigger>

      <DropdownMenuContent
        side="bottom"
        align="end"
        sideOffset={8}
        className="w-56 rounded-[14px] border border-[#e8e8e5] bg-white p-1.5 text-[#20201e] shadow-[0_10px_30px_rgba(0,0,0,0.12)]"
      >
        <DropdownMenuItem
          role="menuitemradio"
          aria-checked={value === "DeepSeek"}
          className="min-h-[56px] items-start justify-between gap-3 rounded-[10px] px-3 py-2 data-[highlighted]:bg-[#f5f5f3]"
          onClick={() => onSelect("DeepSeek")}
        >
          <span className="min-w-0 flex-1">
            <span className="block text-[13px] font-medium leading-5">DeepSeek</span>
            <span className="mt-0.5 block text-[11px] leading-4 text-[#999999]">
              模型服务将在后端接入后启用
            </span>
          </span>
          {value === "DeepSeek" ? (
            <Check size={15} className="mt-0.5 shrink-0 text-[#fe2c55]" />
          ) : null}
        </DropdownMenuItem>
      </DropdownMenuContent>
    </DropdownMenu>
  );
}
export function DataAgentWorkspace({ initialPrompt = "" }: { initialPrompt?: string }) {
  const [conversations, setConversations] = useState<LocalConversation[]>([]);
  const [activeConversationId, setActiveConversationId] = useState<string | null>(null);
  const [selectedModel, setSelectedModel] = useState("DeepSeek");
  const [modelMenuOpen, setModelMenuOpen] = useState(false);
  const [sidebarOpen, setSidebarOpen] = useState(false);
  const [composerKey, setComposerKey] = useState(0);
  const [initialDraft, setInitialDraft] = useState(initialPrompt);
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
    setInitialDraft("");
    setComposerKey((current) => current + 1);
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

  function handlePromptSubmit(content: string) {
    const accepted = handleSubmit(content);
    if (accepted !== false) {
      setInitialDraft("");
    }
    return accepted;
  }

  function handleQuickAction(prompt: string) {
    setInitialDraft(prompt);
    setComposerKey((current) => current + 1);
  }

  const modelSelector = (
    <ModelSelector
      value={selectedModel}
      open={modelMenuOpen}
      onOpenChange={setModelMenuOpen}
      onSelect={(value) => {
        setSelectedModel(value);
        setModelMenuOpen(false);
      }}
    />
  );

  return (
    <div className="flex min-h-0 min-w-0 flex-1 overflow-hidden bg-[#f0f0f0] text-[#20201e]">
      <div
        aria-hidden={!sidebarOpen}
        className={
          "h-full shrink-0 overflow-hidden bg-[#f0f0f0] transition-[width] duration-[320ms] ease-[cubic-bezier(0.22,1,0.36,1)] motion-reduce:transition-none " +
          (sidebarOpen ? "w-64 max-md:w-0" : "w-0")
        }
      >
        <div
          ref={(element) => {
            if (element) element.inert = !sidebarOpen;
          }}
          className={
            "h-full w-64 transform-gpu transition-[transform,opacity] duration-[320ms] ease-[cubic-bezier(0.22,1,0.36,1)] motion-reduce:transition-none " +
            (sidebarOpen
              ? "translate-x-0 opacity-100 pointer-events-auto"
              : "-translate-x-3 opacity-0 pointer-events-none")
          }
        >
          <DataAgentSidebar
            conversations={conversations}
            activeConversationId={activeConversationId}
            onNewConversation={handleNewConversation}
            onSelectConversation={(id) => {
              setActiveConversationId(id);
              setModelMenuOpen(false);
            }}
          />
        </div>
      </div>

      <main className="flex min-h-0 min-w-0 flex-1 bg-[#f0f0f0] p-2 max-md:p-0">
        <section className="relative flex min-h-0 min-w-0 flex-1 flex-col overflow-hidden rounded-2xl border border-[#e7e7e4] bg-white max-md:rounded-none max-md:border-0">
          <div className="pointer-events-none absolute inset-x-0 top-0 z-30 flex items-center justify-between px-[7px] pt-2">
            <button
              type="button"
              aria-label={sidebarOpen ? "收起侧边栏" : "展开侧边栏"}
              aria-expanded={sidebarOpen}
              title={sidebarOpen ? "收起侧边栏" : "展开侧边栏"}
              className="pointer-events-auto flex size-7 cursor-pointer items-center justify-center rounded-lg border border-[#e1e5e4] bg-white text-[#66716f] shadow-[0_1px_2px_rgba(0,0,0,0.035)] transition-colors hover:border-[#cbd2d1] hover:bg-[#f8f9f8] focus-visible:outline-none focus-visible:ring-2 focus-visible:ring-[#fe2c55]/25"
              onClick={() => setSidebarOpen((open) => !open)}
            >
              <PanelLeft size={15} strokeWidth={1.5} />
            </button>

          </div>

          {!hasMessages ? (
            <div className="flex min-h-0 flex-1">
              <div className="flex min-h-0 min-w-0 flex-1 flex-col items-center justify-center overflow-y-auto px-6 py-10">
                <div className="w-full max-w-[540px]">
                  <DataAgentPromptComposer
                    key={composerKey}
                    initialValue={initialDraft}
                    onSubmit={handlePromptSubmit}
                    trailingActions={modelSelector}
                    placeholder="你想让 Data Agent 帮你完成什么？"
                  />

                  <div className="mt-3 grid grid-cols-2 gap-2">
                    {QUICK_ACTIONS.map((action) => {
                      const Icon = action.Icon;
                      return (
                        <button
                          key={action.id}
                          type="button"
                          className="flex h-10 min-w-0 cursor-pointer items-center gap-2 rounded-[12px] border border-[#e2e6e5] bg-white px-2.5 text-left text-[13px] font-medium text-[#26302f] transition-[border-color,background-color] duration-150 hover:border-[#cbd4d2] hover:bg-[#fafbfa] focus-visible:outline-none focus-visible:ring-2 focus-visible:ring-[#fe2c55]/25"
                          onClick={() => handleQuickAction(action.prompt)}
                        >
                          <span
                            className={
                              "flex size-5 shrink-0 items-center justify-center rounded-md " +
                              action.iconClass
                            }
                          >
                            <Icon size={12} strokeWidth={1.8} />
                          </span>
                          <span className="min-w-0 flex-1 truncate">{action.label}</span>
                        </button>
                      );
                    })}
                  </div>
                </div>
              </div>

            </div>
          ) : (
            <div className="flex min-h-0 flex-1">
              <div className="flex min-h-0 min-w-0 flex-1 flex-col">
                <div
                  ref={messageScrollRef}
                  className="min-h-0 flex-1 overflow-y-auto overscroll-contain scrollbar-subtle"
                >
                  <div className="mx-auto flex w-full max-w-3xl flex-col gap-6 px-6 pb-8 pt-14 max-sm:px-4">
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
                      key={composerKey}
                      initialValue={initialDraft}
                      onSubmit={handlePromptSubmit}
                      trailingActions={modelSelector}
                      placeholder="继续向 Data Agent 提问..."
                    />
                    <p className="mb-0 mt-2 text-center text-[11px] text-[#aaaaaa]">
                      当前为本地交互预览，刷新页面后对话不会保留
                    </p>
                  </div>
                </div>
              </div>

            </div>
          )}
        </section>
      </main>
    </div>
  );
}

export default DataAgentWorkspace;
