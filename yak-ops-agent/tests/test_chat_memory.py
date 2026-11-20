"""Turn-boundary memory retrieval and write-back, including HITL resumes."""

from __future__ import annotations

import asyncio
import unittest
from types import SimpleNamespace
from unittest.mock import patch

from agentscope.event import EventType, RequireUserConfirmEvent, UserConfirmResultEvent
from agentscope.message import AssistantMsg, HintBlock, ToolCallBlock, UserMsg
from agentscope.state import AgentState

from yak_agent.memory.sanitize import sanitize_turn
from yak_agent.service import chat as chat_service
from yak_agent.service.events import EventLog
from yak_agent.service.session import Session, Turn


class FakeMemory:
    def __init__(self, hint: str | None = "历史记忆") -> None:
        self.hint = hint
        self.queries: list[str] = []
        self.writes: list[tuple[str, list]] = []
        self.began = 0
        self.ended = 0

    def begin_turn(self) -> None:
        self.began += 1

    def end_turn(self) -> None:
        self.ended += 1

    async def retrieve(self, query: str) -> str | None:
        self.queries.append(query)
        return self.hint

    def enqueue_turn(self, session_id: str, messages: list) -> bool:
        self.writes.append((session_id, sanitize_turn(messages)))
        return True


class FakeAgent:
    def __init__(self, *, hitl: bool = False, fail: bool = False) -> None:
        self.state = AgentState(session_id="state-sid")
        self.hitl = hitl
        self.fail = fail
        self.calls = 0

    async def reply_stream(self, msg):
        self.calls += 1
        if self.fail:
            raise RuntimeError("model failed")
        if isinstance(msg, UserConfirmResultEvent):
            self.state.context.append(AssistantMsg(name="assistant", content="最终回答"))
            yield SimpleNamespace(type=EventType.REPLY_END, reply_id="r1")
            return
        self.state.context.append(UserMsg(name="user", content=msg.get_text_content()))
        if self.hitl and self.calls == 1:
            self.state.context.append(AssistantMsg(name="assistant", content="需要确认"))
            yield RequireUserConfirmEvent(
                reply_id="r1",
                tool_calls=[ToolCallBlock(id="t1", name="write", input="{}")],
            )
            return
        self.state.context.append(AssistantMsg(name="assistant", content="最终回答"))
        yield SimpleNamespace(type=EventType.REPLY_END, reply_id="r1")


def _session(memory: FakeMemory | None, agent: FakeAgent) -> Session:
    return Session(
        sid="sid",
        agent=agent,
        workspace=object(),
        owner="u-1-10",
        workspace_id="1",
        memory=memory,
        persistent=False,
        event_log=EventLog(),
        turn=Turn(turn_id="turn-1", started_at=0.0),
    )


async def _forward(_session: Session, _event) -> None:
    return None


class TurnMemoryTests(unittest.TestCase):
    def test_retrieves_once_and_injects_hint_before_the_turn(self) -> None:
        memory = FakeMemory()
        agent = FakeAgent()
        session = _session(memory, agent)
        old_hint = AssistantMsg(name="memory", content=[HintBlock(hint="old")])
        agent.state.context.append(old_hint)

        asyncio.run(chat_service.run_turn(session, "我只看 FAILURE", _forward))

        self.assertEqual(memory.queries, ["我只看 FAILURE"])
        hints = [m for m in agent.state.context if getattr(m, "name", None) == "memory"]
        self.assertEqual(len(hints), 1)
        self.assertIsNot(hints[0], old_hint)
        self.assertEqual(memory.writes[0][0], "state-sid")
        written_text = [m.get_text_content() for m in memory.writes[0][1]]
        self.assertEqual(written_text, ["我只看 FAILURE", "最终回答"])
        self.assertEqual(memory.began, 1)
        self.assertEqual(memory.ended, 1)

    def test_hitl_turn_retrieves_once_and_writes_the_final_answer(self) -> None:
        memory = FakeMemory()
        agent = FakeAgent(hitl=True)
        session = _session(memory, agent)

        async def confirm(_session, _paused, _timeout):
            return [True]

        with patch.object(chat_service, "_await_confirmation", new=confirm):
            asyncio.run(chat_service.run_turn(session, "发布任务", _forward))

        self.assertEqual(agent.calls, 2)
        self.assertEqual(memory.queries, ["发布任务"])
        self.assertEqual(len(memory.writes), 1)
        written_text = [m.get_text_content() for m in memory.writes[0][1]]
        self.assertEqual(written_text, ["发布任务", "最终回答"])

    def test_failed_turn_does_not_write(self) -> None:
        memory = FakeMemory()
        agent = FakeAgent(fail=True)
        session = _session(memory, agent)

        asyncio.run(chat_service.run_turn(session, "hello", _forward))

        self.assertEqual(memory.writes, [])
        self.assertEqual(memory.began, 1)
        self.assertEqual(memory.ended, 1)

    def test_no_memory_does_not_retrieve(self) -> None:
        agent = FakeAgent()
        session = _session(None, agent)

        asyncio.run(chat_service.run_turn(session, "hello", _forward))

        self.assertEqual(
            [m for m in agent.state.context if getattr(m, "name", None) == "memory"], []
        )

    def test_interrupted_turn_does_not_write(self) -> None:
        memory = FakeMemory()
        agent = FakeAgent()
        session = _session(memory, agent)
        session.turn = Turn(turn_id="turn-1", started_at=0.0, interrupt_requested=True)

        async def cancelled(_msg):
            raise asyncio.CancelledError
            yield  # pragma: no cover

        agent.reply_stream = cancelled

        asyncio.run(chat_service.run_turn(session, "hello", _forward, turn=session.turn))

        self.assertEqual(memory.writes, [])
        self.assertEqual(memory.ended, 1)


if __name__ == "__main__":
    unittest.main()
