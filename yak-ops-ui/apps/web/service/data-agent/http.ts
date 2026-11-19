import {
  DATA_AGENT_API_PREFIX,
  DataAgentApiError,
} from "./types.ts";

export const sessionPath = (sessionId: string) =>
  "/agent/sessions/" + encodeURIComponent(sessionId);

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
  init: RequestInit = {},
  signal?: AbortSignal,
): Promise<T> {
  const headers = new Headers(init.headers);
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
