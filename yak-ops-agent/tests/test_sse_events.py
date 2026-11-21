"""SSE mapping for the remaining tool lifecycle events."""

from __future__ import annotations

import asyncio
import unittest
import warnings

from agentscope.event import (
    CustomEvent,
    ExceedMaxItersEvent,
    HintBlockEvent,
    ReplyStartEvent,
    ToolCallEndEvent,
    ToolResultStartEvent,
)

from yak_agent.api.sse import forward_event
from yak_agent.service.events import EventLog
from yak_agent.service.session import Session


def _session() -> Session:
    return Session(
        sid="sid",
        agent=object(),
        workspace=object(),
        event_log=EventLog(),
    )


def _forward(event) -> list[dict]:
    session = _session()
    asyncio.run(forward_event(session, event))
    return list(session.event_log.events)


class SseLifecycleTests(unittest.TestCase):
    def test_reply_start_payload(self) -> None:
        events = _forward(ReplyStartEvent(session_id="sid", reply_id="reply-1", name="yak_agent"))

        self.assertEqual(len(events), 1)
        self.assertEqual(
            events[0]["type"],
            "reply_start",
        )
        self.assertEqual(events[0]["replyId"], "reply-1")
        self.assertEqual(events[0]["name"], "yak_agent")
        self.assertEqual(events[0]["role"], "assistant")

    def test_tool_call_end_payload(self) -> None:
        events = _forward(ToolCallEndEvent(reply_id="reply-1", tool_call_id="call-1"))

        self.assertEqual(events[0]["type"], "tool_call_end")
        self.assertEqual(events[0]["toolCallId"], "call-1")
        self.assertEqual(events[0]["replyId"], "reply-1")

    def test_tool_result_start_payload(self) -> None:
        events = _forward(
            ToolResultStartEvent(
                reply_id="reply-1",
                tool_call_id="call-1",
                tool_call_name="list_sync_tasks",
            )
        )

        self.assertEqual(events[0]["type"], "tool_result_start")
        self.assertEqual(events[0]["tool"], "list_sync_tasks")
        self.assertEqual(events[0]["toolCallId"], "call-1")

    def test_exceed_max_iters_payload(self) -> None:
        with warnings.catch_warnings():
            warnings.simplefilter("ignore", DeprecationWarning)
            event = ExceedMaxItersEvent(reply_id="reply-1", name="yak_agent")
        events = _forward(event)

        self.assertEqual(events[0]["type"], "exceed_max_iters")
        self.assertEqual(events[0]["replyId"], "reply-1")

    def test_hint_block_is_not_forwarded(self) -> None:
        events = _forward(
            HintBlockEvent(
                reply_id="reply-1",
                block_id="hint-1",
                hint="internal prompt state",
                source="system",
            )
        )

        self.assertEqual(events, [])

    def test_custom_event_is_not_forwarded(self) -> None:
        events = _forward(CustomEvent(name="mcp_progress", value={"step": 1}))

        self.assertEqual(events, [])


if __name__ == "__main__":
    unittest.main()
