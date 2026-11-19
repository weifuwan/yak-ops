"""SSE contract for AgentScope stream events forwarded by the HTTP service."""

import asyncio
import unittest

from agentscope.event import (
    ModelCallEndEvent,
    ReplyEndEvent,
    TextBlockDeltaEvent,
    ThinkingBlockDeltaEvent,
    ThinkingBlockEndEvent,
    ThinkingBlockStartEvent,
    ToolCallDeltaEvent,
    ToolCallStartEvent,
    ToolResultEndEvent,
    ToolResultTextDeltaEvent,
)
from agentscope.message import ToolResultState
from agentscope.types import ErrorInfo, ReplyFinishedReason

from yak_agent.server import Session, _forward


class ForwardEventsTest(unittest.TestCase):
    def setUp(self):
        self.session = Session(sid="test", agent=None, workspace=None)

    def forward(self, event):
        asyncio.run(_forward(self.session, event))
        return self.session.queue.get_nowait()

    def test_text_delta(self):
        payload = self.forward(TextBlockDeltaEvent(reply_id="r1", block_id="b1", delta="hello"))
        self.assertEqual(
            payload,
            {"type": "text_delta", "delta": "hello", "replyId": "r1", "blockId": "b1"},
        )

    def test_thinking_blocks(self):
        self.assertEqual(
            self.forward(ThinkingBlockStartEvent(reply_id="r1", block_id="b1")),
            {"type": "thinking_start", "replyId": "r1", "blockId": "b1"},
        )
        self.assertEqual(
            self.forward(ThinkingBlockDeltaEvent(reply_id="r1", block_id="b1", delta="checking")),
            {
                "type": "thinking_delta",
                "delta": "checking",
                "replyId": "r1",
                "blockId": "b1",
            },
        )
        self.assertEqual(
            self.forward(ThinkingBlockEndEvent(reply_id="r1", block_id="b1")),
            {"type": "thinking_end", "replyId": "r1", "blockId": "b1"},
        )

    def test_tool_call_events(self):
        self.assertEqual(
            self.forward(
                ToolCallStartEvent(
                    reply_id="r1", tool_call_id="t1", tool_call_name="list_sync_tasks"
                )
            ),
            {
                "type": "tool_start",
                "tool": "list_sync_tasks",
                "toolCallId": "t1",
                "replyId": "r1",
            },
        )
        self.assertEqual(
            self.forward(ToolCallDeltaEvent(reply_id="r1", tool_call_id="t1", delta='{"st')),
            {
                "type": "tool_call_delta",
                "delta": '{"st',
                "toolCallId": "t1",
                "replyId": "r1",
            },
        )

    def test_tool_result_events(self):
        self.assertEqual(
            self.forward(ToolResultTextDeltaEvent(reply_id="r1", tool_call_id="t1", delta="ok")),
            {
                "type": "tool_result_delta",
                "delta": "ok",
                "toolCallId": "t1",
                "replyId": "r1",
            },
        )
        self.assertEqual(
            self.forward(
                ToolResultEndEvent(reply_id="r1", tool_call_id="t1", state=ToolResultState.SUCCESS)
            ),
            {
                "type": "tool_result",
                "toolCallId": "t1",
                "state": "success",
                "replyId": "r1",
            },
        )

    def test_usage(self):
        payload = self.forward(
            ModelCallEndEvent(
                reply_id="r1",
                input_tokens=120,
                output_tokens=30,
                cache_input_tokens=12,
                cache_creation_input_tokens=3,
            )
        )
        self.assertEqual(payload["type"], "usage")
        self.assertEqual(payload["inputTokens"], 120)
        self.assertEqual(payload["outputTokens"], 30)
        self.assertEqual(payload["cacheInputTokens"], 12)
        self.assertEqual(payload["cacheCreationInputTokens"], 3)

    def test_reply_end(self):
        payload = self.forward(
            ReplyEndEvent(
                session_id="s1",
                reply_id="r1",
                finished_reason=ReplyFinishedReason.COMPLETED,
            )
        )
        self.assertEqual(payload["type"], "reply_end")
        self.assertEqual(payload["finishedReason"], "completed")
        self.assertIsNone(payload["error"])

        payload = self.forward(
            ReplyEndEvent(
                session_id="s1",
                reply_id="r1",
                finished_reason=ReplyFinishedReason.EXCEED_MAX_ITERS,
            )
        )
        self.assertEqual(payload["finishedReason"], "exceed_max_iters")

        payload = self.forward(
            ReplyEndEvent(
                session_id="s1",
                reply_id="r1",
                finished_reason=ReplyFinishedReason.ERROR,
                error=ErrorInfo(message="boom"),
            )
        )
        self.assertEqual(payload["finishedReason"], "error")
        self.assertEqual(payload["error"], {"type": "unknown", "message": "boom"})

    def test_empty_deltas_are_not_forwarded(self):
        asyncio.run(
            _forward(self.session, TextBlockDeltaEvent(reply_id="r1", block_id="b1", delta=""))
        )
        self.assertTrue(self.session.queue.empty())
