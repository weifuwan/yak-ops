import HttpUtils from "@/service/http/HttpUtils";
import { YAK_OPS_API_PREFIX } from "@/config/api";

import type {
  AgentSessionMessage,
  AgentStreamEvent,
  AgentTurnResult,
  AgentTurnSubmission,
} from "./types";

export type * from "./types";

const AGENT_BASE = `${YAK_OPS_API_PREFIX}/agent`;

export async function submitAgentTurn(input: {
  message: string;
  projectCode: string;
  sessionId?: string;
}): Promise<AgentTurnSubmission> {
  return HttpUtils.postData<AgentTurnSubmission>(`${AGENT_BASE}/chat/turns`, input, {
    businessErrorMode: "reject",
  });
}

export async function fetchAgentTurn(turnId: string): Promise<AgentTurnResult> {
  return HttpUtils.getData<AgentTurnResult>(`${AGENT_BASE}/turns/${encodeURIComponent(turnId)}`, {
    businessErrorMode: "reject",
  });
}

export async function fetchAgentSessionMessages(
  sessionId: string,
  limit = 50,
): Promise<AgentSessionMessage[]> {
  return HttpUtils.getData<AgentSessionMessage[]>(
    `${AGENT_BASE}/sessions/${encodeURIComponent(sessionId)}/messages?limit=${limit}`,
    { businessErrorMode: "reject" },
  );
}

export async function streamAgentTurnEvents(
  turnId: string,
  onEvent: (event: AgentStreamEvent) => void,
  signal?: AbortSignal,
): Promise<void> {
  const response = await fetch(`${AGENT_BASE}/turns/${encodeURIComponent(turnId)}/events`, {
    method: "GET",
    headers: { Accept: "text/event-stream" },
    credentials: "same-origin",
    signal,
  });
  if (!response.ok || !response.body) {
    throw new Error(`订阅助手事件流失败（HTTP ${response.status}）`);
  }

  const reader = response.body.getReader();
  const decoder = new TextDecoder();
  let buffer = "";
  try {
    for (;;) {
      const { value, done } = await reader.read();
      if (done) {
        break;
      }
      buffer += decoder.decode(value, { stream: true });
      let boundary = buffer.indexOf("\n\n");
      while (boundary >= 0) {
        const frame = buffer.slice(0, boundary);
        buffer = buffer.slice(boundary + 2);
        const event = parseSseFrame(frame);
        if (event) {
          onEvent(event);
        }
        boundary = buffer.indexOf("\n\n");
      }
    }
  } finally {
    reader.releaseLock();
  }
}

function parseSseFrame(frame: string): AgentStreamEvent | null {
  let type = "message";
  let id: string | undefined;
  const dataLines: string[] = [];
  for (const line of frame.split("\n")) {
    if (line.startsWith("event:")) {
      type = line.slice(6).trim();
    } else if (line.startsWith("data:")) {
      dataLines.push(line.slice(5).trimStart());
    } else if (line.startsWith("id:")) {
      id = line.slice(3).trim();
    }
  }
  if (dataLines.length === 0) {
    return null;
  }
  const raw = dataLines.join("\n");
  let data: Record<string, unknown> = {};
  try {
    data = JSON.parse(raw) as Record<string, unknown>;
  } catch {
    data = { message: raw };
  }
  return { id, type, data };
}
