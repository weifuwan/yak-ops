import { createAgentRequestHeaders, responseError, sessionPath } from "./http.ts";
import { confirmAgentSession } from "./sessions.ts";
import {
  DATA_AGENT_API_PREFIX,
  DataAgentStreamError,
  type AgentRequestIdentity,
  type AgentStreamEvent,
  type AgentToolCallRequest,
} from "./types.ts";

function optionalIdentity(payload: Record<string, unknown>) {
  return {
    ...(typeof payload.replyId === "string" ? { replyId: payload.replyId } : {}),
    ...(typeof payload.blockId === "string" ? { blockId: payload.blockId } : {}),
  };
}

function parseToolCalls(value: unknown): AgentToolCallRequest[] {
  if (!Array.isArray(value)) return [];
  return value.flatMap((item) => {
    if (!item || typeof item !== "object") return [];
    const toolCall = item as Record<string, unknown>;
    if (typeof toolCall.id !== "string" || typeof toolCall.name !== "string") return [];
    return [{
      id: toolCall.id,
      name: toolCall.name,
      input: typeof toolCall.input === "string" ? toolCall.input : "",
    }];
  });
}

function numericValue(value: unknown) {
  return typeof value === "number" && Number.isFinite(value) ? value : 0;
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
  const identity = optionalIdentity(payload);

  switch (type) {
    case "text_delta":
      return {
        type,
        delta: typeof payload.delta === "string" ? payload.delta : "",
        ...identity,
      };
    case "thinking_start":
    case "thinking_end":
      return {
        type,
        blockId: typeof payload.blockId === "string" ? payload.blockId : "",
        ...identity,
      };
    case "thinking_delta":
      return {
        type,
        delta: typeof payload.delta === "string" ? payload.delta : "",
        blockId: typeof payload.blockId === "string" ? payload.blockId : "",
        ...identity,
      };
    case "tool_start":
      return {
        type,
        tool: typeof payload.tool === "string" ? payload.tool : "",
        toolCallId: typeof payload.toolCallId === "string" ? payload.toolCallId : "",
        ...identity,
      };
    case "tool_call_delta":
    case "tool_result_delta":
      return {
        type,
        toolCallId: typeof payload.toolCallId === "string" ? payload.toolCallId : "",
        delta: typeof payload.delta === "string" ? payload.delta : "",
        ...identity,
      };
    case "tool_result":
      return {
        type,
        toolCallId: typeof payload.toolCallId === "string" ? payload.toolCallId : "",
        ...(typeof payload.state === "string" ? { state: payload.state } : {}),
        ...identity,
      };
    case "progress":
      return {
        type,
        phase: typeof payload.phase === "string" ? payload.phase : "general",
        title: typeof payload.title === "string" ? payload.title : "正在处理",
        status:
          payload.status === "completed" || payload.status === "error"
            ? payload.status
            : "running",
        ...identity,
      };
    case "usage":
      return {
        type,
        inputTokens: numericValue(payload.inputTokens),
        outputTokens: numericValue(payload.outputTokens),
        cacheInputTokens: numericValue(payload.cacheInputTokens),
        cacheCreationInputTokens: numericValue(payload.cacheCreationInputTokens),
        ...identity,
      };
    case "reply_end":
      return {
        type,
        finishedReason:
          typeof payload.finishedReason === "string" ? payload.finishedReason : "unknown",
        error:
          payload.error && typeof payload.error === "object" && !Array.isArray(payload.error)
            ? (payload.error as Record<string, unknown>)
            : null,
        ...identity,
      };
    case "confirm_required":
      return {
        type,
        replyId: typeof payload.replyId === "string" ? payload.replyId : "",
        toolCalls: parseToolCalls(payload.toolCalls),
      };
    case "done":
      return { type };
    case "error":
      return {
        type,
        message:
          typeof payload.message === "string"
            ? payload.message
            : "Agent 执行失败",
      };
    default:
      return null;
  }
}

export async function sendAgentMessage(
  sessionId: string,
  message: string,
  options: {
    identity: AgentRequestIdentity;
    signal: AbortSignal;
    onEvent: (event: AgentStreamEvent) => void;
    onConfirmRequired: (
      event: Extract<AgentStreamEvent, { type: "confirm_required" }>,
    ) => Promise<boolean>;
  },
): Promise<void> {
  const response = await fetch(
    DATA_AGENT_API_PREFIX + sessionPath(sessionId) + "/messages",
    {
      method: "POST",
      headers: createAgentRequestHeaders(options.identity, {
        Accept: "text/event-stream",
        "Content-Type": "application/json",
      }),
      credentials: "include",
      body: JSON.stringify({ message }),
      signal: options.signal,
    },
  );

  if (!response.ok) throw await responseError(response);
  if (!response.body) {
    throw new DataAgentStreamError("浏览器未能读取 Agent 的流式响应");
  }

  const reader = response.body.getReader();
  const decoder = new TextDecoder();
  let buffer = "";
  let completed = false;

  const dispatch = async (frame: string) => {
    const event = parseAgentSseFrame(frame);
    if (!event) return;

    if (event.type === "confirm_required") {
      const approved = await options.onConfirmRequired(event);
      await confirmAgentSession(sessionId, approved, options.identity, options.signal);
      return;
    }

    options.onEvent(event);
    if (event.type === "done") completed = true;
    if (event.type === "error") {
      throw new DataAgentStreamError(event.message);
    }
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
