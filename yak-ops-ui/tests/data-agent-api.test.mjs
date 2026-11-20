import test from "node:test";
import assert from "node:assert/strict";
import { readFileSync } from "node:fs";

import {
  confirmAgentSession,
  createAgentSession,
  deleteAgentSession,
  getAgentSessionMessages,
  getAgentSessionStatus,
  interruptAgentSession,
  listAgentSessions,
  renameAgentSession,
  parseAgentSseFrame,
  sendAgentMessage,
} from "../apps/web/service/data-agent/index.ts";
import { createDataAgentProxy, readYakSessionId } from "../apps/web/agent-proxy.ts";

const identity = { userId: "42", workspaceId: "9001" };

test("SSE parser reads text, tool, confirmation, completion and error events", () => {
  assert.deepEqual(
    parseAgentSseFrame('event: text_delta\ndata: {"type":"text_delta","delta":"hello"}'),
    { type: "text_delta", delta: "hello" },
  );
  assert.deepEqual(
    parseAgentSseFrame(
      'event: tool_start\ndata: {"type":"tool_start","tool":"list_sync_tasks","toolCallId":"tool-1"}',
    ),
    { type: "tool_start", tool: "list_sync_tasks", toolCallId: "tool-1" },
  );
  assert.deepEqual(
    parseAgentSseFrame(
      'event: confirm_required\ndata: {"type":"confirm_required","replyId":"reply-1","toolCalls":[{"id":"tool-2","name":"create_sync_task","input":"{}"}]}',
    ),
    {
      type: "confirm_required",
      replyId: "reply-1",
      toolCalls: [{ id: "tool-2", name: "create_sync_task", input: "{}" }],
    },
  );
  assert.deepEqual(parseAgentSseFrame('event: done\ndata: {"type":"done"}'), { type: "done" });
  assert.deepEqual(
    parseAgentSseFrame('event: error\ndata: {"type":"error","message":"failed"}'),
    { type: "error", message: "failed" },
  );
  assert.deepEqual(
    parseAgentSseFrame('event: thinking_start\ndata: {"type":"thinking_start","replyId":"reply-1","blockId":"block-1"}'),
    { type: "thinking_start", replyId: "reply-1", blockId: "block-1" },
  );
  assert.deepEqual(
    parseAgentSseFrame('event: thinking_delta\ndata: {"type":"thinking_delta","delta":"private reasoning","replyId":"reply-1","blockId":"block-1"}'),
    { type: "thinking_delta", delta: "private reasoning", replyId: "reply-1", blockId: "block-1" },
  );
  assert.deepEqual(
    parseAgentSseFrame('event: thinking_end\ndata: {"type":"thinking_end","replyId":"reply-1","blockId":"block-1"}'),
    { type: "thinking_end", replyId: "reply-1", blockId: "block-1" },
  );
  assert.deepEqual(
    parseAgentSseFrame('event: tool_call_delta\ndata: {"type":"tool_call_delta","toolCallId":"tool-1","delta":"{\\"name\\":"}'),
    { type: "tool_call_delta", toolCallId: "tool-1", delta: '{"name":' },
  );
  assert.deepEqual(
    parseAgentSseFrame('event: tool_result_delta\ndata: {"type":"tool_result_delta","toolCallId":"tool-1","delta":"ok"}'),
    { type: "tool_result_delta", toolCallId: "tool-1", delta: "ok" },
  );
  assert.deepEqual(
    parseAgentSseFrame('event: tool_result\ndata: {"type":"tool_result","toolCallId":"tool-1","state":"error"}'),
    { type: "tool_result", toolCallId: "tool-1", state: "error" },
  );
  assert.deepEqual(
    parseAgentSseFrame('event: progress\ndata: {"type":"progress","phase":"analysis","title":"正在分析你的需求","status":"running"}'),
    { type: "progress", phase: "analysis", title: "正在分析你的需求", status: "running" },
  );
  assert.deepEqual(
    parseAgentSseFrame('event: usage\ndata: {"type":"usage","inputTokens":120,"outputTokens":35,"cacheInputTokens":12,"cacheCreationInputTokens":3}'),
    { type: "usage", inputTokens: 120, outputTokens: 35, cacheInputTokens: 12, cacheCreationInputTokens: 3 },
  );
  assert.deepEqual(
    parseAgentSseFrame('event: reply_end\ndata: {"type":"reply_end","replyId":"reply-1","finishedReason":"completed","error":null}'),
    { type: "reply_end", replyId: "reply-1", finishedReason: "completed", error: null },
  );
  assert.equal(parseAgentSseFrame(": keep-alive"), null);
});

test("Agent proxy extracts the HttpOnly Yak-OPS session cookie without exposing it to browser code", () => {
  assert.equal(
    readYakSessionId("theme=dark; sessionId=abc%2F123%3D; other=value"),
    "abc/123=",
  );
  assert.equal(readYakSessionId("theme=dark; other=value"), null);
  assert.equal(readYakSessionId("sessionId=%ZZ"), "%ZZ");
});

test("Agent proxy forwards verified-context identity headers and removes the browser cookie", () => {
  const proxyOptions = createDataAgentProxy("http://127.0.0.1:8090");
  let onProxyRequest;

  proxyOptions.configure({
    on(event, handler) {
      assert.equal(event, "proxyReq");
      onProxyRequest = handler;
    },
  });

  const forwardedHeaders = new Map();
  const proxyRequest = {
    setHeader(name, value) {
      forwardedHeaders.set(name.toLowerCase(), String(value));
    },
    removeHeader(name) {
      forwardedHeaders.delete(name.toLowerCase());
    },
  };

  onProxyRequest(proxyRequest, {
    headers: {
      cookie: "sessionId=abc123",
      "x-yak-user-id": "42",
      "x-yak-workspace-id": "9001",
    },
  });

  assert.equal(forwardedHeaders.get("x-yak-session-id"), "abc123");
  assert.equal(forwardedHeaders.get("x-yak-user-id"), "42");
  assert.equal(forwardedHeaders.get("x-yak-workspace-id"), "9001");
  assert.equal(forwardedHeaders.has("cookie"), false);
});

test("session lifecycle endpoints carry the current user and workspace identity", async () => {
  const originalFetch = globalThis.fetch;
  const calls = [];

  globalThis.fetch = async (input, init = {}) => {
    calls.push({ url: String(input), init });
    const url = String(input);
    const payload =
      init.method === "GET" && url.endsWith("/agent/sessions")
        ? []
        : init.method === "GET" && url.endsWith("/messages")
          ? { sessionId: "session-1", messages: [], truncated: false }
          : init.method === "GET"
            ? { sessionId: "session-1", state: "idle", busy: false, stale: false }
            : init.method === "DELETE"
              ? { deleted: true }
              : init.method === "PATCH"
                ? { sessionId: "session-1", title: "新标题" }
                : init.method === "POST" && url.endsWith("/agent/sessions")
                  ? { sessionId: "session-1" }
                  : init.method === "POST" && url.endsWith("/interrupt")
                    ? { accepted: true, state: "interrupting" }
                    : { accepted: true };
    return new Response(JSON.stringify(payload), {
      status: 200,
      headers: { "Content-Type": "application/json" },
    });
  };

  try {
    await createAgentSession(identity);
    await listAgentSessions(identity);
    await getAgentSessionStatus("session-1", identity);
    await getAgentSessionMessages("session-1", identity);
    await renameAgentSession("session-1", "新标题", identity);
    await interruptAgentSession("session-1", "turn-1", identity);
    await confirmAgentSession("session-1", true, identity, new AbortController().signal);
    await deleteAgentSession("session-1", identity);

    assert.equal(calls.length, 8);
    for (const call of calls) {
      const headers = new Headers(call.init.headers);
      assert.equal(headers.get("X-Yak-User-Id"), identity.userId);
      assert.equal(headers.get("X-Yak-Workspace-Id"), identity.workspaceId);
      assert.equal(call.init.credentials, "include");
    }
    assert.equal(calls[0].init.method, "POST");
    assert.equal(calls[1].init.method, "GET");
    assert.equal(calls[2].init.method, "GET");
    assert.equal(calls[3].init.method, "GET");
    assert.equal(calls[4].init.method, "PATCH");
    assert.equal(calls[5].init.method, "POST");
    assert.equal(calls[6].init.method, "POST");
    assert.equal(calls[7].init.method, "DELETE");
    assert.match(calls[2].url, /\/agent\/sessions\/session-1$/);
    assert.match(calls[3].url, /\/agent\/sessions\/session-1\/messages$/);
    assert.match(calls[4].url, /\/agent\/sessions\/session-1$/);
    assert.match(calls[5].url, /\/agent\/sessions\/session-1\/interrupt$/);
    assert.match(calls[6].url, /\/agent\/sessions\/session-1\/confirm$/);
  } finally {
    globalThis.fetch = originalFetch;
  }
});

test("SSE confirmation prompts do not block event consumption or post decisions implicitly", async () => {
  const originalFetch = globalThis.fetch;
  const calls = [];
  const streamText = [
    'event: confirm_required\ndata: {"type":"confirm_required","replyId":"reply-1","toolCalls":[{"id":"tool-1","name":"create_sync_task","input":"{}"}]}',
    'event: text_delta\ndata: {"type":"text_delta","delta":"创建完成"}',
    'event: done\ndata: {"type":"done"}',
    "",
  ].join("\n\n");

  globalThis.fetch = async (input, init = {}) => {
    const url = String(input);
    calls.push({ url, init });

    if (url.endsWith("/messages")) {
      const stream = new ReadableStream({
        start(controller) {
          controller.enqueue(new TextEncoder().encode(streamText));
          controller.close();
        },
      });
      return new Response(stream, {
        status: 200,
        headers: { "Content-Type": "text/event-stream" },
      });
    }

    if (url.endsWith("/confirm")) {
      return new Response(JSON.stringify({ accepted: true }), {
        status: 200,
        headers: { "Content-Type": "application/json" },
      });
    }

    throw new Error("Unexpected Agent request: " + url);
  };

  try {
    const events = [];
    let confirmationRequested = false;
    await sendAgentMessage("session-1", "创建同步任务", {
      identity,
      signal: new AbortController().signal,
      onEvent: (event) => events.push(event),
      onConfirmRequired: async (event) => {
        confirmationRequested = event.toolCalls[0]?.name === "create_sync_task";
        return true;
      },
    });

    assert.equal(confirmationRequested, true);
    assert.equal(calls.length, 1);
    assert.match(calls[0].url, /\/agent\/sessions\/session-1\/messages$/);
    for (const call of calls) {
      const headers = new Headers(call.init.headers);
      assert.equal(headers.get("X-Yak-User-Id"), identity.userId);
      assert.equal(headers.get("X-Yak-Workspace-Id"), identity.workspaceId);
    }
    assert.equal(calls[0].init.credentials, "include");
    assert.deepEqual(events.map((event) => event.type), [
      "confirm_required",
      "text_delta",
      "done",
    ]);
    assert.equal(events[0].replyId, "reply-1");
  } finally {
    globalThis.fetch = originalFetch;
  }
});

test("conversation hook takes user and workspace identity from the active application contexts", () => {
  const hook = readFileSync(
    new URL("../apps/web/hooks/useDataAgentConversation.ts", import.meta.url),
    "utf8",
  );
  assert.match(hook, /useAuth\(\)/);
  assert.match(hook, /useWorkspace\(\)/);
  assert.match(hook, /userId: String\(currentUser\.id\)/);
  assert.match(hook, /workspaceId: String\(currentWorkspace\.id\)/);
  assert.match(hook, /listAgentSessions\(requestIdentity/);
  assert.match(hook, /createAgentSession\(requestIdentity/);
  assert.match(hook, /identity: requestIdentity/);
  assert.match(hook, /type: "RESET"/);
});


test("SSE reconnect resumes from the last acknowledged event sequence", async () => {
  const originalFetch = globalThis.fetch;
  const calls = [];
  const firstFrame = 'id: 7\nevent: text_delta\ndata: {"type":"text_delta","seq":7,"delta":"partial"}\n\n';
  const resumedFrame = 'id: 8\nevent: done\ndata: {"type":"done","seq":8}\n\n';

  globalThis.fetch = async (input, init = {}) => {
    const url = String(input);
    calls.push({ url, init });
    const payload = url.endsWith("/messages") ? firstFrame : resumedFrame;
    const stream = new ReadableStream({
      start(controller) {
        controller.enqueue(new TextEncoder().encode(payload));
        controller.close();
      },
    });
    return new Response(stream, {
      status: 200,
      headers: { "Content-Type": "text/event-stream" },
    });
  };

  try {
    const events = [];
    let reconnecting = false;
    await sendAgentMessage("session-1", "继续查询", {
      identity,
      signal: new AbortController().signal,
      onEvent: (event) => events.push(event),
      onConfirmRequired: () => undefined,
      onReconnect: () => {
        reconnecting = true;
      },
    });

    assert.equal(calls.length, 2);
    assert.match(calls[0].url, /\/messages$/);
    assert.match(calls[1].url, /\/stream\?after=7$/);
    assert.equal(reconnecting, true);
    assert.deepEqual(events.map((event) => event.type), ["text_delta", "done"]);
    assert.equal(events[0].seq, 7);
    assert.equal(events[1].seq, 8);
  } finally {
    globalThis.fetch = originalFetch;
  }
});

test("SSE stream forwards activity, tool details, usage and reply lifecycle events", async () => {
  const originalFetch = globalThis.fetch;
  const frames = [
    { type: "progress", phase: "analysis", title: "正在分析你的需求", status: "running" },
    { type: "thinking_start", replyId: "reply-1", blockId: "block-1" },
    { type: "thinking_delta", replyId: "reply-1", blockId: "block-1", delta: "private" },
    { type: "thinking_end", replyId: "reply-1", blockId: "block-1" },
    { type: "text_delta", replyId: "reply-1", blockId: "text-1", delta: "我先检查一下。" },
    { type: "tool_start", tool: "list_sync_tasks", toolCallId: "tool-1", replyId: "reply-1" },
    { type: "tool_call_delta", toolCallId: "tool-1", delta: "{\"keyword\":\"orders\"}" },
    { type: "tool_result_delta", toolCallId: "tool-1", delta: "{\"total\":1}" },
    { type: "tool_result", toolCallId: "tool-1", state: "success" },
    { type: "usage", replyId: "reply-1", inputTokens: 120, outputTokens: 35, cacheInputTokens: 12, cacheCreationInputTokens: 3 },
    { type: "reply_end", replyId: "reply-1", finishedReason: "completed", error: null },
    { type: "done" },
  ];
  const streamText = frames
    .map((event) => "event: " + event.type + "\ndata: " + JSON.stringify(event))
    .join("\n\n") + "\n\n";

  globalThis.fetch = async () => {
    const stream = new ReadableStream({
      start(controller) {
        controller.enqueue(new TextEncoder().encode(streamText));
        controller.close();
      },
    });
    return new Response(stream, {
      status: 200,
      headers: { "Content-Type": "text/event-stream" },
    });
  };

  try {
    const events = [];
    await sendAgentMessage("session-1", "查询同步任务", {
      identity,
      signal: new AbortController().signal,
      onEvent: (event) => events.push(event),
      onConfirmRequired: async () => true,
    });

    assert.deepEqual(events.map((event) => event.type), frames.map((event) => event.type));
    assert.equal(events.find((event) => event.type === "tool_call_delta").delta, "{\"keyword\":\"orders\"}");
    assert.equal(events.find((event) => event.type === "tool_result").state, "success");
    assert.equal(events.find((event) => event.type === "usage").outputTokens, 35);
    assert.equal(events.find((event) => event.type === "reply_end").finishedReason, "completed");
  } finally {
    globalThis.fetch = originalFetch;
  }
});
