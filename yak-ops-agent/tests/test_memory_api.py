"""Memory management endpoints and busy-session guard."""

from __future__ import annotations

import asyncio
import tempfile
import unittest
from pathlib import Path
from unittest.mock import patch

from yak_agent.api import server
from yak_agent.config import Settings
from yak_agent.memory.registry import MEMORY
from yak_agent.service.errors import MemoryDisabled, SessionBusy
from yak_agent.service.session import Identity, Session
from yak_agent.yak_client import YakAuth


def _settings(tmp: str, **overrides) -> Settings:
    values = {
        "llm_base_url": "http://llm.example",
        "llm_api_key": "test-key",
        "llm_model": "test-model",
        "yak_ops_base_url": "http://yak.example",
        "yak_ops_token": "",
        "memory_dir": Path(tmp) / "memory",
    }
    values.update(overrides)
    return Settings(**values)


def _identity(**overrides) -> Identity:
    values = {
        "owner": "u-1-10",
        "workspace_id": "1",
        "auth": YakAuth(base_url="http://yak.example", token="t"),
        "persistent": True,
    }
    values.update(overrides)
    return Identity(**values)


class MemoryEndpointTests(unittest.TestCase):
    def test_get_returns_overview_without_content(self) -> None:
        with tempfile.TemporaryDirectory() as tmp:
            settings = _settings(tmp)
            card = Path(settings.memory_dir) / "u-1-10" / "w-1" / "daily" / "2026-11-20" / "x.md"
            card.parent.mkdir(parents=True)
            card.write_text("secret", encoding="utf-8")
            with (
                patch.object(server, "_get_settings", return_value=settings),
                patch.object(server, "request_identity", return_value=_identity()),
            ):
                result = asyncio.run(server.get_memory(None))

        self.assertTrue(result["enabled"])
        self.assertEqual(result["workspaceId"], "1")
        self.assertEqual(result["files"][0]["path"], "daily/2026-11-20/x.md")
        self.assertNotIn("secret", str(result))

    def test_get_is_disabled_when_memory_is_off(self) -> None:
        with tempfile.TemporaryDirectory() as tmp:
            settings = _settings(tmp, memory_enabled=False)
            with (
                patch.object(server, "_get_settings", return_value=settings),
                patch.object(server, "request_identity", return_value=_identity()),
            ):
                with self.assertRaises(MemoryDisabled):
                    asyncio.run(server.get_memory(None))

    def test_delete_current_scope_leaves_other_workspaces(self) -> None:
        with tempfile.TemporaryDirectory() as tmp:
            settings = _settings(tmp)
            root = Path(settings.memory_dir)
            for workspace in ("1", "2"):
                (root / "u-1-10" / f"w-{workspace}").mkdir(parents=True, exist_ok=True)
            with (
                patch.object(server, "_get_settings", return_value=settings),
                patch.object(server, "request_identity", return_value=_identity()),
            ):
                result = asyncio.run(server.clear_memory(None, all_scopes=False))

            self.assertEqual(result, {"deleted": True, "scopes": 1})
            self.assertFalse((root / "u-1-10" / "w-1").exists())
            self.assertTrue((root / "u-1-10" / "w-2").exists())

    def test_delete_all_only_clears_the_caller(self) -> None:
        with tempfile.TemporaryDirectory() as tmp:
            settings = _settings(tmp)
            root = Path(settings.memory_dir)
            for owner in ("u-1-10", "u-2-20"):
                (root / owner / "w-1").mkdir(parents=True, exist_ok=True)
            with (
                patch.object(server, "_get_settings", return_value=settings),
                patch.object(server, "request_identity", return_value=_identity()),
            ):
                result = asyncio.run(server.clear_memory(None, all_scopes=True))

            self.assertEqual(result["scopes"], 1)
            self.assertFalse((root / "u-1-10").exists())
            self.assertTrue((root / "u-2-20" / "w-1").exists())

    def test_delete_is_refused_while_a_scope_is_busy(self) -> None:
        with tempfile.TemporaryDirectory() as tmp:
            settings = _settings(tmp)
            session = Session(
                sid="sid",
                agent=object(),
                workspace=object(),
                owner="u-1-10",
                workspace_id="1",
                busy=True,
            )
            server.SESSIONS["sid"] = session
            try:
                with (
                    patch.object(server, "_get_settings", return_value=settings),
                    patch.object(server, "request_identity", return_value=_identity()),
                ):
                    with self.assertRaises(SessionBusy):
                        asyncio.run(server.clear_memory(None, all_scopes=False))
            finally:
                server.SESSIONS.pop("sid", None)

    def test_global_registry_is_exported(self) -> None:
        self.assertIsInstance(MEMORY, object)


if __name__ == "__main__":
    unittest.main()
