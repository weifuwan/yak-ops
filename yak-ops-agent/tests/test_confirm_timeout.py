"""Confirmation timeout: deny pending calls, audit the reason, release the session."""

from __future__ import annotations

import asyncio
import json
import unittest
from types import SimpleNamespace
from typing import Any
from unittest.mock import patch

from agentscope.event import RequireUserConfirmEvent, UserConfirmResultEvent
from agentscope.message import ToolCallBlock

from yak_agent.api import server
from yak_agent.service import chat as chat_service
from yak_agent.service import session as session_service
from yak_agent.service.errors import ConfirmationNotPending


def _tool_calls(count: int) -> list[ToolCallBlock]:
    return [
        ToolCallBlock(id=f"t{index}", name=f"tool{index}", input="{}") for index in range(count)
    ]


def _paused(count: int = 2) -> SimpleNamespace:
    return SimpleNamespace(reply_id="reply-1", tool_calls=_tool_calls(count))


def _session(paused: Any) -> session_service.Session:
    return session_service.Session(
        sid="sid",
        agent=object(),
        workspace=object(),
        owner="u-1",
        workspace_id="1",
        pending_event=paused,
    )


class ConfirmTimeoutTests(unittest.TestCase):
    def test_timeout_denies_every_call_and_audits_the_reason(self) -> None:
        paused = _paused(2)
        session = _session(paused)

        with patch.object(chat_service.audit, "record_tool_call") as audit:
            flags = asyncio.run(chat_service._await_confirmation(session, paused, 0.01))

        self.assertEqual(flags, [False, False])
        self.assertEqual(audit.call_count, 2)
        first = audit.call_args_list[0]
        self.assertEqual(first.args[0], "session_confirm_timeout")
        self.assertFalse(first.args[2])
        self.assertEqual(first.args[1]["toolCallId"], "t0")
        self.assertFalse(first.args[1]["confirmed"])
        self.assertIsNone(session.pending_event)
        self.assertIsNone(session.confirm_future)
        self.assertTrue(session.event_log.events)
        self.assertEqual(session.event_log.events[-1]["type"], "progress")
        self.assertEqual(session.event_log.events[-1]["status"], "error")

    def test_frontend_answer_before_the_deadline_is_returned_as_is(self) -> None:
        paused = _paused(2)
        session = _session(paused)

        async def answer() -> list[bool]:
            task = asyncio.create_task(chat_service._await_confirmation(session, paused, 5))
            await asyncio.sleep(0)
            session.confirm_future.set_result([True, False])
            return await task

        with patch.object(chat_service.audit, "record_tool_call") as audit:
            flags = asyncio.run(answer())

        self.assertEqual(flags, [True, False])
        audit.assert_not_called()
        self.assertIsNone(session.pending_event)

    def test_boolean_answer_expands_to_all_pending_calls(self) -> None:
        paused = _paused(3)
        session = _session(paused)

        async def answer() -> list[bool]:
            task = asyncio.create_task(chat_service._await_confirmation(session, paused, 5))
            await asyncio.sleep(0)
            session.confirm_future.set_result(True)
            return await task

        self.assertEqual(asyncio.run(answer()), [True, True, True])

    def test_late_confirmation_is_rejected_after_timeout(self) -> None:
        paused = _paused(2)
        session = _session(paused)
        identity = SimpleNamespace(owner="u-1", workspace_id="1", auth=None, persistent=True)

        with patch.object(chat_service.audit, "record_tool_call"):
            asyncio.run(chat_service._await_confirmation(session, paused, 0.01))

        with (
            patch.object(server, "_get_settings", return_value=object()),
            patch.object(server, "request_identity", return_value=identity),
            patch.object(server, "load_session", return_value=session),
        ):
            with self.assertRaises(ConfirmationNotPending):
                asyncio.run(server.confirm("sid", server.ConfirmBody(approved=True), None))


class _FakeAgent:
    def __init__(self, paused: RequireUserConfirmEvent) -> None:
        self.paused = paused
        self.messages: list[Any] = []

    async def reply_stream(self, message: Any):
        self.messages.append(message)
        if len(self.messages) == 1:
            yield self.paused


async def _noop_forward(session: session_service.Session, event: Any) -> None:
    return None


async def _wait_for(predicate: Any, attempts: int = 200) -> None:
    for _ in range(attempts):
        if predicate():
            return
        await asyncio.sleep(0.005)
    raise AssertionError("condition was not reached")


class RunTurnTimeoutTests(unittest.TestCase):
    def test_turn_continues_with_all_calls_denied(self) -> None:
        paused = RequireUserConfirmEvent(reply_id="reply-1", tool_calls=_tool_calls(2))
        session = _session(paused)
        session.agent = _FakeAgent(paused)

        async def scenario() -> None:
            async with asyncio.timeout(5):
                await chat_service.run_turn(session, "停止实例", _noop_forward, 0.01)

        with (
            patch.object(chat_service.persistence, "persist"),
            patch.object(chat_service.audit, "record_tool_call") as audit,
        ):
            asyncio.run(scenario())

        self.assertEqual(len(session.agent.messages), 2)
        result = session.agent.messages[1]
        self.assertIsInstance(result, UserConfirmResultEvent)
        self.assertEqual([item.confirmed for item in result.confirm_results], [False, False])
        self.assertEqual(audit.call_count, 2)
        self.assertFalse(session.busy)
        self.assertIsNone(session.pending_event)
        events = list(session.event_log.events)
        self.assertEqual(events[-1]["type"], "done")

    def test_cancelling_a_paused_turn_releases_the_session(self) -> None:
        paused = RequireUserConfirmEvent(reply_id="reply-1", tool_calls=_tool_calls(1))
        session = _session(paused)
        session.agent = _FakeAgent(paused)

        async def scenario() -> None:
            task = asyncio.create_task(chat_service.run_turn(session, "停止实例", _noop_forward, 5))
            await _wait_for(lambda: session.confirm_future is not None)
            task.cancel()
            with self.assertRaises(asyncio.CancelledError):
                await task

        with (
            patch.object(chat_service.persistence, "persist"),
            patch.object(chat_service.audit, "record_tool_call"),
        ):
            asyncio.run(scenario())

        self.assertIsNone(session.pending_event)
        self.assertIsNone(session.confirm_future)
        self.assertFalse(session.busy)


class SendMessageStreamTests(unittest.TestCase):
    def test_sse_stream_reports_timeout_and_finishes(self) -> None:
        paused = RequireUserConfirmEvent(reply_id="reply-1", tool_calls=_tool_calls(1))
        session = _session(paused)
        session.agent = _FakeAgent(paused)
        settings = SimpleNamespace(confirm_timeout_seconds=0.01)
        identity = SimpleNamespace(owner="u-1", workspace_id="1", auth=None, persistent=True)

        async def scenario() -> list[dict[str, Any]]:
            with (
                patch.object(server, "_get_settings", return_value=settings),
                patch.object(server, "request_identity", return_value=identity),
                patch.object(server, "load_session", return_value=session),
                patch.object(chat_service.persistence, "persist"),
                patch.object(chat_service.audit, "record_tool_call"),
            ):
                response = await server.send_message(
                    "sid",
                    server.MessageBody(message="停止实例"),
                    None,
                )
                return [item async for item in response.body_iterator]

        items = asyncio.run(scenario())

        event_types = [item["event"] for item in items]
        self.assertEqual(event_types[0], "progress")
        self.assertIn("confirm_required", event_types)
        self.assertEqual(event_types[-1], "done")
        self.assertNotIn("error", event_types)
        progress = [json.loads(item["data"]) for item in items if item["event"] == "progress"]
        timeout = [event for event in progress if event.get("phase") == "confirm"]
        self.assertEqual(len(timeout), 1)
        self.assertEqual(timeout[0]["status"], "error")
        self.assertEqual(timeout[0]["replyId"], "reply-1")
        self.assertFalse(session.busy)
        self.assertIsNone(session.pending_event)


if __name__ == "__main__":
    unittest.main()
