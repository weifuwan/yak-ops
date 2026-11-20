"""Confirmation timeout: deny pending calls, audit the reason, release the session."""

from __future__ import annotations

import asyncio
import unittest
from types import SimpleNamespace
from typing import Any
from unittest.mock import patch

from agentscope.event import RequireUserConfirmEvent, UserConfirmResultEvent
from agentscope.message import ToolCallBlock

from yak_agent.service import chat as chat_service
from yak_agent.service import session as session_service


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
        self.assertFalse(session.queue.empty())
        self.assertEqual(session.queue.get_nowait()["status"], "error")

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
        events = []
        while not session.queue.empty():
            events.append(session.queue.get_nowait())
        self.assertEqual(events[-1]["type"], "done")


if __name__ == "__main__":
    unittest.main()
