import {
  DATA_AGENT_API_PREFIX,
  DataAgentApiError,
  type AgentRequestIdentity,
} from "./types.ts";

export const sessionPath = (sessionId: string) =>
  "/agent/sessions/" + encodeURIComponent(sessionId);

export function createAgentRequestHeaders(
  identity: AgentRequestIdentity,
  initialHeaders?: HeadersInit,
): Headers {
  const userId = identity.userId.trim();
  const workspaceId = identity.workspaceId.trim();
  if (!userId || !workspaceId) {
    throw new DataAgentApiError("Data Agent 请求缺少当前用户或工作空间，请先登录并选择工作空间。");
  }

  const headers = new Headers(initialHeaders);
  headers.set("X-Yak-User-Id", userId);
  headers.set("X-Yak-Workspace-Id", workspaceId);
  return headers;
}

export async function responseError(response: Response): Promise<DataAgentApiError> {
  const raw = await response.text();
  let message = raw || response.statusText || "Data Agent 请求失败";

  if (raw) {
    try {
      const payload = JSON.parse(raw) as {
        detail?: unknown;
        message?: unknown;
      };
      if (typeof payload.detail === "string") message = payload.detail;
      else if (typeof payload.message === "string") message = payload.message;
    } catch {
      // Keep the original response text for non-JSON proxy errors.
    }
  }

  return new DataAgentApiError(message, response.status);
}

export async function requestJson<T>(
  path: string,
  identity: AgentRequestIdentity,
  init: RequestInit = {},
  signal?: AbortSignal,
): Promise<T> {
  const headers = createAgentRequestHeaders(identity, init.headers);
  if (init.body !== undefined && !headers.has("Content-Type")) {
    headers.set("Content-Type", "application/json");
  }

  const response = await fetch(DATA_AGENT_API_PREFIX + path, {
    ...init,
    headers,
    credentials: "include",
    signal: signal ?? init.signal,
  });

  if (!response.ok) throw await responseError(response);
  if (response.status === 204) return undefined as T;

  const raw = await response.text();
  return (raw ? JSON.parse(raw) : undefined) as T;
}
