"""Busy-session FIFO message queue: opt-in enqueue, worker order, cancel (4.5)."""

from __future__ import annotations

import asyncio
import json
import unittest
from collections.abc import AsyncIterator
from http import HTTPStatus
from types import SimpleNamespace
from typing import Any
from unittest.mock import patch

from agentscope.event import EventType

from yak_agent.api import server
from yak_agent.api.errors import _STATUS_BY_ERROR
from yak_agent.service import chat as chat_service
from yak_agent.service.errors import (
    QueueItemNotFound,
    QueueItemStarted,
    SessionBusy,
    SessionForbidden,
    SessionNotFound,
    SessionQueueFull,
)
from yak_agent.service.session import (
    QueuedMessage,
    Session,
    Turn,
    cancel_queued_message,
    enqueue_message,
)

_TERMINAL = ("done", "error", "queue_cancelled")


def _settings(**overrides: Any) -> SimpleNamespace:
    values: dict[str, Any] = {
        "session_queue_max": 5,
        "confirm_timeout_seconds": 5.0,
    }
    values.update(overrides)
    return SimpleNamespace(**values)


def _identity() -> SimpleNamespace:
    return SimpleNamespace(owner="u-1", workspace_id="1", auth=None, persistent=True)


def _session() -> Session:
    return Session(sid="sid", agent=object(), workspace=object(), owner="u-1", workspace_id="1")


def _message_text(message: Any) -> str:
    content = getattr(message, "content", "")
    if isinstance(content, str):
        return content
    return "".join(getattr(block, "text", "") for block in content)


class EmptyAgent:
    def __init__(self) -> None:
        self.executed: list[str] = []

    async def reply_stream(self, message: Any) -> AsyncIterator[Any]:
        self.executed.append(_message_text(message))
        return
        yield  # pragma: no cover - marks this method as an async generator


class FirstTurnHangsAgent:
    """First turn blocks forever until cancelled; later turns record text."""

    def __init__(self) -> None:
        self.executed: list[str] = []
        self.calls = 0

    async def reply_stream(self, message: Any) -> AsyncIterator[Any]:
        self.calls += 1
        if self.calls == 1:
            yield SimpleNamespace(
                type=EventType.TEXT_BLOCK_DELTA,
                delta="partial",
                reply_id="reply-1",
                block_id="block-1",
            )
            await asyncio.sleep(30)
        self.executed.append(_message_text(message))


async def _drain(stream: AsyncIterator[dict[str, str]], types: list[str]) -> None:
    while True:
        frame = await asyncio.wait_for(stream.__anext__(), 5)
        payload = json.loads(frame["data"])
        types.append(payload["type"])
        if payload["type"] in _TERMINAL:
            return


class SendRoutingTests(unittest.TestCase):
    """Without ?queue=true the behavior is exactly the pre-queue contract."""

    def test_busy_without_queue_stays_conflict(self) -> None:
        session = _session()
        session.busy = True

        async def scenario() -> None:
            with (
                patch.object(server, "_get_settings", return_value=_settings()),
                patch.object(server, "request_identity", return_value=_identity()),
                patch.object(server, "load_session", return_value=session),
            ):
                with self.assertRaises(SessionBusy):
                    await server.send_message("sid", server.MessageBody(message="第二条"), None)

        asyncio.run(scenario())

    def test_queue_disabled_means_conflict_even_with_queue_param(self) -> None:
        session = _session()
        session.busy = True

        async def scenario() -> None:
            with (
                patch.object(server, "_get_settings", return_value=_settings(session_queue_max=0)),
                patch.object(server, "request_identity", return_value=_identity()),
                patch.object(server, "load_session", return_value=session),
            ):
                with self.assertRaises(SessionBusy):
                    await server.send_message(
                        "sid", server.MessageBody(message="第二条"), None, True
                    )

        asyncio.run(scenario())

    def test_nonempty_queue_blocks_direct_send(self) -> None:
        session = _session()
        enqueue_message(session, "已在排队", 5)

        async def scenario() -> None:
            with (
                patch.object(server, "_get_settings", return_value=_settings()),
                patch.object(server, "request_identity", return_value=_identity()),
                patch.object(server, "load_session", return_value=session),
            ):
                with self.assertRaises(SessionBusy):
                    await server.send_message("sid", server.MessageBody(message="插队消息"), None)

        asyncio.run(scenario())

    def test_queue_full_rejects_with_429_error(self) -> None:
        session = _session()
        session.busy = True
        session.idle_event.clear()  # manual busy must mirror _begin_turn's claim

        async def scenario() -> None:
            with (
                patch.object(server, "_get_settings", return_value=_settings(session_queue_max=1)),
                patch.object(server, "request_identity", return_value=_identity()),
                patch.object(server, "load_session", return_value=session),
                patch.object(chat_service.persistence, "persist"),
                patch.object(server.audit, "record_tool_call"),
            ):
                await server.send_message("sid", server.MessageBody(message="第二条"), None, True)
                with self.assertRaises(SessionQueueFull):
                    await server.send_message(
                        "sid", server.MessageBody(message="第三条"), None, True
                    )
                session.queue_worker.cancel()
                await asyncio.gather(session.queue_worker, return_exceptions=True)

        asyncio.run(scenario())


class QueueFlowTests(unittest.TestCase):
    def test_busy_message_queues_and_runs_after_current_turn(self) -> None:
        agent = FirstTurnHangsAgent()

        async def scenario() -> tuple[list[str], list[str], Session]:
            session = _session()
            session.agent = agent
            settings = _settings()
            types: list[str] = []
            with (
                patch.object(server, "_get_settings", return_value=settings),
                patch.object(server, "request_identity", return_value=_identity()),
                patch.object(server, "load_session", return_value=session),
                patch.object(chat_service.persistence, "persist"),
                patch.object(server.audit, "record_tool_call"),
            ):
                first_turn, _cursor = await server._begin_turn(session, "第一条", settings)
                response = await server.send_message(
                    "sid", server.MessageBody(message="第二条"), None, True
                )
                first = json.loads((await response.body_iterator.__anext__())["data"])
                self.assertEqual(first["type"], "queue_waiting")
                self.assertEqual(first["position"], 1)
                self.assertEqual(first["messagePreview"], "第二条")
                self.assertEqual(len(session.queue), 1)

                # End the hanging first turn the way an interrupt would.
                first_turn.task.cancel()
                await asyncio.gather(first_turn.task, return_exceptions=True)
                await _drain(response.body_iterator, types)
                return types, agent.executed, session

        types, executed, session = asyncio.run(scenario())
        self.assertEqual(executed, ["第二条"])
        self.assertIn("queue_started", types)
        self.assertNotIn("replay_reset", types)  # subscribes at the turn cursor
        self.assertEqual(types[-1], "done")
        self.assertFalse(session.busy)
        self.assertIsNone(session.turn)
        self.assertEqual(len(session.queue), 0)
        self.assertTrue(session.idle_event.is_set())

    def test_two_queued_messages_run_in_fifo_order(self) -> None:
        agent = FirstTurnHangsAgent()

        async def scenario() -> tuple[list[str], list[str], list[str]]:
            session = _session()
            session.agent = agent
            settings = _settings()
            types_second: list[str] = []
            types_third: list[str] = []
            with (
                patch.object(server, "_get_settings", return_value=settings),
                patch.object(server, "request_identity", return_value=_identity()),
                patch.object(server, "load_session", return_value=session),
                patch.object(chat_service.persistence, "persist"),
                patch.object(server.audit, "record_tool_call"),
            ):
                first_turn, _cursor = await server._begin_turn(session, "第一条", settings)
                second = await server.send_message(
                    "sid", server.MessageBody(message="第二条"), None, True
                )
                third = await server.send_message(
                    "sid", server.MessageBody(message="第三条"), None, True
                )
                second_position = json.loads((await second.body_iterator.__anext__())["data"])
                third_position = json.loads((await third.body_iterator.__anext__())["data"])
                self.assertEqual(second_position["position"], 1)
                self.assertEqual(third_position["position"], 2)

                first_turn.task.cancel()
                await asyncio.gather(first_turn.task, return_exceptions=True)
                await asyncio.gather(
                    _drain(second.body_iterator, types_second),
                    _drain(third.body_iterator, types_third),
                )
                return types_second, types_third, agent.executed

        types_second, types_third, executed = asyncio.run(scenario())
        self.assertEqual(executed, ["第二条", "第三条"])
        self.assertEqual(types_second[-1], "done")
        self.assertEqual(types_third[-1], "done")
        self.assertIn("queue_started", types_second)
        self.assertIn("queue_started", types_third)

    def test_idle_message_joins_the_back_of_a_nonempty_queue(self) -> None:
        agent = EmptyAgent()

        async def scenario() -> tuple[int, list[str]]:
            session = _session()
            session.agent = agent
            settings = _settings()
            types: list[str] = []
            with (
                patch.object(server, "_get_settings", return_value=settings),
                patch.object(server, "request_identity", return_value=_identity()),
                patch.object(server, "load_session", return_value=session),
                patch.object(chat_service.persistence, "persist"),
                patch.object(server.audit, "record_tool_call"),
                # the manually enqueued head has no SSE consumer; skip its wait
                patch.object(server, "_QUEUE_ATTACH_TIMEOUT_SECONDS", 0.2),
            ):
                enqueue_message(session, "先来的一条", 5)
                response = await server.send_message(
                    "sid", server.MessageBody(message="后到的一条"), None, True
                )
                worker = session.queue_worker
                frame = json.loads((await response.body_iterator.__anext__())["data"])
                position = frame["position"]
                self.assertIsNotNone(worker)
                drain = asyncio.create_task(_drain(response.body_iterator, types))
                await asyncio.wait_for(worker, 5)
                await drain
                return position, agent.executed

        position, executed = asyncio.run(scenario())
        self.assertEqual(position, 2)  # never jumps ahead of the earlier item
        self.assertEqual(executed, ["先来的一条", "后到的一条"])

    def test_cancel_queued_message_prevents_execution(self) -> None:
        agent = FirstTurnHangsAgent()

        async def scenario() -> tuple[str, dict[str, bool]]:
            session = _session()
            session.agent = agent
            settings = _settings()
            with (
                patch.object(server, "_get_settings", return_value=settings),
                patch.object(server, "request_identity", return_value=_identity()),
                patch.object(server, "load_session", return_value=session),
                patch.object(server, "SESSIONS", {"sid": session}),
                patch.object(chat_service.persistence, "persist"),
                patch.object(server.audit, "record_tool_call"),
            ):
                first_turn, _cursor = await server._begin_turn(session, "第一条", settings)
                response = await server.send_message(
                    "sid", server.MessageBody(message="第二条"), None, True
                )
                worker = session.queue_worker
                waiting = json.loads((await response.body_iterator.__anext__())["data"])
                result = await server.cancel_queue_item("sid", waiting["queueId"], None)
                cancelled = json.loads((await response.body_iterator.__anext__())["data"])
                first_turn.task.cancel()
                await asyncio.gather(first_turn.task, return_exceptions=True)
                await asyncio.wait_for(worker, 2)
                return cancelled, result

        cancelled, result = asyncio.run(scenario())
        self.assertEqual(result, {"cancelled": True})
        self.assertEqual(cancelled["type"], "queue_cancelled")
        self.assertEqual(cancelled["reason"], "cancelled")
        self.assertEqual(agent.executed, [])  # cancelled item never ran

    def test_interrupted_turn_does_not_block_the_queue(self) -> None:
        """Interrupting the current turn lets the worker start the next item."""
        agent = FirstTurnHangsAgent()

        async def scenario() -> tuple[list[str], list[str]]:
            session = _session()
            session.agent = agent
            settings = _settings()
            types: list[str] = []
            with (
                patch.object(server, "_get_settings", return_value=settings),
                patch.object(server, "request_identity", return_value=_identity()),
                patch.object(server, "load_session", return_value=session),
                patch.object(chat_service.persistence, "persist"),
                patch.object(server.audit, "record_tool_call"),
            ):
                first_turn, _cursor = await server._begin_turn(session, "第一条", settings)
                response = await server.send_message(
                    "sid", server.MessageBody(message="第二条"), None, True
                )
                worker = session.queue_worker
                waiting = json.loads((await response.body_iterator.__anext__())["data"])
                self.assertEqual(waiting["type"], "queue_waiting")

                result = await server.interrupt("sid", server.InterruptBody(), None)
                self.assertEqual(result["accepted"], True)
                await asyncio.wait_for(first_turn.task, 2)
                await _drain(response.body_iterator, types)
                await asyncio.wait_for(worker, 2)
                return types, agent.executed

        types, executed = asyncio.run(scenario())
        self.assertEqual(executed, ["第二条"])  # queue continues after the interrupt
        self.assertIn("queue_started", types)
        self.assertEqual(types[-1], "done")

    def test_delete_session_cancels_queue_with_reason(self) -> None:
        session = _session()
        item = enqueue_message(session, "排队的一条", 5)

        async def scenario() -> None:
            with (
                patch.object(server, "_get_settings", return_value=_settings()),
                patch.object(server, "request_identity", return_value=_identity()),
                patch.object(server, "load_session", return_value=session),
                patch.object(chat_service.persistence, "persist"),
                patch.object(chat_service.persistence, "delete_files"),
                patch.object(server.audit, "record_tool_call"),
            ):
                result = await server.delete_session("sid", None)

            self.assertEqual(result, {"deleted": True})
            self.assertEqual(len(session.queue), 0)
            self.assertTrue(item.cancelled.is_set())
            self.assertEqual(item.cancel_reason, "session_deleted")

        asyncio.run(scenario())


class QueueStreamEdgeTests(unittest.TestCase):
    def test_waiting_heartbeat_never_reports_position_zero(self) -> None:
        """A popped-but-not-started item is next: never send position 0."""
        session = _session()
        item = QueuedMessage(queue_id="q-1", message="排队中", created_at=0.0)

        async def scenario() -> tuple[dict[str, Any], dict[str, Any], dict[str, Any]]:
            with patch.object(server, "_QUEUE_WAIT_HEARTBEAT_SECONDS", 0.01):
                stream = server._queue_stream(session, item, 1)
                first = json.loads((await stream.__anext__())["data"])
                second = json.loads((await stream.__anext__())["data"])
                item.cancelled.set()
                third = json.loads((await stream.__anext__())["data"])
            return first, second, third

        first, second, third = asyncio.run(scenario())
        self.assertEqual(first["position"], 1)
        self.assertEqual(second["type"], "queue_waiting")
        self.assertEqual(second["position"], 1)  # not in deque, but it is next
        self.assertEqual(third["type"], "queue_cancelled")

    def test_stream_ignores_events_from_a_later_turn(self) -> None:
        """A reset for the next turn must never leak into this message's SSE."""
        session = _session()
        item = QueuedMessage(queue_id="q-1", message="排队中", created_at=0.0)
        item.turn_id = "turn-1"
        item.started.set()
        session.event_log.events.append(
            {"seq": 1, "at": "2026-11-22T00:00:00", "type": "text_delta", "turnId": "turn-2"}
        )
        session.event_log.next_seq = 2

        async def scenario() -> dict[str, Any]:
            stream = server._queue_stream(session, item, 1)
            first = json.loads((await stream.__anext__())["data"])
            with self.assertRaises(StopAsyncIteration):
                await stream.__anext__()
            return first

        first = asyncio.run(scenario())
        self.assertEqual(first["type"], "queue_waiting")
        self.assertEqual(session.event_log.events[0]["turnId"], "turn-2")


class QueueErrorMappingTests(unittest.TestCase):
    def test_queue_errors_map_to_the_documented_http_status(self) -> None:
        self.assertEqual(_STATUS_BY_ERROR[SessionQueueFull], HTTPStatus.TOO_MANY_REQUESTS)
        self.assertEqual(_STATUS_BY_ERROR[QueueItemNotFound], HTTPStatus.NOT_FOUND)
        self.assertEqual(_STATUS_BY_ERROR[QueueItemStarted], HTTPStatus.CONFLICT)


class CancelSemanticsTests(unittest.TestCase):
    def test_cancel_remove_started_and_missing(self) -> None:
        session = _session()
        item = enqueue_message(session, "待取消", 5)
        cancelled = cancel_queued_message(session, item.queue_id)
        self.assertEqual(cancelled.queue_id, item.queue_id)
        self.assertTrue(cancelled.cancelled.is_set())

        with self.assertRaises(QueueItemNotFound):
            cancel_queued_message(session, "missing")
        session.turn = Turn(turn_id="turn-1", started_at=0.0, queue_id="turn-1-queue")
        with self.assertRaises(QueueItemStarted):
            cancel_queued_message(session, "turn-1-queue")


class QueueEndpointsTests(unittest.TestCase):
    def test_get_queue_lists_positions_and_max(self) -> None:
        session = _session()
        enqueue_message(session, "第一条", 5)
        enqueue_message(session, "第二条", 5)

        async def scenario() -> dict[str, Any]:
            with (
                patch.object(server, "_get_settings", return_value=_settings()),
                patch.object(server, "request_identity", return_value=_identity()),
                patch.object(server, "SESSIONS", {"sid": session}),
            ):
                return await server.get_session_queue("sid", None)

        result = asyncio.run(scenario())
        self.assertEqual(result["max"], 5)
        self.assertEqual([item["position"] for item in result["items"]], [1, 2])
        self.assertEqual(result["items"][0]["messagePreview"], "第一条")

    def test_get_queue_requires_ownership_on_disk_sessions(self) -> None:
        async def scenario() -> None:
            with (
                patch.object(server, "_get_settings", return_value=_settings()),
                patch.object(server, "request_identity", return_value=_identity()),
                patch.object(server, "SESSIONS", {}),
                patch.object(server.persistence, "read_meta", return_value={}),
            ):
                with self.assertRaises(SessionNotFound):
                    await server.get_session_queue("sid", None)

        asyncio.run(scenario())

    def test_get_queue_returns_empty_for_restored_disk_session(self) -> None:
        """After a restart every session is disk-only: queue must be empty, not 500."""

        async def scenario() -> dict[str, Any]:
            with (
                patch.object(server, "_get_settings", return_value=_settings()),
                patch.object(server, "request_identity", return_value=_identity()),
                patch.object(server, "SESSIONS", {}),
                patch.object(
                    server.persistence,
                    "read_meta",
                    return_value={"owner": "u-1", "workspace": "1"},
                ),
            ):
                return await server.get_session_queue("sid", None)

        result = asyncio.run(scenario())
        self.assertEqual(result, {"items": [], "max": 5})

    def test_status_reports_queued_state_and_count(self) -> None:
        session = _session()
        enqueue_message(session, "排队中", 5)

        async def scenario() -> dict[str, Any]:
            with (
                patch.object(server, "_get_settings", return_value=_settings()),
                patch.object(server, "request_identity", return_value=_identity()),
                patch.object(server, "SESSIONS", {"sid": session}),
                patch.object(server.persistence, "read_meta", return_value={}),
                patch.object(server, "_updated_at", return_value=None),
            ):
                return await server.session_status("sid", None)

        status = asyncio.run(scenario())
        self.assertEqual(status["state"], "queued")
        self.assertEqual(status["queued"], 1)
        self.assertFalse(status["busy"])

    def test_status_reports_zero_queue_for_disk_sessions(self) -> None:
        async def scenario() -> dict[str, Any]:
            with (
                patch.object(server, "_get_settings", return_value=_settings()),
                patch.object(server, "request_identity", return_value=_identity()),
                patch.object(
                    server.persistence,
                    "read_meta",
                    return_value={"owner": "u-1", "workspace": "1"},
                ),
                patch.object(server, "_updated_at", return_value=None),
            ):
                return await server.session_status("sid", None)

        status = asyncio.run(scenario())
        self.assertEqual(status["state"], "idle")
        self.assertEqual(status["queued"], 0)

    def test_forbidden_owner_cannot_read_queue(self) -> None:
        session = Session(
            sid="sid", agent=object(), workspace=object(), owner="u-2", workspace_id="1"
        )

        async def scenario() -> None:
            with (
                patch.object(server, "_get_settings", return_value=_settings()),
                patch.object(server, "request_identity", return_value=_identity()),
                patch.object(server, "SESSIONS", {"sid": session}),
            ):
                with self.assertRaises(SessionForbidden):
                    await server.get_session_queue("sid", None)

        asyncio.run(scenario())

    def test_cancel_on_disk_session_fails_without_loading_it(self) -> None:
        """Cancel must not resurrect a disk-only session into memory."""

        async def scenario() -> None:
            with (
                patch.object(server, "_get_settings", return_value=_settings()),
                patch.object(server, "request_identity", return_value=_identity()),
                patch.object(server, "SESSIONS", {}),
                patch.object(
                    server.persistence,
                    "read_meta",
                    return_value={"owner": "u-1", "workspace": "1"},
                ),
                patch.object(server, "load_session") as load,
            ):
                with self.assertRaises(QueueItemNotFound):
                    await server.cancel_queue_item("sid", "q-1", None)

            load.assert_not_called()

        asyncio.run(scenario())

    def test_cancel_checks_meta_ownership_on_disk_sessions(self) -> None:
        async def scenario() -> None:
            with (
                patch.object(server, "_get_settings", return_value=_settings()),
                patch.object(server, "request_identity", return_value=_identity()),
                patch.object(server, "SESSIONS", {}),
                patch.object(
                    server.persistence,
                    "read_meta",
                    return_value={"owner": "u-2", "workspace": "1"},
                ),
            ):
                with self.assertRaises(SessionForbidden):
                    await server.cancel_queue_item("sid", "q-1", None)

        asyncio.run(scenario())


if __name__ == "__main__":
    unittest.main()
