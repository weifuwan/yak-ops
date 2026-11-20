import type {
  AgentHistoryBlock,
  AgentHistoryMessage,
  AgentSessionHistory,
  AgentSessionSummary,
} from "@/service/data-agent";
import type {
  AgentActivity,
  AgentConversation,
  AgentMessage,
  AgentToolCall,
  ToolCallStatus,
} from "./types";

function toolStatus(value?: string): ToolCallStatus {
  switch (value) {
    case "error":
      return "error";
    case "denied":
      return "denied";
    case "interrupted":
      return "interrupted";
    case "success":
      return "completed";
    default:
      return "running";
  }
}

function textFromBlocks(blocks: AgentHistoryBlock[]) {
  return blocks
    .filter((block): block is Extract<AgentHistoryBlock, { type: "text" }> => block.type === "text")
    .map((block) => block.text)
    .join("");
}

function mapHistoryMessage(
  sessionId: string,
  source: AgentHistoryMessage,
  index: number,
): AgentMessage {
  const id = source.id || sessionId + ":history:" + String(index);
  const createdAt = source.createdAt && Number.isFinite(Date.parse(source.createdAt))
    ? new Date(source.createdAt).toISOString()
    : new Date().toISOString();
  if (source.role === "user") {
    return {
      id,
      role: "user",
      content: textFromBlocks(source.blocks),
      createdAt,
      status: "complete",
    };
  }

  const results = new Map(
    source.blocks
      .filter((block): block is Extract<AgentHistoryBlock, { type: "tool_result" }> => block.type === "tool_result")
      .map((block) => [block.id, block]),
  );

  const toolCalls: AgentToolCall[] = [];
  const activities: AgentActivity[] = [];

  source.blocks.forEach((block, blockIndex) => {
    if (block.type === "text" && block.text) {
      activities.push({
        id: "text:history:" + id + ":" + String(blockIndex),
        kind: "text",
        title: "回复内容",
        status: "completed",
        content: block.text,
      });
      return;
    }

    if (block.type === "tool_call") {
      const result = results.get(block.id);
      const status = toolStatus(result?.state);
      toolCalls.push({
        id: block.id,
        name: block.name,
        input: block.input,
        status,
      });
      activities.push({
        id: "tool:" + block.id,
        kind: "tool",
        title: block.name,
        status,
        toolCallId: block.id,
        toolName: block.name,
        input: block.input,
        output: result?.output ?? "",
      });
      return;
    }

    if (block.type === "tool_result" && !source.blocks.some(
      (candidate) => candidate.type === "tool_call" && candidate.id === block.id,
    )) {
      const status = toolStatus(block.state);
      activities.push({
        id: "tool:" + block.id,
        kind: "tool",
        title: block.name,
        status,
        toolCallId: block.id,
        toolName: block.name,
        output: block.output,
      });
    }
  });

  return {
    id,
    role: "assistant",
    content: textFromBlocks(source.blocks),
    createdAt,
    status: "complete",
    toolCalls,
    activities,
  };
}

export function mapAgentHistoryToConversation(
  sessionId: string,
  summary: AgentSessionSummary | AgentSessionStatusLike,
  history: AgentSessionHistory,
): AgentConversation {
  const messages = history.messages.map((message, index) =>
    mapHistoryMessage(sessionId, message, index),
  );
  const updatedAt = typeof summary.updatedAt === "number"
    ? new Date(summary.updatedAt * 1000).toISOString()
    : typeof summary.updatedAt === "string" && Number.isFinite(Date.parse(summary.updatedAt))
      ? new Date(summary.updatedAt).toISOString()
      : messages[messages.length - 1]?.createdAt ?? new Date().toISOString();

  return {
    id: sessionId,
    title: summary.title || "历史会话 · " + sessionId.slice(0, 6),
    preview: summary.preview || "",
    updatedAt,
    messages,
    restored: true,
  };
}

type AgentSessionStatusLike = {
  title: string;
  preview: string;
  updatedAt?: number | string | null;
};
