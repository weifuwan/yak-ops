"""Message history serialization and endpoint (4.3)."""

from __future__ import annotations

import asyncio
import tempfile
import unittest
from pathlib import Path
from types import SimpleNamespace
from unittest.mock import patch

from agentscope.message import (
    Msg,
    TextBlock,
    ThinkingBlock,
    ToolCallBlock,
    ToolResultBlock,
    ToolResultState,
)
from agentscope.state import AgentState

from yak_agent.api import server
from yak_agent.service import persistence
from yak_agent.service.errors import SessionForbidden
from yak_agent.service.history import serialize_history
from yak_agent.service.session import SESSIONS, Session


def _state() -> AgentState:
    state = AgentState()
    state.context.extend(
        [
            Msg(
                name="user",
                role="user",
                content=[TextBlock(type="text", text="下线第一个任务")],
            ),
            Msg(
                name="yak_agent",
                role="assistant",
                content=[
                    ThinkingBlock(
                        type="thinking",
                        thinking="internal",
                        id="thinking-1",
                        created_at="2026-11-20T09:00:01.000Z",
                        finished_at="2026-11-20T09:00:02.250Z",
                    ),
                    TextBlock(type="text", text="先查任务列表"),
                    ToolCallBlock(id="call-1", name="list_sync_tasks", input='{"page_no":1}'),
                    ToolResultBlock(
                        id="call-1",
                        name="list_sync_tasks",
                        output="x" * 50,
                        state=ToolResultState.SUCCESS,
                    ),
                ],
            ),
            Msg(
                name="system",
                role="system",
                content=[TextBlock(type="text", text="should be dropped")],
            ),
        ]
    )
    return state


class HistorySerializerTests(unittest.TestCase):
    def test_keeps_text_and_tool_blocks_and_drops_internal_blocks(self) -> None:
        result = serialize_history(_state(), limit=10, tool_chars=10)

        self.assertEqual(len(result["messages"]), 2)
        user, assistant = result["messages"]
        self.assertEqual(user["role"], "user")
        self.assertEqual(user["blocks"], [{"type": "text", "text": "下线第一个任务"}])
        types = [block["type"] for block in assistant["blocks"]]
        self.assertEqual(types, ["thinking", "text", "tool_call", "tool_result"])
        self.assertEqual(
            assistant["blocks"][0],
            {
                "type": "thinking",
                "id": "thinking-1",
                "startedAt": "2026-11-20T09:00:01.000Z",
                "completedAt": "2026-11-20T09:00:02.250Z",
            },
        )
        self.assertNotIn("thinking", assistant["blocks"][0])
        self.assertEqual(assistant["blocks"][2]["input"], '{"page_no":1}')
        self.assertEqual(assistant["blocks"][3]["state"], "success")
        self.assertEqual(assistant["blocks"][3]["output"], "x" * 10)
        self.assertTrue(assistant["blocks"][3]["truncated"])

    def test_incomplete_thinking_block_exposes_start_without_reasoning_or_end_time(self) -> None:
        state = AgentState()
        state.context.append(
            Msg(
                name="yak_agent",
                role="assistant",
                content=[
                    ThinkingBlock(
                        type="thinking",
                        thinking="must never be returned",
                        id="thinking-open",
                        created_at="2026-11-20T09:00:01.000Z",
                    )
                ],
            )
        )

        result = serialize_history(state, limit=10, tool_chars=100)

        block = result["messages"][0]["blocks"][0]
        self.assertEqual(
            block,
            {
                "type": "thinking",
                "id": "thinking-open",
                "startedAt": "2026-11-20T09:00:01.000Z",
            },
        )
        self.assertNotIn("must never be returned", str(result))

    def test_limit_drops_oldest_messages(self) -> None:
        result = serialize_history(_state(), limit=1, tool_chars=10)

        self.assertTrue(result["truncated"])
        self.assertEqual(len(result["messages"]), 1)
        self.assertEqual(result["messages"][0]["role"], "assistant")

    def test_tool_output_reads_text_block_objects(self) -> None:
        state = AgentState()
        state.context.append(
            Msg(
                name="yak_agent",
                role="assistant",
                content=[
                    ToolResultBlock(
                        id="call-1",
                        name="download_task_log",
                        output=[TextBlock(type="text", text="Caused by: boom")],
                        state=ToolResultState.SUCCESS,
                    )
                ],
            )
        )

        result = serialize_history(state, limit=10, tool_chars=100)

        block = result["messages"][0]["blocks"][0]
        self.assertEqual(block["output"], "Caused by: boom")
        self.assertFalse(block["truncated"])


class HistoryEndpointTests(unittest.TestCase):
    def setUp(self) -> None:
        self._sessions = dict(SESSIONS)
        SESSIONS.clear()
        self._session_dir = persistence.SESSION_DIR
        self._tmp = tempfile.TemporaryDirectory()
        persistence.SESSION_DIR = Path(self._tmp.name)
        self._settings = SimpleNamespace(history_max_messages=10, history_tool_chars=5)

    def tearDown(self) -> None:
        SESSIONS.clear()
        SESSIONS.update(self._sessions)
        persistence.SESSION_DIR = self._session_dir
        self._tmp.cleanup()

    def _messages(self, sid: str, limit: int = 0):
        with (
            patch.object(server, "_get_settings", return_value=self._settings),
            patch.object(
                server,
                "request_identity",
                return_value=SimpleNamespace(
                    owner="u-1", workspace_id="1", auth=None, persistent=True
                ),
            ),
        ):
            return asyncio.run(server.session_messages(sid, None, limit))

    def test_in_memory_session_uses_live_state(self) -> None:
        session = Session(
            sid="sid",
            agent=SimpleNamespace(state=_state()),
            workspace=object(),
            owner="u-1",
            workspace_id="1",
        )
        SESSIONS["sid"] = session

        result = self._messages("sid")

        self.assertEqual(result["sessionId"], "sid")
        self.assertEqual(len(result["messages"]), 2)

    def test_disk_session_is_read_without_building_an_agent(self) -> None:
        persistence.state_path("sid").write_text(_state().model_dump_json(), encoding="utf-8")
        persistence.meta_path("sid").write_text(
            '{"owner": "u-1", "workspace": "1"}',
            encoding="utf-8",
        )

        result = self._messages("sid")

        self.assertEqual(len(result["messages"]), 2)
        self.assertEqual(result["messages"][0]["role"], "user")

    def test_cross_owner_disk_session_is_rejected(self) -> None:
        persistence.state_path("sid").write_text(_state().model_dump_json(), encoding="utf-8")
        persistence.meta_path("sid").write_text(
            '{"owner": "u-2", "workspace": "1"}',
            encoding="utf-8",
        )

        with self.assertRaises(SessionForbidden):
            self._messages("sid")


if __name__ == "__main__":
    unittest.main()
