import test from "node:test";
import assert from "node:assert/strict";

import { mapAgentHistoryToConversation } from "../apps/web/app/data-agent/model/session-history.ts";

test("restored session history rebuilds user messages, assistant text and tool activities", () => {
  const conversation = mapAgentHistoryToConversation(
    "session-1",
    {
      sessionId: "session-1",
      title: "分析订单表",
      preview: "检查订单表字段",
      updatedAt: 1795152000,
    },
    {
      sessionId: "session-1",
      truncated: false,
      usage: {
        inputTokens: 120,
        outputTokens: 35,
        cacheInputTokens: 12,
        cacheCreationInputTokens: 3,
      },
      messages: [
        {
          id: "user-1",
          role: "user",
          createdAt: "2026-11-20T09:00:00.000Z",
          blocks: [{ type: "text", text: "分析订单表" }],
        },
        {
          id: "assistant-1",
          role: "assistant",
          createdAt: "2026-11-20T09:00:05.000Z",
          blocks: [
            { type: "text", text: "我先检查字段。" },
            { type: "tool_call", id: "tool-1", name: "describe_table", input: "{\"table\":\"orders\"}" },
            { type: "tool_result", id: "tool-1", name: "describe_table", state: "success", output: "12 columns" },
          ],
        },
      ],
    },
  );

  assert.equal(conversation.title, "分析订单表");
  assert.equal(conversation.preview, "检查订单表字段");
  assert.equal(conversation.messages.length, 2);
  assert.equal(conversation.messages[0].content, "分析订单表");
  assert.equal(conversation.messages[1].content, "我先检查字段。");
  assert.equal(conversation.messages[1].toolCalls[0].status, "completed");
  assert.equal(conversation.messages[1].activities[0].kind, "text");
  assert.equal(conversation.messages[1].activities[1].kind, "tool");
  assert.equal(conversation.messages[1].activities[1].output, "12 columns");
  assert.equal(conversation.messages[1].activities[1].status, "completed");
  assert.equal(conversation.messages[1].activities[1].startedAt, undefined);
});
