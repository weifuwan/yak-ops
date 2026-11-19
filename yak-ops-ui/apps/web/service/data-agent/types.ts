export interface AgentSessionSummary {
  sessionId: string;
  updatedAt: number;
}

export interface AgentToolCallRequest {
  id: string;
  name: string;
  input: string;
}

export type AgentStreamEvent =
  | { type: "text_delta"; delta: string }
  | { type: "tool_start"; tool: string; toolCallId: string }
  | { type: "tool_result"; toolCallId: string }
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
