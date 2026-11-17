export type AgentTurnSubmission = {
  sessionId: string;
  turnId: string;
  status: string;
};

export type AgentTurnUsage = {
  promptTokens?: number;
  completionTokens?: number;
  latencyMs?: number;
};

export type AgentTurnResult = {
  turnId: string;
  sessionId: string;
  status: string;
  answer?: string;
  errorCode?: string;
  errorMessage?: string;
  usage?: AgentTurnUsage;
};

export type AgentSessionMessage = {
  id: string;
  turnId?: string;
  role: string;
  content: string;
  createTime?: string;
};

export type AgentStreamEvent = {
  id?: string;
  type: string;
  data: Record<string, unknown>;
};
