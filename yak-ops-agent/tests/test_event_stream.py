"""Event log replay and stream decoupling (session lifecycle 4.1)."""

from __future__ import annotations

import asyncio
import json
import unittest
from types import SimpleNamespace
from typing import Any
from unittest.mock import patch

from agentscope.event import EventType

from yak_agent.api import server
from yak_agent.service import chat as chat_service
from yak_agent.service.events import EventLog, emit, subscribe
from yak_agent.service.session import Session, Turn


def _session(turn_id: str | None = "turn-1") -> Session:
    return Session(
        sid="sid",
        agent=object(),
        workspace=object(),
        owner="u-1",
        workspace_id="1",
        turn=Turn(turn_id=turn_id, started_at=0.0) if turn_id else None,
    )


class EventLogTests(unittest.TestCase):
    def test_emit_stamps_sequence_and_turn(self) -> None:
        session = _session()

        async def scenario() -> list[dict[str, Any]]:
            await emit(session, {"type": "progress", "phase": "analysis"})
            await emit(session, {"type": "done"})
            return [event async for event in subscribe(session, 0)]

        events = asyncio.run(scenario())

        self.assertEqual([event["seq"] for event in events], [1, 2])
        self.assertEqual(events[0]["turnId"], "turn-1")
        self.assertEqual(events[-1]["type"], "done")

    def test_replay_starts_after_the_cursor(self) -> None:
        session = _session()

        async def scenario() -> list[dict[str, Any]]:
            await emit(session, {"type": "progress"})
            await emit(session, {"type": "text_delta", "delta": "a"})
            await emit(session, {"type": "done"})
            return [event async for event in subscribe(session, 1)]

        events = asyncio.run(scenario())

        self.assertEqual([event["seq"] for event in events], [2, 3])

    def test_consumed_terminal_yields_stream_idle(self) -> None:
        session = _session()

        async def scenario() -> list[dict[str, Any]]:
            await emit(session, {"type": "done"})
            return [event async for event in subscribe(session, 1)]

        events = asyncio.run(scenario())

        self.assertEqual(events[0]["type"], "stream_idle")

    def test_future_cursor_is_clamped(self) -> None:
        session = _session()

        async def scenario() -> list[dict[str, Any]]:
            await emit(session, {"type": "done"})
            return [event async for event in subscribe(session, 99)]

        events = asyncio.run(scenario())

        self.assertEqual(events[0]["type"], "stream_idle")

    def test_expired_cursor_emits_replay_reset(self) -> None:
        session = _session()
        session.event_log = EventLog(capacity=2)

        async def scenario() -> list[dict[str, Any]]:
            await emit(session, {"type": "progress"})
            await emit(session, {"type": "text_delta", "delta": "b"})
            await emit(session, {"type": "done"})
            return [event async for event in subscribe(session, 0)]

        events = asyncio.run(scenario())

        self.assertEqual(events[0]["type"], "replay_reset")
        self.assertEqual([event["seq"] for event in events[1:]], [2, 3])

    def test_reset_clears_events_but_keeps_sequence(self) -> None:
        session = _session()

        async def scenario() -> int:
            await emit(session, {"type": "done"})
            await session.event_log.reset()
            await emit(session, {"type": "progress"})
            return session.event_log.events[-1]["seq"]

        self.assertEqual(asyncio.run(scenario()), 2)
        self.assertEqual(len(session.event_log.events), 1)


class SlowAgent:
    def __init__(self) -> None:
        self.calls = 0

    async def reply_stream(self, message: Any):
        self.calls += 1
        yield SimpleNamespace(
            type=EventType.TEXT_BLOCK_DELTA,
            delta="partial",
            reply_id="reply-1",
            block_id="block-1",
        )
        await asyncio.sleep(0.2)
        yield SimpleNamespace(
            type=EventType.REPLY_END,
            finished_reason=SimpleNamespace(value="completed"),
            error=None,
            reply_id="reply-1",
        )


class FastAgent:
    async def reply_stream(self, message: Any):
        yield SimpleNamespace(
            type=EventType.TEXT_BLOCK_DELTA,
            delta="ok",
            reply_id="reply-1",
            block_id="block-1",
        )
        yield SimpleNamespace(
            type=EventType.REPLY_END,
            finished_reason=SimpleNamespace(value="completed"),
            error=None,
            reply_id="reply-1",
        )


class StreamDecouplingTests(unittest.TestCase):
    def _settings(self) -> SimpleNamespace:
        return SimpleNamespace(confirm_timeout_seconds=5)

    def _identity(self) -> SimpleNamespace:
        return SimpleNamespace(owner="u-1", workspace_id="1", auth=None, persistent=True)

    def test_disconnect_does_not_cancel_the_turn(self) -> None:
        session = _session()
        session.agent = SlowAgent()

        async def scenario() -> list[dict[str, Any]]:
            with (
                patch.object(server, "_get_settings", return_value=self._settings()),
                patch.object(server, "request_identity", return_value=self._identity()),
                patch.object(server, "load_session", return_value=session),
                patch.object(chat_service.persistence, "persist"),
            ):
                response = await server.send_message(
                    "sid",
                    server.MessageBody(message="hello"),
                    None,
                )
                iterator = response.body_iterator
                first = await iterator.__anext__()
                await iterator.aclose()
                await asyncio.sleep(0.4)
                return [first]

        asyncio.run(scenario())

        self.assertFalse(session.busy)
        self.assertIsNone(session.turn)
        self.assertEqual(session.agent.calls, 1)
        self.assertEqual(session.event_log.events[-1]["type"], "done")

    def test_resume_replays_without_reexecuting_the_agent(self) -> None:
        session = _session()

        async def scenario() -> list[dict[str, Any]]:
            with (
                patch.object(server, "_get_settings", return_value=self._settings()),
                patch.object(server, "request_identity", return_value=self._identity()),
                patch.object(server, "load_session", return_value=session),
                patch.object(chat_service.persistence, "persist"),
            ):
                await emit(session, {"type": "progress", "phase": "analysis"})
                await emit(session, {"type": "text_delta", "delta": "a"})
                await emit(session, {"type": "done"})
                response = await server.resume_stream(
                    "sid",
                    None,
                    1,
                )
                return [item async for item in response.body_iterator]

        items = asyncio.run(scenario())

        self.assertEqual([item["event"] for item in items], ["text_delta", "done"])
        self.assertEqual([item["id"] for item in items], ["2", "3"])
        self.assertIn("turnId", json.loads(items[0]["data"]))

    def test_second_turn_does_not_emit_replay_reset(self) -> None:
        session = _session()
        session.agent = FastAgent()

        async def scenario() -> list[list[str]]:
            with (
                patch.object(server, "_get_settings", return_value=self._settings()),
                patch.object(server, "request_identity", return_value=self._identity()),
                patch.object(server, "load_session", return_value=session),
                patch.object(chat_service.persistence, "persist"),
            ):
                outcomes = []
                for _ in range(2):
                    response = await server.send_message(
                        "sid",
                        server.MessageBody(message="hello"),
                        None,
                    )
                    items = [item async for item in response.body_iterator]
                    outcomes.append([item["event"] for item in items])
                return outcomes

        first, second = asyncio.run(scenario())

        self.assertNotIn("replay_reset", first)
        self.assertNotIn("replay_reset", second)
        self.assertEqual(second[0], "progress")


if __name__ == "__main__":
    unittest.main()
