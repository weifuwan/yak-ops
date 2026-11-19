import { requestJson, sessionPath } from "./http";
import type { AgentSessionSummary } from "./types";

export async function createAgentSession(
  signal?: AbortSignal,
): Promise<{ sessionId: string }> {
  return requestJson<{ sessionId: string }>(
    "/agent/sessions",
    { method: "POST" },
    signal,
  );
}

export async function listAgentSessions(
  signal?: AbortSignal,
): Promise<AgentSessionSummary[]> {
  return requestJson<AgentSessionSummary[]>(
    "/agent/sessions",
    { method: "GET" },
    signal,
  );
}

export async function deleteAgentSession(
  sessionId: string,
  signal?: AbortSignal,
): Promise<{ deleted: boolean }> {
  return requestJson<{ deleted: boolean }>(
    sessionPath(sessionId),
    { method: "DELETE" },
    signal,
  );
}

export async function confirmAgentSession(
  sessionId: string,
  approved: boolean,
  signal: AbortSignal,
): Promise<void> {
  await requestJson<{ accepted: boolean }>(
    sessionPath(sessionId) + "/confirm",
    {
      method: "POST",
      body: JSON.stringify({ approved }),
    },
    signal,
  );
}
