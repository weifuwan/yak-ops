import type { AgentStreamEvent, AgentToolCallRequest } from "@/service/data-agent";

export type ToolCallStatus =
  | "running"
  | "completed"
  | "awaiting-confirmation"
  | "approved"
  | "rejected"
  | "error"
  | "denied"
  | "interrupted";

export type AgentActivityStatus = ToolCallStatus;
export type AgentActivityKind = "text" | "thinking" | "progress" | "tool";

export type AgentActivity = {
  id: string;
  kind: AgentActivityKind;
  title: string;
  status: AgentActivityStatus;
  replyId?: string;
  blockId?: string;
  phase?: string;
  toolCallId?: string;
  toolName?: string;
  content?: string;
  input?: string;
  output?: string;
  startedAt?: number;
  completedAt?: number;
};

export type AgentUsage = {
  replyId?: string;
  inputTokens: number;
  outputTokens: number;
  cacheInputTokens: number;
  cacheCreationInputTokens: number;
};

export type AgentReplyEnd = {
  replyId?: string;
  finishedReason: string;
  error?: Record<string, unknown> | null;
};

export type AgentToolCall = AgentToolCallRequest & {
  status: ToolCallStatus;
};

export type AgentMessage = {
  id: string;
  role: "user" | "assistant";
  content: string;
  createdAt: string;
  status?: "streaming" | "complete" | "awaiting-confirmation" | "interrupted" | "error";
  toolCalls?: AgentToolCall[];
  activities?: AgentActivity[];
  usage?: AgentUsage[];
  replyEnds?: AgentReplyEnd[];
  error?: string;
};

export type AgentConversation = {
  id: string;
  title: string;
  updatedAt: string;
  preview?: string;
  contextTokens?: number;
  messages: AgentMessage[];
  restored?: boolean;
};

export type PendingConfirmation = {
  sessionId: string;
  assistantMessageId: string;
  event: Extract<AgentStreamEvent, { type: "confirm_required" }>;
};
