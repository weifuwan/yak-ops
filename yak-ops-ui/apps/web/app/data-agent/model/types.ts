import type { AgentStreamEvent, AgentToolCallRequest } from "@/service/data-agent";

export type ToolCallStatus =
  | "running"
  | "completed"
  | "awaiting-confirmation"
  | "approved"
  | "rejected";

export type AgentToolCall = AgentToolCallRequest & {
  status: ToolCallStatus;
};

export type AgentMessage = {
  id: string;
  role: "user" | "assistant";
  content: string;
  createdAt: string;
  status?: "streaming" | "complete" | "awaiting-confirmation" | "error";
  toolCalls?: AgentToolCall[];
  error?: string;
};

export type AgentConversation = {
  id: string;
  title: string;
  updatedAt: string;
  messages: AgentMessage[];
  restored?: boolean;
};

export type PendingConfirmation = {
  sessionId: string;
  assistantMessageId: string;
  event: Extract<AgentStreamEvent, { type: "confirm_required" }>;
};
