"""Interrupt endpoint: parked confirmation and running turn (4.2)."""

from __future__ import annotations

import asyncio
import unittest
from types import SimpleNamespace
from typing import Any
from unittest.mock import patch

from agentscope.event import EventType, RequireUserConfirmEvent, UserInterruptEvent
from agentscope.message import ToolCallBlock

from yak_agent.api import server
from yak_agent.service import chat as chat_service
from yak_agent.service.session import Session, Turn


def _tool_calls(count: int = 1) -> list[ToolCallBlock]:
    return [ToolCallBlock(id=f"t{i}", name=f"tool{i}", input="{}") for i in range(count)]


def _session() -> Session:
    session = Session(sid="sid", agent=object(), workspace=object(), owner="u-1", workspace_id="1")
    session.busy = True
    return session


def _identity() -> SimpleNamespace:
    return SimpleNamespace(owner="u-1", workspace_id="1", auth=None, persistent=True)


async def _noop_forward(session: Session, event: Any) -> None:
    return None


async def _wait_for(predicate: Any, attempts: int = 200) -> None:
    for _ in range(attempts):
        if predicate():
            return
        await asyncio.sleep(0.005)
    raise AssertionError("condition was not reached")


class ParkedAgent:
    def __init__(self, paused: RequireUserConfirmEvent) -> None:
        self.paused = paused
        self.messages: list[Any] = []

    async def reply_stream(self, message: Any):
        self.messages.append(message)
        if len(self.messages) == 1:
            yield self.paused


class RunningAgent:
    async def reply_stream(self, message: Any):
        yield SimpleNamespace(
            type=EventType.TEXT_BLOCK_DELTA,
            delta="partial",
            reply_id="reply-1",
            block_id="block-1",
        )
        await asyncio.sleep(30)


class InterruptTests(unittest.TestCase):
    def test_parked_confirmation_is_interrupted_without_executing_tools(self) -> None:
        paused = RequireUserConfirmEvent(reply_id="reply-1", tool_calls=_tool_calls(2))
        session = _session()
        session.agent = ParkedAgent(paused)
        session.pending_event = paused

        async def scenario() -> list[Any]:
            with (
                patch.object(server, "_get_settings", return_value=SimpleNamespace()),
                patch.object(server, "request_identity", return_value=_identity()),
                patch.object(server, "load_session", return_value=session),
                patch.object(chat_service.persistence, "persist"),
                patch.object(server.audit, "record_tool_call") as audit,
            ):
                turn = Turn(turn_id="turn-1", started_at=0.0)
                task = asyncio.create_task(
                    chat_service.run_turn(session, "停止实例", _noop_forward, 5, turn)
                )
                turn.task = task
                await _wait_for(lambda: session.confirm_future is not None)
                result = await server.interrupt("sid", server.InterruptBody(), None)
                await asyncio.wait_for(task, 2)
                return [result, audit.call_args_list]

        result, audit_calls = asyncio.run(scenario())

        self.assertTrue(result["accepted"])
        self.assertEqual(result["state"], "interrupting")
        self.assertIsInstance(session.agent.messages[1], UserInterruptEvent)
        self.assertFalse(session.busy)
        self.assertIsNone(session.pending_event)
        self.assertEqual(session.event_log.events[-1]["type"], "done")
        names = [call.args[0] for call in audit_calls]
        self.assertIn("session_interrupt", names)
        self.assertEqual(names.count("session_confirm_interrupted"), 2)

    def test_running_turn_is_cancelled_and_finishes_with_done(self) -> None:
        session = _session()
        session.agent = RunningAgent()

        async def scenario() -> dict[str, Any]:
            with (
                patch.object(server, "_get_settings", return_value=SimpleNamespace()),
                patch.object(server, "request_identity", return_value=_identity()),
                patch.object(server, "load_session", return_value=session),
                patch.object(chat_service.persistence, "persist"),
                patch.object(server.audit, "record_tool_call"),
            ):
                turn = Turn(turn_id="turn-1", started_at=0.0)
                task = asyncio.create_task(
                    chat_service.run_turn(session, "长任务", _noop_forward, 5, turn)
                )
                turn.task = task
                await _wait_for(lambda: bool(session.event_log.events))
                result = await server.interrupt("sid", server.InterruptBody(), None)
                await asyncio.wait_for(task, 2)
                return result

        result = asyncio.run(scenario())

        self.assertTrue(result["accepted"])
        self.assertFalse(session.busy)
        self.assertIsNone(session.turn)
        self.assertEqual(session.event_log.events[-1]["type"], "done")
        self.assertEqual(session.event_log.events[-1]["finishedReason"], "interrupted")

    def test_stale_turn_id_does_not_stop_the_current_turn(self) -> None:
        session = _session()
        session.agent = RunningAgent()

        async def scenario() -> tuple[dict[str, Any], dict[str, Any]]:
            with (
                patch.object(server, "_get_settings", return_value=SimpleNamespace()),
                patch.object(server, "request_identity", return_value=_identity()),
                patch.object(server, "load_session", return_value=session),
                patch.object(chat_service.persistence, "persist"),
                patch.object(server.audit, "record_tool_call"),
            ):
                turn = Turn(turn_id="turn-1", started_at=0.0)
                task = asyncio.create_task(
                    chat_service.run_turn(session, "长任务", _noop_forward, 5, turn)
                )
                turn.task = task
                await _wait_for(lambda: bool(session.event_log.events))
                stale = await server.interrupt(
                    "sid",
                    server.InterruptBody(turnId="older-turn"),
                    None,
                )
                current = await server.interrupt("sid", server.InterruptBody(turnId="turn-1"), None)
                await asyncio.wait_for(task, 2)
                return stale, current

        stale, current = asyncio.run(scenario())

        self.assertFalse(stale["accepted"])
        self.assertTrue(current["accepted"])

    def test_idle_session_reports_not_accepted(self) -> None:
        session = _session()
        session.busy = False

        with (
            patch.object(server, "_get_settings", return_value=SimpleNamespace()),
            patch.object(server, "request_identity", return_value=_identity()),
            patch.object(server, "load_session", return_value=session),
        ):
            result = asyncio.run(server.interrupt("sid", server.InterruptBody(), None))

        self.assertFalse(result["accepted"])
        self.assertEqual(result["state"], "idle")

    def test_duplicate_interrupt_is_idempotent(self) -> None:
        session = _session()
        session.agent = RunningAgent()

        async def scenario() -> tuple[dict[str, Any], dict[str, Any]]:
            with (
                patch.object(server, "_get_settings", return_value=SimpleNamespace()),
                patch.object(server, "request_identity", return_value=_identity()),
                patch.object(server, "load_session", return_value=session),
                patch.object(chat_service.persistence, "persist"),
                patch.object(server.audit, "record_tool_call"),
            ):
                turn = Turn(turn_id="turn-1", started_at=0.0)
                task = asyncio.create_task(
                    chat_service.run_turn(session, "长任务", _noop_forward, 5, turn)
                )
                turn.task = task
                await _wait_for(lambda: bool(session.event_log.events))
                first = await server.interrupt("sid", server.InterruptBody(), None)
                second = await server.interrupt("sid", server.InterruptBody(), None)
                await asyncio.wait_for(task, 2)
                return first, second

        first, second = asyncio.run(scenario())

        self.assertTrue(first["accepted"])
        self.assertTrue(second["accepted"])

    def test_interrupt_audit_records_turn_scope(self) -> None:
        session = _session()
        session.agent = RunningAgent()

        async def scenario() -> list[Any]:
            with (
                patch.object(server, "_get_settings", return_value=SimpleNamespace()),
                patch.object(server, "request_identity", return_value=_identity()),
                patch.object(server, "load_session", return_value=session),
                patch.object(chat_service.persistence, "persist"),
                patch.object(server.audit, "record_tool_call") as audit,
            ):
                turn = Turn(turn_id="turn-1", started_at=0.0)
                task = asyncio.create_task(
                    chat_service.run_turn(session, "长任务", _noop_forward, 5, turn)
                )
                turn.task = task
                await _wait_for(lambda: bool(session.event_log.events))
                await server.interrupt("sid", server.InterruptBody(), None)
                await asyncio.wait_for(task, 2)
                return audit.call_args_list

        calls = asyncio.run(scenario())

        interrupt_calls = [call for call in calls if call.args[0] == "session_interrupt"]
        self.assertEqual(len(interrupt_calls), 1)
        self.assertEqual(interrupt_calls[0].args[1]["scope"], "running")
        self.assertEqual(interrupt_calls[0].args[1]["turnId"], "turn-1")


if __name__ == "__main__":
    unittest.main()
