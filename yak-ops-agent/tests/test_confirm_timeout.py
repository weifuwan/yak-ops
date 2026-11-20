"""Confirmation timeout: deny pending calls, audit the reason, release the session."""

from __future__ import annotations

import asyncio
import unittest
from types import SimpleNamespace
from unittest.mock import patch

from agentscope.message import ToolCallBlock

from yak_agent.service import chat as chat_service
from yak_agent.service import session as session_service


def _paused(count: int = 2) -> SimpleNamespace:
    return SimpleNamespace(
        reply_id="reply-1",
        tool_calls=[
            ToolCallBlock(id=f"t{index}", name=f"tool{index}", input="{}") for index in range(count)
        ],
    )


def _session(paused: SimpleNamespace) -> session_service.Session:
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


if __name__ == "__main__":
    unittest.main()
