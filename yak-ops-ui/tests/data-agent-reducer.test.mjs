import test from "node:test";
import assert from "node:assert/strict";

import {
  conversationReducer,
  initialConversationState,
} from "../apps/web/app/data-agent/model/conversation-reducer.ts";

const now = "2026-11-19T10:00:00.000Z";

function createMessages() {
  return {
    userMessage: {
      id: "user-1",
      role: "user",
      content: "创建一个同步任务",
      createdAt: now,
    },
    assistantMessage: {
      id: "assistant-1",
      role: "assistant",
      content: "",
      createdAt: now,
      status: "streaming",
      toolCalls: [],
    },
  };
}

test("conversation reducer applies streamed text and tool lifecycle transitions", () => {
  const { userMessage, assistantMessage } = createMessages();
  let state = conversationReducer(initialConversationState, {
    type: "MESSAGE_PAIR_ADDED",
    sessionId: "session-1",
    title: "创建一个同步任务",
    updatedAt: now,
    userMessage,
    assistantMessage,
  });

  state = conversationReducer(state, {
    type: "TEXT_DELTA",
    sessionId: "session-1",
    messageId: "assistant-1",
    delta: "我先检查一下。",
    updatedAt: now,
  });
  state = conversationReducer(state, {
    type: "TOOL_STARTED",
    sessionId: "session-1",
    messageId: "assistant-1",
    toolCallId: "tool-1",
    toolName: "list_sync_tasks",
    updatedAt: now,
  });
  state = conversationReducer(state, {
    type: "TOOL_COMPLETED",
    sessionId: "session-1",
    messageId: "assistant-1",
    toolCallId: "tool-1",
    updatedAt: now,
  });
  state = conversationReducer(state, {
    type: "MESSAGE_COMPLETED",
    sessionId: "session-1",
    messageId: "assistant-1",
    updatedAt: now,
  });

  const message = state.conversations[0].messages[1];
  assert.equal(state.activeConversationId, "session-1");
  assert.equal(message.content, "我先检查一下。");
  assert.equal(message.status, "complete");
  assert.equal(message.toolCalls[0].status, "completed");
});

test("conversation reducer preserves confirmation decisions and restored sessions", () => {
  const { userMessage, assistantMessage } = createMessages();
  let state = conversationReducer(initialConversationState, {
    type: "MESSAGE_PAIR_ADDED",
    sessionId: "session-1",
    title: "创建同步任务",
    updatedAt: now,
    userMessage,
    assistantMessage,
  });

  state = conversationReducer(state, {
    type: "CONFIRMATION_REQUIRED",
    sessionId: "session-1",
    messageId: "assistant-1",
    toolCalls: [
      {
        id: "tool-2",
        name: "create_sync_task",
        input: "{\"name\":\"daily_sync\"}",
        status: "awaiting-confirmation",
      },
    ],
    updatedAt: now,
  });
  assert.equal(
    state.conversations[0].messages[1].status,
    "awaiting-confirmation",
  );

  state = conversationReducer(state, {
    type: "CONFIRMATION_RESOLVED",
    sessionId: "session-1",
    messageId: "assistant-1",
    approved: true,
    updatedAt: now,
  });
  assert.equal(state.conversations[0].messages[1].status, "streaming");
  assert.equal(
    state.conversations[0].messages[1].toolCalls[0].status,
    "approved",
  );

  state = conversationReducer(state, {
    type: "RESTORE_CONVERSATIONS",
    conversations: [
      {
        id: "session-1",
        title: "重复会话",
        updatedAt: now,
        messages: [],
        restored: true,
      },
      {
        id: "session-2",
        title: "历史会话",
        updatedAt: now,
        messages: [],
        restored: true,
      },
    ],
  });

  assert.equal(state.conversations.length, 2);
  assert.equal(state.conversations[1].id, "session-2");
  assert.equal(state.conversations[0].messages.length, 2);
});

test("conversation reducer ignores stream events for missing messages", () => {
  const state = conversationReducer(initialConversationState, {
    type: "TEXT_DELTA",
    sessionId: "missing-session",
    messageId: "missing-message",
    delta: "ignored",
    updatedAt: now,
  });

  assert.equal(state, initialConversationState);
});
