import { requestJson, sessionPath } from "./http.ts";
import type { AgentRequestIdentity, AgentSessionSummary } from "./types.ts";

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

export async function confirmAgentSession(
  sessionId: string,
  approved: boolean,
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
