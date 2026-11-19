import test from "node:test";
import assert from "node:assert/strict";
import { readFileSync } from "node:fs";

import {
  createAgentSession,
  deleteAgentSession,
  listAgentSessions,
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

test("all JSON session endpoints carry the current user and workspace identity", async () => {
  const originalFetch = globalThis.fetch;
  const calls = [];

  globalThis.fetch = async (input, init = {}) => {
    calls.push({ url: String(input), init });
    const payload =
      init.method === "GET"
        ? []
        : init.method === "DELETE"
          ? { deleted: true }
          : { sessionId: "session-1" };
    return new Response(JSON.stringify(payload), {
      status: 200,
      headers: { "Content-Type": "application/json" },
    });
  };

  try {
    await createAgentSession(identity);
    await listAgentSessions(identity);
    await deleteAgentSession("session-1", identity);

    assert.equal(calls.length, 3);
    for (const call of calls) {
      const headers = new Headers(call.init.headers);
      assert.equal(headers.get("X-Yak-User-Id"), identity.userId);
      assert.equal(headers.get("X-Yak-Workspace-Id"), identity.workspaceId);
      assert.equal(call.init.credentials, "include");
    }
    assert.equal(calls[0].init.method, "POST");
    assert.equal(calls[1].init.method, "GET");
    assert.equal(calls[2].init.method, "DELETE");
  } finally {
    globalThis.fetch = originalFetch;
  }
});

test("SSE confirmation pauses the stream until the frontend posts the approval decision", async () => {
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
    assert.equal(calls.length, 2);
    assert.match(calls[0].url, /\/agent\/sessions\/session-1\/messages$/);
    assert.match(calls[1].url, /\/agent\/sessions\/session-1\/confirm$/);
    assert.deepEqual(JSON.parse(calls[1].init.body), { approved: true });
    for (const call of calls) {
      const headers = new Headers(call.init.headers);
      assert.equal(headers.get("X-Yak-User-Id"), identity.userId);
      assert.equal(headers.get("X-Yak-Workspace-Id"), identity.workspaceId);
    }
    assert.equal(calls[0].init.credentials, "include");
    assert.deepEqual(events, [
      { type: "text_delta", delta: "创建完成" },
      { type: "done" },
    ]);
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
