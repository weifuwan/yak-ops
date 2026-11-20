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
            {
              type: "thinking",
              id: "thinking-1",
              startedAt: "2026-11-20T09:00:01.000Z",
              completedAt: "2026-11-20T09:00:02.250Z",
            },
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
  assert.deepEqual(
    conversation.messages[1].activities.map((activity) => activity.kind),
    ["thinking", "text", "tool"],
  );
  assert.equal(conversation.messages[1].activities[0].status, "completed");
  assert.equal(
    conversation.messages[1].activities[0].startedAt,
    Date.parse("2026-11-20T09:00:01.000Z"),
  );
  assert.equal(
    conversation.messages[1].activities[0].completedAt,
    Date.parse("2026-11-20T09:00:02.250Z"),
  );
  assert.equal(conversation.messages[1].activities[0].content, undefined);
  assert.equal(conversation.messages[1].activities[1].content, "我先检查字段。");
  assert.equal(conversation.messages[1].activities[2].output, "12 columns");
  assert.equal(conversation.messages[1].activities[2].status, "completed");
  assert.equal(conversation.messages[1].activities[2].startedAt, undefined);
});

test("restored incomplete thinking blocks do not show a live-updating duration", () => {
  const conversation = mapAgentHistoryToConversation(
    "session-2",
    { title: "中断会话", preview: "", updatedAt: "2026-11-20T09:00:05.000Z" },
    {
      sessionId: "session-2",
      truncated: false,
      messages: [
        {
          id: "assistant-2",
          role: "assistant",
          createdAt: "2026-11-20T09:00:00.000Z",
          blocks: [
            {
              type: "thinking",
              id: "thinking-open",
              startedAt: "2026-11-20T09:00:01.000Z",
            },
          ],
        },
      ],
    },
  );

  const activity = conversation.messages[0].activities[0];
  assert.equal(activity.kind, "thinking");
  assert.equal(activity.status, "interrupted");
  assert.equal(activity.startedAt, Date.parse("2026-11-20T09:00:01.000Z"));
  assert.equal(activity.completedAt, undefined);
});
