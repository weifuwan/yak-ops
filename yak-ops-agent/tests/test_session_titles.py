"""Conversation title and preview (4.6)."""

from __future__ import annotations

import asyncio
import json
import tempfile
import unittest
from pathlib import Path
from types import SimpleNamespace
from unittest.mock import patch

from yak_agent.api import server
from yak_agent.service import persistence
from yak_agent.service.session import SESSIONS, Session


def _identity() -> SimpleNamespace:
    return SimpleNamespace(owner="u-1", workspace_id="1", auth=None, persistent=True)


async def _noop_turn(*args, **kwargs) -> None:
    return None


class TitleHelperTests(unittest.TestCase):
    def test_long_message_is_truncated_and_whitespace_collapsed(self) -> None:
        title, preview = server._conversation_title("  第一行\n第二行  " + "长" * 200)

        self.assertEqual(len(title), 40)
        self.assertTrue(title.startswith("第一行 第二行"))
        self.assertEqual(len(preview), 120)
        self.assertNotIn("\n", title)

    def test_blank_message_falls_back(self) -> None:
        self.assertEqual(server._conversation_title("   \n  "), ("新会话", ""))


class TitlePersistenceTests(unittest.TestCase):
    def setUp(self) -> None:
        self._sessions = dict(SESSIONS)
        SESSIONS.clear()
        self._session_dir = persistence.SESSION_DIR
        self._tmp = tempfile.TemporaryDirectory()
        persistence.SESSION_DIR = Path(self._tmp.name)
        self._settings = SimpleNamespace(confirm_timeout_seconds=5)

    def tearDown(self) -> None:
        SESSIONS.clear()
        SESSIONS.update(self._sessions)
        persistence.SESSION_DIR = self._session_dir
        self._tmp.cleanup()

    def _send(self, message: str, title: str = "") -> Session:
        session = Session(
            sid="sid",
            agent=SimpleNamespace(state=None),
            workspace=object(),
            owner="u-1",
            workspace_id="1",
            title=title,
        )
        SESSIONS["sid"] = session

        async def scenario() -> None:
            with (
                patch.object(server, "_get_settings", return_value=self._settings),
                patch.object(server, "request_identity", return_value=_identity()),
                patch.object(server, "load_session", return_value=session),
                patch.object(server, "run_turn", _noop_turn),
            ):
                await server.send_message("sid", server.MessageBody(message=message), None)
                await asyncio.sleep(0)

        asyncio.run(scenario())
        return session

    def test_first_message_writes_title_preview_and_active_turn(self) -> None:
        self._send("  帮我检查\n同步任务  ")

        meta = persistence.read_meta("sid")
        self.assertEqual(meta["title"], "帮我检查 同步任务")
        self.assertEqual(meta["preview"], "帮我检查 同步任务")
        self.assertEqual(meta["activeTurn"]["status"], "running")
        self.assertEqual(meta["activeTurn"]["message"], "  帮我检查\n同步任务  ")

    def test_rename_updates_persisted_metadata(self) -> None:
        persistence.state_path("sid").write_text("{}", encoding="utf-8")
        persistence.meta_path("sid").write_text(
            json.dumps({"owner": "u-1", "workspace": "1", "title": "旧标题"}),
            encoding="utf-8",
        )

        with (
            patch.object(server, "_get_settings", return_value=self._settings),
            patch.object(server, "request_identity", return_value=_identity()),
        ):
            result = asyncio.run(
                server.rename_session("sid", server.RenameSessionBody(title="  新标题  "), None)
            )

        self.assertEqual(result["title"], "新标题")
        self.assertEqual(persistence.read_meta("sid")["title"], "新标题")

    def test_rename_rejects_blank_title(self) -> None:
        from fastapi import HTTPException

        with (
            patch.object(server, "_get_settings", return_value=self._settings),
            patch.object(server, "request_identity", return_value=_identity()),
            self.assertRaises(HTTPException),
        ):
            asyncio.run(
                server.rename_session("sid", server.RenameSessionBody(title="   "), None)
            )

    def test_existing_title_is_not_overwritten(self) -> None:
        self._send("新的问题", title="旧标题")

        self.assertEqual(persistence.read_meta("sid")["title"], "旧标题")

    def test_legacy_meta_keeps_title_after_persist(self) -> None:
        persistence.meta_path("sid").write_text(
            json.dumps({"owner": "u-1", "workspace": "1", "title": "历史标题"}),
            encoding="utf-8",
        )
        session = Session(
            sid="sid",
            agent=SimpleNamespace(state=None),
            workspace=object(),
            owner="u-1",
            workspace_id="1",
        )

        persistence.persist(session)

        self.assertEqual(persistence.read_meta("sid")["title"], "历史标题")


class SessionListTitleTests(unittest.TestCase):
    def setUp(self) -> None:
        self._session_dir = persistence.SESSION_DIR
        self._tmp = tempfile.TemporaryDirectory()
        persistence.SESSION_DIR = Path(self._tmp.name)

    def tearDown(self) -> None:
        persistence.SESSION_DIR = self._session_dir
        self._tmp.cleanup()

    def test_list_returns_title_preview_and_legacy_fallback(self) -> None:
        persistence.state_path("new-session").write_text("{}", encoding="utf-8")
        persistence.meta_path("new-session").write_text(
            json.dumps(
                {
                    "owner": "u-1",
                    "workspace": "1",
                    "title": "同步任务排查",
                    "preview": "查看失败实例",
                },
                ensure_ascii=False,
            ),
            encoding="utf-8",
        )
        persistence.state_path("old-session").write_text("{}", encoding="utf-8")
        persistence.meta_path("old-session").write_text(
            json.dumps({"owner": "u-1", "workspace": "1"}),
            encoding="utf-8",
        )

        with (
            patch.object(server, "_get_settings", return_value=object()),
            patch.object(server, "request_identity", return_value=_identity()),
        ):
            items = asyncio.run(server.list_sessions(None))

        by_id = {item["sessionId"]: item for item in items}
        self.assertEqual(set(by_id), {"new-session", "old-session"})
        self.assertEqual(by_id["new-session"]["title"], "同步任务排查")
        self.assertEqual(by_id["new-session"]["preview"], "查看失败实例")
        self.assertEqual(by_id["old-session"]["title"], "历史会话 · old-se")
        self.assertEqual(by_id["old-session"]["preview"], "")


if __name__ == "__main__":
    unittest.main()
