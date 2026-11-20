"""Session status endpoint (4.4)."""

from __future__ import annotations

import asyncio
import json
import tempfile
import unittest
from pathlib import Path
from types import SimpleNamespace
from unittest.mock import patch

from agentscope.message import ToolCallBlock

from yak_agent.api import server
from yak_agent.service import persistence
from yak_agent.service.errors import SessionForbidden, SessionNotFound
from yak_agent.service.session import SESSIONS, Session, Turn


def _identity(owner: str = "u-1", workspace: str = "1") -> SimpleNamespace:
    return SimpleNamespace(owner=owner, workspace_id=workspace, auth=None, persistent=True)


class SessionStatusTests(unittest.TestCase):
    def setUp(self) -> None:
        self._sessions = dict(SESSIONS)
        SESSIONS.clear()
        self._session_dir = persistence.SESSION_DIR
        self._tmp = tempfile.TemporaryDirectory()
        persistence.SESSION_DIR = Path(self._tmp.name)

    def tearDown(self) -> None:
        SESSIONS.clear()
        SESSIONS.update(self._sessions)
        persistence.SESSION_DIR = self._session_dir
        self._tmp.cleanup()

    def _status(self, sid: str, identity: SimpleNamespace | None = None):
        with (
            patch.object(server, "_get_settings", return_value=object()),
            patch.object(server, "request_identity", return_value=identity or _identity()),
        ):
            return asyncio.run(server.session_status(sid, None))

    def test_running_turn_reports_turn_and_reply(self) -> None:
        session = Session(
            sid="sid", agent=object(), workspace=object(), owner="u-1", workspace_id="1"
        )
        session.busy = True
        session.turn = Turn(turn_id="turn-1", started_at=1763000000.0)
        SESSIONS["sid"] = session

        result = self._status("sid")

        self.assertEqual(result["state"], "running")
        self.assertTrue(result["busy"])
        self.assertEqual(result["turnId"], "turn-1")
        self.assertIsNone(result["pendingConfirmation"])
        self.assertFalse(result["stale"])

    def test_awaiting_confirmation_includes_pending_calls(self) -> None:
        session = Session(
            sid="sid", agent=object(), workspace=object(), owner="u-1", workspace_id="1"
        )
        session.busy = True
        session.turn = Turn(
            turn_id="turn-1",
            started_at=1763000000.0,
            status="awaiting_confirmation",
            reply_id="reply-1",
        )
        session.pending_event = SimpleNamespace(
            reply_id="reply-1",
            tool_calls=[ToolCallBlock(id="t1", name="delete_sync_task", input='{"task_code":1}')],
        )
        SESSIONS["sid"] = session

        result = self._status("sid")

        self.assertEqual(result["state"], "awaiting_confirmation")
        self.assertEqual(result["replyId"], "reply-1")
        self.assertEqual(result["pendingConfirmation"]["toolCalls"][0]["name"], "delete_sync_task")

    def test_disk_session_with_active_turn_is_stale(self) -> None:
        persistence.meta_path("sid").write_text(
            json.dumps(
                {
                    "owner": "u-1",
                    "workspace": "1",
                    "title": "历史标题",
                    "preview": "历史预览",
                    "activeTurn": {"turnId": "turn-9", "startedAt": 1763000000.0},
                },
                ensure_ascii=False,
            ),
            encoding="utf-8",
        )

        result = self._status("sid")

        self.assertEqual(result["state"], "stale")
        self.assertTrue(result["stale"])
        self.assertFalse(result["busy"])
        self.assertEqual(result["turnId"], "turn-9")
        self.assertEqual(result["title"], "历史标题")

    def test_disk_session_without_active_turn_is_idle(self) -> None:
        persistence.meta_path("sid").write_text(
            json.dumps({"owner": "u-1", "workspace": "1"}),
            encoding="utf-8",
        )

        result = self._status("sid")

        self.assertEqual(result["state"], "idle")
        self.assertFalse(result["stale"])

    def test_new_in_memory_session_without_meta_is_reported(self) -> None:
        SESSIONS["sid"] = Session(
            sid="sid",
            agent=object(),
            workspace=object(),
            owner="u-1",
            workspace_id="1",
        )

        result = self._status("sid")

        self.assertEqual(result["state"], "idle")
        self.assertEqual(result["title"], "")

    def test_unknown_session_raises_not_found(self) -> None:
        with self.assertRaises(SessionNotFound):
            self._status("missing")

    def test_cross_owner_session_raises_forbidden(self) -> None:
        persistence.meta_path("sid").write_text(
            json.dumps({"owner": "u-2", "workspace": "1"}),
            encoding="utf-8",
        )

        with self.assertRaises(SessionForbidden):
            self._status("sid", _identity(owner="u-1"))


if __name__ == "__main__":
    unittest.main()
