import { requestJson, sessionPath } from "./http.ts";
import type {
  AgentRequestIdentity,
  AgentSessionHistory,
  AgentSessionStatus,
  AgentSessionSummary,
  AgentToolCallRequest,
} from "./types.ts";

export async function createAgentSession(
  identity: AgentRequestIdentity,
  signal?: AbortSignal,
): Promise<{ sessionId: string }> {
  return requestJson<{ sessionId: string }>(
    "/agent/sessions",
    identity,
    { method: "POST" },
    signal,
  );
}

export async function listAgentSessions(
  identity: AgentRequestIdentity,
  signal?: AbortSignal,
): Promise<AgentSessionSummary[]> {
  return requestJson<AgentSessionSummary[]>(
    "/agent/sessions",
    identity,
    { method: "GET" },
    signal,
  );
}

export async function getAgentSessionStatus(
  sessionId: string,
  identity: AgentRequestIdentity,
  signal?: AbortSignal,
): Promise<AgentSessionStatus> {
  return requestJson<AgentSessionStatus>(
    sessionPath(sessionId),
    identity,
    { method: "GET" },
    signal,
  );
}

export async function getAgentSessionMessages(
  sessionId: string,
  identity: AgentRequestIdentity,
  signal?: AbortSignal,
): Promise<AgentSessionHistory> {
  return requestJson<AgentSessionHistory>(
    sessionPath(sessionId) + "/messages",
    identity,
    { method: "GET" },
    signal,
  );
}

export async function renameAgentSession(
  sessionId: string,
  title: string,
  identity: AgentRequestIdentity,
  signal?: AbortSignal,
): Promise<{ sessionId: string; title: string }> {
  return requestJson<{ sessionId: string; title: string }>(
    sessionPath(sessionId),
    identity,
    {
      method: "PATCH",
      body: JSON.stringify({ title }),
    },
    signal,
  );
}

export async function deleteAgentSession(
  sessionId: string,
  identity: AgentRequestIdentity,
  signal?: AbortSignal,
): Promise<{ deleted: boolean }> {
  return requestJson<{ deleted: boolean }>(
    sessionPath(sessionId),
    identity,
    { method: "DELETE" },
    signal,
  );
}

export async function interruptAgentSession(
  sessionId: string,
  turnId: string | null,
  identity: AgentRequestIdentity,
  signal?: AbortSignal,
): Promise<{ accepted: boolean; state: string; turnId?: string | null }> {
  return requestJson<{ accepted: boolean; state: string; turnId?: string | null }>(
    sessionPath(sessionId) + "/interrupt",
    identity,
    {
      method: "POST",
      body: JSON.stringify(turnId ? { turnId } : {}),
    },
    signal,
  );
}

export async function confirmAgentSession(
  sessionId: string,
  approved: boolean | boolean[],
  identity: AgentRequestIdentity,
  signal: AbortSignal,
): Promise<void> {
  await requestJson<{ accepted: boolean }>(
    sessionPath(sessionId) + "/confirm",
    identity,
    {
      method: "POST",
      body: JSON.stringify({ approved }),
    },
    signal,
  );
}

export type RestoredConfirmation = {
  replyId: string;
  toolCalls: AgentToolCallRequest[];
};
