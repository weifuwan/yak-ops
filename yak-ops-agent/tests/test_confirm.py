"""Regression tests for HITL confirmation validation (backlog 4.7)."""

from __future__ import annotations

import asyncio
import unittest
from types import SimpleNamespace
from unittest.mock import patch

from agentscope.message import ToolCallBlock

from yak_agent.api import server
from yak_agent.service import chat as chat_service
from yak_agent.service import session as session_service
from yak_agent.service.errors import ConfirmationInvalid, ConfirmationNotPending


def _tool_calls(count: int = 2) -> list[ToolCallBlock]:
    return [
        ToolCallBlock(id=f"t{index}", name=f"tool{index}", input="{}") for index in range(count)
    ]


def _session(tool_calls: list[ToolCallBlock]) -> session_service.Session:
    return session_service.Session(
        sid="sid",
        agent=object(),
        workspace=object(),
        owner="u-1",
        workspace_id="1",
        pending_event=SimpleNamespace(reply_id="reply-1", tool_calls=tool_calls),
    )


def _identity() -> session_service.Identity:
    return session_service.Identity(
        owner="u-1",
        workspace_id="1",
        auth=None,
        persistent=True,
    )


async def _invoke(session: session_service.Session, approved: bool | list[bool]):
    loop = asyncio.get_running_loop()
    session.confirm_future = loop.create_future()
    with (
        patch.object(server, "_get_settings", return_value=object()),
        patch.object(server, "request_identity", return_value=_identity()),
        patch.object(server, "load_session", return_value=session),
    ):
        return await server.confirm(
            "sid",
            server.ConfirmBody(approved=approved),
            None,
        )


class ConfirmTests(unittest.TestCase):
    def test_bool_confirmation_expands_to_all_calls(self) -> None:
        session = _session(_tool_calls(2))

        with patch.object(server.audit, "record_tool_call") as audit:
            result = asyncio.run(_invoke(session, True))

        self.assertEqual(result, {"accepted": True})
        self.assertEqual(session.confirm_future.result(), [True, True])
        self.assertEqual(audit.call_count, 2)
        first = audit.call_args_list[0].args[1]
        self.assertEqual(first["toolCallId"], "t0")
        self.assertEqual(first["tool"], "tool0")
        self.assertTrue(first["confirmed"])

    def test_matching_list_is_accepted(self) -> None:
        session = _session(_tool_calls(2))

        with patch.object(server.audit, "record_tool_call") as audit:
            result = asyncio.run(_invoke(session, [True, False]))

        self.assertEqual(result, {"accepted": True})
        self.assertEqual(session.confirm_future.result(), [True, False])
        self.assertEqual(audit.call_count, 2)
        self.assertFalse(audit.call_args_list[1].args[1]["confirmed"])

    def test_short_list_is_rejected(self) -> None:
        session = _session(_tool_calls(2))

        with patch.object(server.audit, "record_tool_call") as audit:
            with self.assertRaises(ConfirmationInvalid):
                asyncio.run(_invoke(session, [True]))

        audit.assert_not_called()
        self.assertFalse(session.confirm_future.done())

    def test_long_list_is_rejected(self) -> None:
        session = _session(_tool_calls(2))

        with patch.object(server.audit, "record_tool_call") as audit:
            with self.assertRaises(ConfirmationInvalid):
                asyncio.run(_invoke(session, [True, False, True]))

        audit.assert_not_called()
        self.assertFalse(session.confirm_future.done())

    def test_empty_list_is_rejected(self) -> None:
        session = _session(_tool_calls(2))

        with patch.object(server.audit, "record_tool_call") as audit:
            with self.assertRaises(ConfirmationInvalid):
                asyncio.run(_invoke(session, []))

        audit.assert_not_called()
        self.assertFalse(session.confirm_future.done())

    def test_no_pending_confirmation(self) -> None:
        session = _session(_tool_calls(2))
        session.pending_event = None

        with patch.object(server.audit, "record_tool_call") as audit:
            with self.assertRaises(ConfirmationNotPending):
                asyncio.run(_invoke(session, True))

        audit.assert_not_called()

    def test_empty_pending_calls_are_rejected(self) -> None:
        session = _session([])

        with patch.object(server.audit, "record_tool_call") as audit:
            with self.assertRaises(ConfirmationNotPending):
                asyncio.run(_invoke(session, True))

        audit.assert_not_called()


class ConfirmResultBuilderTests(unittest.TestCase):
    def test_builder_rejects_mismatch(self) -> None:
        with self.assertRaises(ValueError):
            chat_service._build_confirm_results([True], _tool_calls(2))

    def test_builder_keeps_every_call(self) -> None:
        results = chat_service._build_confirm_results([True, False], _tool_calls(2))

        self.assertEqual(len(results), 2)
        self.assertEqual([result.confirmed for result in results], [True, False])


if __name__ == "__main__":
    unittest.main()
