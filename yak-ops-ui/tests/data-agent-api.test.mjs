import test from "node:test";
import assert from "node:assert/strict";

import { parseAgentSseFrame, sendAgentMessage } from "../apps/web/service/data-agent/index.ts";
import { readYakSessionId } from "../apps/web/agent-proxy.ts";

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
    assert.equal(calls[0].init.credentials, "include");
    assert.deepEqual(events, [
      { type: "text_delta", delta: "创建完成" },
      { type: "done" },
    ]);
  } finally {
    globalThis.fetch = originalFetch;
  }
});
