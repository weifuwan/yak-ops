import { createAgentRequestHeaders, responseError, sessionPath } from "./http.ts";
import { getAgentSessionStatus } from "./sessions.ts";
import {
  DATA_AGENT_API_PREFIX,
  DataAgentApiError,
  DataAgentStreamError,
  type AgentRequestIdentity,
  type AgentStreamEvent,
  type AgentToolCallRequest,
} from "./types.ts";

type StreamOptions = {
  identity: AgentRequestIdentity;
  signal: AbortSignal;
  queue?: boolean;
  onEvent: (event: AgentStreamEvent) => void | Promise<void>;
  onConfirmRequired: (
    event: Extract<AgentStreamEvent, { type: "confirm_required" }>,
  ) => void;
  onReconnect?: () => void;
};

type StreamReadResult = {
  cursor: number;
  completed: boolean;
  idle: boolean;
  received: boolean;
};

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
  let eventId = "";
  const dataLines: string[] = [];

  for (const line of frame.split(/\r?\n/)) {
    if (!line || line.startsWith(":")) continue;

    const separator = line.indexOf(":");
    const field = separator < 0 ? line : line.slice(0, separator);
    const rawValue = separator < 0 ? "" : line.slice(separator + 1);
    const value = rawValue.startsWith(" ") ? rawValue.slice(1) : rawValue;

    if (field === "event") eventName = value;
    else if (field === "id") eventId = value;
    else if (field === "data") dataLines.push(value);
  }

  if (dataLines.length === 0) return null;

  let payload: Record<string, unknown>;
  try {
    payload = JSON.parse(dataLines.join("\n")) as Record<string, unknown>;
  } catch {
    throw new DataAgentStreamError("Agent 返回了无法解析的 SSE 事件");
  }

  const type = typeof payload.type === "string" ? payload.type : eventName;
  const identity = optionalIdentity(payload);
  const seq = numericValue(payload.seq) || Number(eventId) || undefined;
  const metadata = {
    ...(seq ? { seq } : {}),
    ...(eventId ? { eventId } : {}),
    ...(typeof payload.turnId === "string" ? { turnId: payload.turnId } : {}),
    ...(typeof payload.at === "string" ? { at: payload.at } : {}),
  };

  switch (type) {
    case "reply_start":
      return {
        type,
        name: typeof payload.name === "string" ? payload.name : "",
        role: typeof payload.role === "string" ? payload.role : "assistant",
        ...identity,
        ...metadata,
      };
    case "text_delta":
      return {
        type,
        delta: typeof payload.delta === "string" ? payload.delta : "",
        ...identity,
        ...metadata,
      };
    case "thinking_start":
    case "thinking_end":
      return {
        type,
        blockId: typeof payload.blockId === "string" ? payload.blockId : "",
        ...identity,
        ...metadata,
      };
    case "thinking_delta":
      return {
        type,
        delta: typeof payload.delta === "string" ? payload.delta : "",
        blockId: typeof payload.blockId === "string" ? payload.blockId : "",
        ...identity,
        ...metadata,
      };
    case "tool_start":
      return {
        type,
        tool: typeof payload.tool === "string" ? payload.tool : "",
        toolCallId: typeof payload.toolCallId === "string" ? payload.toolCallId : "",
        ...identity,
        ...metadata,
      };
    case "tool_call_delta":
    case "tool_result_delta":
      return {
        type,
        toolCallId: typeof payload.toolCallId === "string" ? payload.toolCallId : "",
        delta: typeof payload.delta === "string" ? payload.delta : "",
        ...identity,
        ...metadata,
      };
    case "tool_call_end":
      return {
        type,
        toolCallId: typeof payload.toolCallId === "string" ? payload.toolCallId : "",
        ...identity,
        ...metadata,
      };
    case "tool_result_start":
      return {
        type,
        tool: typeof payload.tool === "string" ? payload.tool : "",
        toolCallId: typeof payload.toolCallId === "string" ? payload.toolCallId : "",
        ...identity,
        ...metadata,
      };
    case "tool_result":
      return {
        type,
        toolCallId: typeof payload.toolCallId === "string" ? payload.toolCallId : "",
        ...(typeof payload.state === "string" ? { state: payload.state } : {}),
        ...identity,
        ...metadata,
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
        ...metadata,
      };
    case "usage":
      return {
        type,
        inputTokens: numericValue(payload.inputTokens),
        outputTokens: numericValue(payload.outputTokens),
        cacheInputTokens: numericValue(payload.cacheInputTokens),
        cacheCreationInputTokens: numericValue(payload.cacheCreationInputTokens),
        ...identity,
        ...metadata,
      };
    case "exceed_max_iters":
      return {
        type,
        name: typeof payload.name === "string" ? payload.name : "",
        ...identity,
        ...metadata,
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
        ...metadata,
      };
    case "confirm_required":
      return {
        type,
        replyId: typeof payload.replyId === "string" ? payload.replyId : "",
        toolCalls: parseToolCalls(payload.toolCalls),
        ...metadata,
      };
    case "queue_waiting":
      return {
        type,
        queueId: typeof payload.queueId === "string" ? payload.queueId : "",
        position: numericValue(payload.position),
        messagePreview:
          typeof payload.messagePreview === "string" ? payload.messagePreview : "",
        ...metadata,
      };
    case "queue_started":
      return {
        type,
        queueId: typeof payload.queueId === "string" ? payload.queueId : "",
        ...metadata,
      };
    case "queue_cancelled":
      return {
        type,
        queueId: typeof payload.queueId === "string" ? payload.queueId : "",
        reason: typeof payload.reason === "string" ? payload.reason : "cancelled",
        ...metadata,
      };
    case "done":
      return {
        type,
        ...(typeof payload.finishedReason === "string"
          ? { finishedReason: payload.finishedReason }
          : {}),
        ...metadata,
      };
    case "error":
      return {
        type,
        message: typeof payload.message === "string" ? payload.message : "Agent 执行失败",
        ...metadata,
      };
    case "replay_reset":
      return {
        type,
        firstSeq: numericValue(payload.firstSeq),
        ...metadata,
      };
    case "stream_idle":
      return {
        type,
        ...(typeof payload.state === "string" ? { state: payload.state } : {}),
        ...metadata,
      };
    default:
      return null;
  }
}

async function consumeAgentStream(
  response: Response,
  options: StreamOptions,
  initialCursor: number,
): Promise<StreamReadResult> {
  if (!response.body) {
    throw new DataAgentStreamError("浏览器未能读取 Agent 的流式响应");
  }

  const reader = response.body.getReader();
  const decoder = new TextDecoder();
  let buffer = "";
  let cursor = initialCursor;
  let completed = false;
  let idle = false;
  let received = false;

  const dispatch = async (frame: string) => {
    const event = parseAgentSseFrame(frame);
    if (!event) return;

    received = true;
    if (event.seq && event.seq > cursor) cursor = event.seq;
    else if (event.eventId && /^\d+$/.test(event.eventId)) {
      cursor = Math.max(cursor, Number(event.eventId));
    }

    if (event.type === "confirm_required") {
      options.onConfirmRequired(event);
    }
    await options.onEvent(event);

    if (
      event.type === "done" ||
      event.type === "error" ||
      event.type === "queue_cancelled"
    ) {
      completed = true;
    }
    if (event.type === "stream_idle") idle = true;
  };

  try {
    while (!completed && !idle) {
      let result: ReadableStreamReadResult<Uint8Array>;
      try {
        result = await reader.read();
      } catch (error) {
        if (options.signal.aborted) throw error;
        return { cursor, completed, idle, received };
      }

      buffer += decoder.decode(result.value, { stream: !result.done });
      let boundary = /\r?\n\r?\n/.exec(buffer);
      while (boundary) {
        const frame = buffer.slice(0, boundary.index);
        buffer = buffer.slice(boundary.index + boundary[0].length);
        await dispatch(frame);
        boundary = /\r?\n\r?\n/.exec(buffer);
        if (completed || idle) break;
      }

      if (result.done) break;
    }

    if (!completed && !idle && buffer.trim()) {
      await dispatch(buffer);
    }
  } finally {
    reader.releaseLock();
  }

  return { cursor, completed, idle, received };
}

function waitForRetry(milliseconds: number, signal: AbortSignal) {
  return new Promise<void>((resolve, reject) => {
    if (signal.aborted) {
      reject(new DOMException("The operation was aborted", "AbortError"));
      return;
    }
    const timer = window.setTimeout(() => {
      signal.removeEventListener("abort", abort);
      resolve();
    }, milliseconds);
    const abort = () => {
      window.clearTimeout(timer);
      reject(new DOMException("The operation was aborted", "AbortError"));
    };
    signal.addEventListener("abort", abort, { once: true });
  });
}

export async function resumeAgentStream(
  sessionId: string,
  after: number,
  options: StreamOptions,
): Promise<void> {
  let cursor = after;
  let failures = 0;

  while (!options.signal.aborted && failures < 8) {
    try {
      const response = await fetch(
        DATA_AGENT_API_PREFIX +
          sessionPath(sessionId) +
          "/stream?after=" +
          String(cursor),
        {
          method: "GET",
          headers: createAgentRequestHeaders(options.identity, {
            Accept: "text/event-stream",
          }),
          credentials: "include",
          signal: options.signal,
        },
      );
      if (!response.ok) throw await responseError(response);

      const result = await consumeAgentStream(response, options, cursor);
      cursor = result.cursor;
      if (result.completed) return;

      const status = await getAgentSessionStatus(
        sessionId,
        options.identity,
        options.signal,
      );
      if (!status.busy && status.state !== "queued") return;

      failures = result.idle || !result.received ? failures + 1 : 0;
    } catch (error) {
      if (options.signal.aborted) throw error;
      if (
        error instanceof DataAgentApiError &&
        (error.status === 401 || error.status === 403 || error.status === 404)
      ) {
        throw error;
      }
      failures += 1;
    }

    if (failures < 8) {
      await waitForRetry(Math.min(1000 * 2 ** (failures - 1), 8000), options.signal);
    }
  }

  if (!options.signal.aborted) {
    throw new DataAgentStreamError("Agent 连接暂时中断，自动恢复次数已达上限；重新打开会话可继续恢复");
  }
}

export async function sendAgentMessage(
  sessionId: string,
  message: string,
  options: StreamOptions,
): Promise<void> {
  const queueSuffix = options.queue ? "?queue=true" : "";
  const response = await fetch(
    DATA_AGENT_API_PREFIX + sessionPath(sessionId) + "/messages" + queueSuffix,
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
  const result = await consumeAgentStream(response, options, 0);
  if (result.completed) return;

  options.onReconnect?.();
  await resumeAgentStream(sessionId, result.cursor, options);
}
