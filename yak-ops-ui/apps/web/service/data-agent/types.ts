export interface AgentRequestIdentity {
  userId: string;
  workspaceId: string;
}

export interface AgentSessionUsage {
  inputTokens: number;
  outputTokens: number;
  cacheInputTokens: number;
  cacheCreationInputTokens: number;
}

export interface AgentSessionSummary {
  sessionId: string;
  title: string;
  preview: string;
  updatedAt: number;
  usage?: AgentSessionUsage;
}

export type AgentSessionState =
  | "idle"
  | "queued"
  | "running"
  | "awaiting_confirmation"
  | "interrupting"
  | "stale";

export interface AgentSessionStatus {
  sessionId: string;
  state: AgentSessionState;
  busy: boolean;
  queued: number;
  stale: boolean;
  turnId?: string | null;
  replyId?: string | null;
  startedAt?: string | null;
  activeTurnMessage?: string | null;
  pendingConfirmation?: {
    replyId: string;
    toolCalls: AgentToolCallRequest[];
  } | null;
  title: string;
  preview: string;
  updatedAt?: number | null;
  usage?: AgentSessionUsage;
}

export interface AgentSessionQueueItem {
  queueId: string;
  position: number;
  createdAt: string;
  messagePreview: string;
}

export interface AgentSessionQueue {
  items: AgentSessionQueueItem[];
  max: number;
}

export type AgentHistoryBlock =
  | { type: "text"; text: string }
  | { type: "thinking"; id: string; startedAt?: string; completedAt?: string }
  | { type: "tool_call"; id: string; name: string; input: string }
  | {
      type: "tool_result";
      id: string;
      name: string;
      state: string;
      output: string;
      truncated?: boolean;
    };

export interface AgentHistoryMessage {
  id: string;
  role: "user" | "assistant";
  createdAt: string;
  blocks: AgentHistoryBlock[];
}

export interface AgentSessionHistory {
  sessionId: string;
  truncated: boolean;
  messages: AgentHistoryMessage[];
  usage?: AgentSessionUsage;
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

export type AgentStreamMetadata = {
  seq?: number;
  turnId?: string;
  at?: string;
  eventId?: string;
};

type AgentStreamPayload =
  | (AgentEventIdentity & {
      type: "reply_start";
      name: string;
      role: string;
    })
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
      type: "tool_call_end";
      toolCallId: string;
    })
  | (AgentEventIdentity & {
      type: "tool_result_start";
      tool: string;
      toolCallId: string;
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
      type: "exceed_max_iters";
      name: string;
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
  | {
      type: "queue_waiting";
      queueId: string;
      position: number;
      messagePreview: string;
    }
  | {
      type: "queue_started";
      queueId: string;
    }
  | {
      type: "queue_cancelled";
      queueId: string;
      reason: string;
    }
  | { type: "done"; finishedReason?: string }
  | { type: "error"; message: string }
  | { type: "replay_reset"; firstSeq: number }
  | { type: "stream_idle"; state?: string };

export type AgentStreamEvent = AgentStreamPayload & AgentStreamMetadata;

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
