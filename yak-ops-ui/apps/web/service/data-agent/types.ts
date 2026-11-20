export interface AgentRequestIdentity {
  userId: string;
  workspaceId: string;
}

export interface AgentSessionSummary {
  sessionId: string;
  updatedAt: number;
}

export interface AgentToolCallRequest {
  id: string;
  name: string;
  input: string;
}

type AgentEventIdentity = {
  replyId?: string;
};

type AgentBlockIdentity = AgentEventIdentity & {
  blockId: string;
};

export type AgentStreamEvent =
  | (AgentEventIdentity & {
      type: "text_delta";
      delta: string;
      blockId?: string;
    })
  | (AgentBlockIdentity & { type: "thinking_start" })
  | (AgentBlockIdentity & { type: "thinking_delta"; delta: string })
  | (AgentBlockIdentity & { type: "thinking_end" })
  | (AgentEventIdentity & {
      type: "tool_start";
      tool: string;
      toolCallId: string;
    })
  | (AgentEventIdentity & {
      type: "tool_call_delta";
      toolCallId: string;
      delta: string;
    })
  | (AgentEventIdentity & {
      type: "tool_result_delta";
      toolCallId: string;
      delta: string;
    })
  | (AgentEventIdentity & {
      type: "tool_result";
      toolCallId: string;
      state?: string;
    })
  | (AgentEventIdentity & {
      type: "progress";
      phase: string;
      title: string;
      status: "running" | "completed" | "error";
    })
  | (AgentEventIdentity & {
      type: "usage";
      inputTokens: number;
      outputTokens: number;
      cacheInputTokens: number;
      cacheCreationInputTokens: number;
    })
  | (AgentEventIdentity & {
      type: "reply_end";
      finishedReason: string;
      error?: Record<string, unknown> | null;
    })
  | {
      type: "confirm_required";
      replyId: string;
      toolCalls: AgentToolCallRequest[];
    }
  | { type: "done" }
  | { type: "error"; message: string };

export class DataAgentApiError extends Error {
  readonly status?: number;

  constructor(message: string, status?: number) {
    super(message);
    this.name = "DataAgentApiError";
    this.status = status;
  }
}

export class DataAgentStreamError extends Error {
  constructor(message: string) {
    super(message);
    this.name = "DataAgentStreamError";
  }
}

export const DATA_AGENT_API_PREFIX = "/yak-ops/agent-api";
