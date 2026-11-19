import {
  DropdownMenu,
  DropdownMenuContent,
  DropdownMenuItem,
  DropdownMenuTrigger,
} from "@yak-ops/yak-ui";
import {
  Activity,
  AlertCircle,
  Check,
  CheckCircle2,
  ChevronDown,
  Code2,
  Database,
  LoaderCircle,
  PanelLeft,
  ShieldCheck,
  Sparkles,
  Workflow,
  XCircle,
  Wrench,
} from "lucide-react";
import { useEffect, useRef, useState } from "react";

import { handleAuthenticationFailure } from "@/service/http/request";
import {
  createAgentSession,
  DataAgentApiError,
  listAgentSessions,
  sendAgentMessage,
  type AgentStreamEvent,
  type AgentToolCallRequest,
} from "@/service/data-agent";

import { DataAgentPromptComposer } from "./DataAgentPromptComposer";
import { DataAgentSidebar } from "./DataAgentSidebar";
import { DataAgentSidebarResizer } from "./DataAgentSidebarResizer";

type ToolCallStatus =
  | "running"
  | "completed"
  | "awaiting-confirmation"
  | "approved"
  | "rejected";

type AgentToolCall = AgentToolCallRequest & {
  status: ToolCallStatus;
};

type AgentMessage = {
  id: string;
  role: "user" | "assistant";
  content: string;
  createdAt: string;
  status?: "streaming" | "complete" | "awaiting-confirmation" | "error";
  toolCalls?: AgentToolCall[];
  error?: string;
};

type AgentConversation = {
  id: string;
  title: string;
  updatedAt: string;
  messages: AgentMessage[];
  restored?: boolean;
};

type PendingConfirmation = {
  sessionId: string;
  assistantMessageId: string;
  event: Extract<AgentStreamEvent, { type: "confirm_required" }>;
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

const TOOL_LABELS: Record<string, string> = {
  list_sync_tasks: "查询同步任务",
  get_sync_task: "查询任务详情",
  preview_sync_task: "预览任务配置",
  get_sync_overview: "查询同步概览",
  list_sync_instances: "查询运行实例",
  get_sync_instance_detail: "查询实例详情",
  query_task_log: "查询任务日志",
  download_task_log: "下载任务日志",
  create_sync_task: "创建同步任务",
  update_sync_task: "修改同步任务",
  publish_sync_task: "发布同步任务",
  unpublish_sync_task: "下线同步任务",
  delete_sync_task: "删除同步任务",
  stop_task_instance: "停止任务实例",
  force_success_task_instance: "强制成功任务实例",
  run_analysis_script: "运行分析脚本",
  read_workspace_file: "读取工作区文件",
  list_workspace_files: "列出工作区文件",
};

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

function formatSessionTime(value: number) {
  const date = new Date(value * 1000);
  return Number.isNaN(date.getTime()) ? new Date().toISOString() : date.toISOString();
}

function formatToolInput(value: string) {
  if (!value) return "";
  try {
    return JSON.stringify(JSON.parse(value), null, 2);
  } catch {
    return value;
  }
}

function toolStatusLabel(status: ToolCallStatus) {
  switch (status) {
    case "running":
      return "执行中";
    case "completed":
      return "已完成";
    case "awaiting-confirmation":
      return "等待确认";
    case "approved":
      return "已确认";
    case "rejected":
      return "已拒绝";
  }
}

function agentErrorMessage(error: unknown) {
  if (error instanceof DataAgentApiError) {
    if (error.status === 401) return "Yak-OPS 登录状态已失效，请重新登录后再试。";
    if (error.status === 403) {
      return "当前用户没有访问 Agent 配置项目的权限，请检查项目授权和 YAK_OPS_PROJECT_CODE。";
    }
    if (error.status === 404) return "Agent 会话不存在或已失效，请新建对话。";
    return error.message || "Data Agent 请求失败，请检查 Agent 服务。";
  }

  if (error instanceof Error) return error.message;
  return "Data Agent 暂时无法响应，请稍后重试。";
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
              模型由 Agent 服务端配置决定
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
  const [conversations, setConversations] = useState<AgentConversation[]>([]);
  const [activeConversationId, setActiveConversationId] = useState<string | null>(null);
  const [selectedModel, setSelectedModel] = useState("DeepSeek");
  const [modelMenuOpen, setModelMenuOpen] = useState(false);
  const [sidebarOpen, setSidebarOpen] = useState(true);
  const [sidebarWidth, setSidebarWidth] = useState(256);
  const [sidebarResizing, setSidebarResizing] = useState(false);
  const [composerKey, setComposerKey] = useState(0);
  const [initialDraft, setInitialDraft] = useState(initialPrompt);
  const [isSending, setIsSending] = useState(false);
  const [connectionError, setConnectionError] = useState("");
  const [pendingConfirmation, setPendingConfirmation] = useState<PendingConfirmation | null>(null);
  const messageScrollRef = useRef<HTMLDivElement>(null);
  const streamControllerRef = useRef<AbortController | null>(null);
  const pendingConfirmationResolverRef = useRef<((approved: boolean) => void) | null>(null);

  const activeConversation =
    conversations.find((conversation) => conversation.id === activeConversationId) ?? null;
  const messages = activeConversation?.messages ?? [];
  const hasMessages = messages.length > 0;
  const lastMessage = messages[messages.length - 1];

  useEffect(() => {
    const controller = new AbortController();

    listAgentSessions(controller.signal)
      .then((sessions) => {
        if (controller.signal.aborted) return;
        setConversations((current) => {
          const existingIds = new Set(current.map((conversation) => conversation.id));
          const restored = sessions
            .filter((session) => session.sessionId && !existingIds.has(session.sessionId))
            .map((session) => ({
              id: session.sessionId,
              title: "历史会话 · " + session.sessionId.slice(0, 6),
              updatedAt: formatSessionTime(session.updatedAt),
              messages: [],
              restored: true,
            }));
          return [...current, ...restored].sort(
            (left, right) =>
              new Date(right.updatedAt).getTime() - new Date(left.updatedAt).getTime(),
          );
        });
      })
      .catch((error: unknown) => {
        if (controller.signal.aborted) return;
        if (error instanceof DataAgentApiError && error.status === 401) {
          handleAuthenticationFailure("Data Agent 无法验证当前 Yak-OPS 登录状态，请重新登录。");
          return;
        }
        setConnectionError(agentErrorMessage(error));
      });

    return () => controller.abort();
  }, []);

  useEffect(
    () => () => {
      streamControllerRef.current?.abort();
      pendingConfirmationResolverRef.current?.(false);
      pendingConfirmationResolverRef.current = null;
    },
    [],
  );

  useEffect(() => {
    const container = messageScrollRef.current;
    if (!container || !hasMessages) return;
    container.scrollTop = container.scrollHeight;
  }, [
    activeConversationId,
    hasMessages,
    messages.length,
    lastMessage?.content,
    lastMessage?.status,
    lastMessage?.toolCalls?.length,
    pendingConfirmation?.event.replyId,
  ]);

  function patchAssistantMessage(
    sessionId: string,
    messageId: string,
    update: (message: AgentMessage) => AgentMessage,
  ) {
    setConversations((current) =>
      current.map((conversation) =>
        conversation.id === sessionId
          ? {
              ...conversation,
              updatedAt: new Date().toISOString(),
              messages: conversation.messages.map((message) =>
                message.id === messageId ? update(message) : message,
              ),
            }
          : conversation,
      ),
    );
  }

  function handleNewConversation() {
    if (isSending) return;
    setActiveConversationId(null);
    setModelMenuOpen(false);
    setConnectionError("");
    setInitialDraft("");
    setComposerKey((current) => current + 1);
  }

  function handleSelectConversation(id: string) {
    if (isSending) return;
    setActiveConversationId(id);
    setModelMenuOpen(false);
    setConnectionError("");
  }

  function resolveConfirmation(approved: boolean) {
    if (!pendingConfirmation) return;

    const pending = pendingConfirmation;
    setPendingConfirmation(null);
    patchAssistantMessage(pending.sessionId, pending.assistantMessageId, (message) => ({
      ...message,
      status: "streaming",
      toolCalls: (message.toolCalls ?? []).map((toolCall) => ({
        ...toolCall,
        status: approved ? "approved" : "rejected",
      })),
    }));

    const resolve = pendingConfirmationResolverRef.current;
    pendingConfirmationResolverRef.current = null;
    resolve?.(approved);
  }

  async function submitPrompt(content: string) {
    if (isSending) return;

    setIsSending(true);
    setConnectionError("");
    setModelMenuOpen(false);

    const controller = new AbortController();
    streamControllerRef.current = controller;

    const now = new Date().toISOString();
    const userMessage: AgentMessage = {
      id: globalThis.crypto.randomUUID(),
      role: "user",
      content,
      createdAt: now,
    };
    const assistantMessage: AgentMessage = {
      id: globalThis.crypto.randomUUID(),
      role: "assistant",
      content: "",
      createdAt: now,
      status: "streaming",
      toolCalls: [],
    };

    let sessionId = activeConversationId;

    try {
      if (!sessionId) {
        const created = await createAgentSession(controller.signal);
        sessionId = created.sessionId;
        setActiveConversationId(sessionId);
      }

      const resolvedSessionId = sessionId;
      setConversations((current) => {
        const existing = current.find((conversation) => conversation.id === resolvedSessionId);
        if (!existing) {
          return [
            {
              id: resolvedSessionId,
              title: conversationTitle(content),
              updatedAt: now,
              messages: [userMessage, assistantMessage],
            },
            ...current,
          ];
        }

        return current.map((conversation) =>
          conversation.id === resolvedSessionId
            ? {
                ...conversation,
                title:
                  conversation.messages.length === 0
                    ? conversationTitle(content)
                    : conversation.title,
                updatedAt: now,
                restored: false,
                messages: [...conversation.messages, userMessage, assistantMessage],
              }
            : conversation,
        );
      });

      await sendAgentMessage(resolvedSessionId, content, {
        signal: controller.signal,
        onEvent: (event) => {
          switch (event.type) {
            case "text_delta":
              patchAssistantMessage(resolvedSessionId, assistantMessage.id, (message) => ({
                ...message,
                content: message.content + event.delta,
                status: "streaming",
              }));
              break;
            case "tool_start":
              patchAssistantMessage(resolvedSessionId, assistantMessage.id, (message) => {
                const calls = message.toolCalls ?? [];
                const exists = calls.some((call) => call.id === event.toolCallId);
                return {
                  ...message,
                  toolCalls: exists
                    ? calls.map((call) =>
                        call.id === event.toolCallId ? { ...call, status: "running" } : call,
                      )
                    : [
                        ...calls,
                        {
                          id: event.toolCallId,
                          name: event.tool,
                          input: "",
                          status: "running",
                        },
                      ],
                };
              });
              break;
            case "tool_result":
              patchAssistantMessage(resolvedSessionId, assistantMessage.id, (message) => ({
                ...message,
                toolCalls: (message.toolCalls ?? []).map((call) =>
                  call.id === event.toolCallId ? { ...call, status: "completed" } : call,
                ),
              }));
              break;
            case "done":
              patchAssistantMessage(resolvedSessionId, assistantMessage.id, (message) => ({
                ...message,
                status: "complete",
              }));
              break;
            case "error":
              patchAssistantMessage(resolvedSessionId, assistantMessage.id, (message) => ({
                ...message,
                status: "error",
                error: event.message,
              }));
              break;
          }
        },
        onConfirmRequired: (event) =>
          new Promise<boolean>((resolve) => {
            pendingConfirmationResolverRef.current = resolve;
            setPendingConfirmation({
              sessionId: resolvedSessionId,
              assistantMessageId: assistantMessage.id,
              event,
            });
            patchAssistantMessage(resolvedSessionId, assistantMessage.id, (message) => ({
              ...message,
              status: "awaiting-confirmation",
              toolCalls: event.toolCalls.map((toolCall) => ({
                ...toolCall,
                status: "awaiting-confirmation",
              })),
            }));
          }),
      });
    } catch (error) {
      if (!controller.signal.aborted) {
        if (error instanceof DataAgentApiError && error.status === 401) {
          handleAuthenticationFailure("Data Agent 无法验证当前 Yak-OPS 登录状态，请重新登录。");
        }

        const message = agentErrorMessage(error);
        if (sessionId) {
          patchAssistantMessage(sessionId, assistantMessage.id, (current) => ({
            ...current,
            status: "error",
            error: message,
          }));
        } else {
          setConnectionError(message);
          setInitialDraft(content);
          setComposerKey((current) => current + 1);
        }
      }
    } finally {
      if (streamControllerRef.current === controller) streamControllerRef.current = null;
      setIsSending(false);
    }
  }

  function handlePromptSubmit(content: string) {
    if (isSending) return false;
    setInitialDraft("");
    void submitPrompt(content);
    return true;
  }

  function handleQuickAction(prompt: string) {
    if (isSending) return;
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
          "h-full shrink-0 overflow-hidden bg-[#f0f0f0] " +
          (sidebarResizing
            ? "transition-none "
            : "transition-[width] duration-[320ms] ease-[cubic-bezier(0.22,1,0.36,1)] motion-reduce:transition-none ") +
          "w-[var(--data-agent-sidebar-width)] max-md:!w-0"
        }
        style={{
          "--data-agent-sidebar-width": sidebarOpen ? sidebarWidth + "px" : "0px",
        } as React.CSSProperties}
      >
        <div
          ref={(element) => {
            if (element) element.inert = !sidebarOpen;
          }}
          className={
            "h-full w-full transform-gpu transition-[transform,opacity] duration-[320ms] ease-[cubic-bezier(0.22,1,0.36,1)] motion-reduce:transition-none " +
            (sidebarOpen
              ? "translate-x-0 opacity-100 pointer-events-auto"
              : "-translate-x-3 opacity-0 pointer-events-none")
          }
        >
          <DataAgentSidebar
            conversations={conversations}
            activeConversationId={activeConversationId}
            busy={isSending}
            onNewConversation={handleNewConversation}
            onSelectConversation={handleSelectConversation}
          />
        </div>
      </div>
      {sidebarOpen ? (
        <DataAgentSidebarResizer
          width={sidebarWidth}
          onResize={setSidebarWidth}
          onDraggingChange={setSidebarResizing}
        />
      ) : null}

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
                  {connectionError ? (
                    <div
                      role="alert"
                      className="mb-3 flex items-start gap-2 rounded-xl border border-[#f0d6d6] bg-[#fff7f7] px-3 py-2.5 text-xs leading-5 text-[#a14d4d]"
                    >
                      <AlertCircle size={15} className="mt-0.5 shrink-0" />
                      <span>{connectionError}</span>
                    </div>
                  ) : null}

                  {activeConversation?.restored ? (
                    <div className="mb-3 rounded-xl border border-[#e7e9e8] bg-[#f8faf9] px-3 py-2 text-xs leading-5 text-[#78817f]">
                      已恢复服务端会话上下文。当前 Agent 接口暂不提供历史消息回放，你可以继续提问。
                    </div>
                  ) : null}

                  <DataAgentPromptComposer
                    key={composerKey}
                    initialValue={initialDraft}
                    onSubmit={handlePromptSubmit}
                    isSubmitting={isSending}
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
                          disabled={isSending}
                          className="flex h-10 min-w-0 cursor-pointer items-center gap-2 rounded-[12px] border border-[#e2e6e5] bg-white px-2.5 text-left text-[13px] font-medium text-[#26302f] transition-[border-color,background-color] duration-150 hover:border-[#cbd4d2] hover:bg-[#fafbfa] focus-visible:outline-none focus-visible:ring-2 focus-visible:ring-[#fe2c55]/25 disabled:cursor-not-allowed disabled:opacity-50"
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
                    {messages.map((message) => {
                      const isUser = message.role === "user";
                      return (
                        <article
                          key={message.id}
                          className={"flex w-full " + (isUser ? "justify-end" : "justify-start")}
                        >
                          <div className={isUser ? "max-w-[82%]" : "w-full max-w-[92%]"}>
                            {isUser ? (
                              <div className="rounded-[22px] rounded-br-md border border-[#e5e5e2] bg-[#f5f5f3] px-4 py-3 text-[15px] leading-7 text-[#20201e]">
                                <p className="m-0 whitespace-pre-wrap break-words">
                                  {message.content}
                                </p>
                              </div>
                            ) : (
                              <div className="text-[14px] leading-7 text-[#27302f]">
                                {message.content ? (
                                  <p className="m-0 whitespace-pre-wrap break-words">
                                    {message.content}
                                  </p>
                                ) : message.status === "streaming" &&
                                  (message.toolCalls?.length ?? 0) === 0 ? (
                                  <div className="flex items-center gap-2 text-xs text-[#899391]">
                                    <LoaderCircle size={14} className="animate-spin" />
                                    正在思考...
                                  </div>
                                ) : null}

                                {(message.toolCalls?.length ?? 0) > 0 ? (
                                  <div className="mt-3 flex flex-col gap-2">
                                    {message.toolCalls?.map((toolCall) => {
                                      const StatusIcon =
                                        toolCall.status === "running"
                                          ? LoaderCircle
                                          : toolCall.status === "rejected"
                                            ? XCircle
                                            : toolCall.status === "awaiting-confirmation"
                                              ? ShieldCheck
                                              : CheckCircle2;
                                      return (
                                        <div
                                          key={toolCall.id}
                                          className="rounded-xl border border-[#e7eae9] bg-[#fbfcfb] px-3 py-2.5"
                                        >
                                          <div className="flex min-w-0 items-center gap-2">
                                            <Wrench
                                              size={14}
                                              className="shrink-0 text-[#7a8985]"
                                            />
                                            <span className="min-w-0 flex-1 truncate text-xs font-medium text-[#3b4744]">
                                              {TOOL_LABELS[toolCall.name] ?? toolCall.name}
                                            </span>
                                            <span className="flex shrink-0 items-center gap-1 text-[11px] text-[#8b9693]">
                                              <StatusIcon
                                                size={12}
                                                className={
                                                  toolCall.status === "running"
                                                    ? "animate-spin"
                                                    : toolCall.status === "rejected"
                                                      ? "text-[#b96b6b]"
                                                      : "text-[#6c9b7e]"
                                                }
                                              />
                                              {toolStatusLabel(toolCall.status)}
                                            </span>
                                          </div>
                                          {toolCall.input ? (
                                            <pre className="mt-2 max-h-36 overflow-auto whitespace-pre-wrap break-all rounded-lg bg-[#f3f5f4] p-2 text-[11px] leading-5 text-[#68736f]">
                                              {formatToolInput(toolCall.input)}
                                            </pre>
                                          ) : null}
                                        </div>
                                      );
                                    })}
                                  </div>
                                ) : null}

                                {pendingConfirmation?.assistantMessageId === message.id ? (
                                  <div className="mt-3 rounded-[14px] border border-[#ead9b7] bg-[#fffaf2] p-3.5">
                                    <div className="flex items-center gap-2 text-[13px] font-semibold text-[#745b32]">
                                      <ShieldCheck size={16} />
                                      需要你确认后才能执行
                                    </div>
                                    <p className="mb-3 mt-1 text-xs leading-5 text-[#927b56]">
                                      以下操作可能修改 Yak-OPS 中的数据。请检查操作内容后再决定是否继续。
                                    </p>
                                    <div className="flex flex-col gap-2">
                                      {pendingConfirmation.event.toolCalls.map((toolCall) => (
                                        <div
                                          key={toolCall.id}
                                          className="rounded-lg border border-[#eee2cc] bg-white/80 p-2.5"
                                        >
                                          <div className="text-xs font-medium text-[#51442f]">
                                            {TOOL_LABELS[toolCall.name] ?? toolCall.name}
                                          </div>
                                          {toolCall.input ? (
                                            <pre className="mt-2 max-h-40 overflow-auto whitespace-pre-wrap break-all rounded-md bg-[#faf7f1] p-2 text-[11px] leading-5 text-[#756b5a]">
                                              {formatToolInput(toolCall.input)}
                                            </pre>
                                          ) : null}
                                        </div>
                                      ))}
                                    </div>
                                    <div className="mt-3 flex justify-end gap-2">
                                      <button
                                        type="button"
                                        className="h-8 cursor-pointer rounded-lg border border-[#e4d9c8] bg-white px-3 text-xs font-medium text-[#756b5a] transition-colors hover:bg-[#f8f4ed]"
                                        onClick={() => resolveConfirmation(false)}
                                      >
                                        拒绝
                                      </button>
                                      <button
                                        type="button"
                                        className="h-8 cursor-pointer rounded-lg bg-[#20201e] px-3 text-xs font-medium text-white transition-colors hover:bg-[#454542]"
                                        onClick={() => resolveConfirmation(true)}
                                      >
                                        确认执行
                                      </button>
                                    </div>
                                  </div>
                                ) : null}

                                {message.error ? (
                                  <div
                                    role="alert"
                                    className="mt-3 flex items-start gap-2 rounded-xl border border-[#f0d6d6] bg-[#fff7f7] px-3 py-2.5 text-xs leading-5 text-[#a14d4d]"
                                  >
                                    <AlertCircle size={14} className="mt-0.5 shrink-0" />
                                    <span>{message.error}</span>
                                  </div>
                                ) : null}
                              </div>
                            )}
                            <time
                              dateTime={message.createdAt}
                              className={
                                "mt-1 block text-[11px] text-[#aaaaaa] " +
                                (isUser ? "text-right" : "text-left")
                              }
                            >
                              {formatConversationTime(message.createdAt)}
                            </time>
                          </div>
                        </article>
                      );
                    })}
                  </div>
                </div>

                <div className="shrink-0 px-6 pb-5 pt-3 max-sm:px-4">
                  <div className="mx-auto w-full max-w-3xl">
                    {connectionError ? (
                      <div
                        role="alert"
                        className="mb-2 flex items-start gap-2 rounded-xl border border-[#f0d6d6] bg-[#fff7f7] px-3 py-2.5 text-xs leading-5 text-[#a14d4d]"
                      >
                        <AlertCircle size={14} className="mt-0.5 shrink-0" />
                        <span>{connectionError}</span>
                      </div>
                    ) : null}
                    <DataAgentPromptComposer
                      key={composerKey}
                      initialValue={initialDraft}
                      onSubmit={handlePromptSubmit}
                      isSubmitting={isSending}
                      trailingActions={modelSelector}
                      placeholder="继续向 Data Agent 提问..."
                    />
                    <p className="mb-0 mt-2 text-center text-[11px] text-[#aaaaaa]">
                      Agent 正在实时处理消息；涉及写操作时，需要你确认后才会执行
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
