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
  constructor(
    message: string,
    readonly status?: number,
  ) {
    super(message);
    this.name = "DataAgentApiError";
  }
}

export class DataAgentStreamError extends Error {
  constructor(message: string) {
    super(message);
    this.name = "DataAgentStreamError";
  }
}

export const DATA_AGENT_API_PREFIX = "/yak-ops/agent-api";

const sessionPath = (sessionId: string) =>
  "/agent/sessions/" + encodeURIComponent(sessionId);

async function responseError(response: Response): Promise<DataAgentApiError> {
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

async function requestJson<T>(
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

export async function createAgentSession(signal?: AbortSignal): Promise<{ sessionId: string }> {
  return requestJson<{ sessionId: string }>(
    "/agent/sessions",
    { method: "POST" },
    signal,
  );
}

export async function listAgentSessions(signal?: AbortSignal): Promise<AgentSessionSummary[]> {
  return requestJson<AgentSessionSummary[]>("/agent/sessions", { method: "GET" }, signal);
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

async function confirmAgentSession(
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

export function parseAgentSseFrame(frame: string): AgentStreamEvent | null {
  let eventName = "";
  const dataLines: string[] = [];

  for (const line of frame.split(/\r?\n/)) {
    if (!line || line.startsWith(":")) continue;

    const separator = line.indexOf(":");
    const field = separator < 0 ? line : line.slice(0, separator);
    const rawValue = separator < 0 ? "" : line.slice(separator + 1);
    const value = rawValue.startsWith(" ") ? rawValue.slice(1) : rawValue;

    if (field === "event") eventName = value;
    else if (field === "data") dataLines.push(value);
  }

  if (dataLines.length === 0) return null;

  const payload = JSON.parse(dataLines.join("\n")) as Record<string, unknown>;
  const type = typeof payload.type === "string" ? payload.type : eventName;

  switch (type) {
    case "text_delta":
      return {
        type,
        delta: typeof payload.delta === "string" ? payload.delta : "",
      };
    case "tool_start":
      return {
        type,
        tool: typeof payload.tool === "string" ? payload.tool : "",
        toolCallId: typeof payload.toolCallId === "string" ? payload.toolCallId : "",
      };
    case "tool_result":
      return {
        type,
        toolCallId: typeof payload.toolCallId === "string" ? payload.toolCallId : "",
      };
    case "confirm_required":
      return {
        type,
        replyId: typeof payload.replyId === "string" ? payload.replyId : "",
        toolCalls: Array.isArray(payload.toolCalls)
          ? (payload.toolCalls as AgentToolCallRequest[])
          : [],
      };
    case "done":
      return { type };
    case "error":
      return {
        type,
        message: typeof payload.message === "string" ? payload.message : "Agent 执行失败",
      };
    default:
      return null;
  }
}

export async function sendAgentMessage(
  sessionId: string,
  message: string,
  options: {
    signal: AbortSignal;
    onEvent: (event: AgentStreamEvent) => void;
    onConfirmRequired: (event: Extract<AgentStreamEvent, { type: "confirm_required" }>) => Promise<boolean>;
  },
): Promise<void> {
  const response = await fetch(
    DATA_AGENT_API_PREFIX + sessionPath(sessionId) + "/messages",
    {
      method: "POST",
      headers: {
        Accept: "text/event-stream",
        "Content-Type": "application/json",
      },
      credentials: "include",
      body: JSON.stringify({ message }),
      signal: options.signal,
    },
  );

  if (!response.ok) throw await responseError(response);
  if (!response.body) throw new DataAgentStreamError("浏览器未能读取 Agent 的流式响应");

  const reader = response.body.getReader();
  const decoder = new TextDecoder();
  let buffer = "";
  let completed = false;

  const dispatch = async (frame: string) => {
    const event = parseAgentSseFrame(frame);
    if (!event) return;

    if (event.type === "confirm_required") {
      const approved = await options.onConfirmRequired(event);
      await confirmAgentSession(sessionId, approved, options.signal);
      return;
    }

    options.onEvent(event);
    if (event.type === "done") completed = true;
    if (event.type === "error") throw new DataAgentStreamError(event.message);
  };

  try {
    while (true) {
      const result = await reader.read();
      buffer += decoder.decode(result.value, { stream: !result.done });

      let boundary = /\r?\n\r?\n/.exec(buffer);
      while (boundary) {
        const frame = buffer.slice(0, boundary.index);
        buffer = buffer.slice(boundary.index + boundary[0].length);
        await dispatch(frame);
        boundary = /\r?\n\r?\n/.exec(buffer);
      }

      if (result.done) break;
    }

    if (buffer.trim()) await dispatch(buffer);
  } finally {
    reader.releaseLock();
  }

  if (!completed && !options.signal.aborted) {
    throw new DataAgentStreamError("Agent 响应连接意外结束，请重试");
  }
}
