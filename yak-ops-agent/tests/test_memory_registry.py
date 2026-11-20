"""Memory registry: scope reuse, isolation and cleanup."""

from __future__ import annotations

import asyncio
import tempfile
import unittest
from pathlib import Path

from yak_agent.config import Settings
from yak_agent.memory.registry import MemoryRegistry, memory_overview


def _settings(tmp: str, **overrides) -> Settings:
    values = {
        "llm_base_url": "http://llm.example",
        "llm_api_key": "test-key",
        "llm_model": "test-model",
        "yak_ops_base_url": "http://yak.example",
        "yak_ops_token": "",
        "memory_dir": Path(tmp) / "memory",
        "memory_enabled": True,
        "memory_max_scopes": 2,
        "memory_idle_seconds": 0,
    }
    values.update(overrides)
    return Settings(**values)


class MemoryRegistryTests(unittest.TestCase):
    def test_same_scope_reuses_one_instance(self) -> None:
        with tempfile.TemporaryDirectory() as tmp:
            settings = _settings(tmp)
            registry = MemoryRegistry()

            first = registry.get("u-1-10", "1", settings)
            second = registry.get("u-1-10", "1", settings)

            self.assertIsNotNone(first)
            self.assertIs(first, second)

    def test_users_and_workspaces_get_separate_instances(self) -> None:
        with tempfile.TemporaryDirectory() as tmp:
            settings = _settings(tmp)
            registry = MemoryRegistry()

            a = registry.get("u-1-10", "1", settings)
            b = registry.get("u-2-20", "1", settings)
            c = registry.get("u-1-10", "2", settings)

            self.assertIsNot(a, b)
            self.assertIsNot(a, c)
            self.assertEqual(
                {Path(item._workspace_dir).name for item in (a, b, c) if item is not None},
                {"w-1", "w-2"},
            )

    def test_disabled_memory_returns_none(self) -> None:
        with tempfile.TemporaryDirectory() as tmp:
            settings = _settings(tmp, memory_enabled=False)
            self.assertIsNone(MemoryRegistry().get("u-1-10", "1", settings))

    def test_invalid_owner_degrades_to_none(self) -> None:
        with tempfile.TemporaryDirectory() as tmp:
            settings = _settings(tmp)
            self.assertIsNone(MemoryRegistry().get("../escape", "1", settings))

    def test_idle_scope_is_evicted_over_the_limit(self) -> None:
        with tempfile.TemporaryDirectory() as tmp:
            settings = _settings(tmp)
            registry = MemoryRegistry()

            first = registry.get("u-1-10", "1", settings)
            registry.get("u-2-20", "1", settings)
            registry.get("u-3-30", "1", settings)

            self.assertIsNotNone(first)
            self.assertEqual(len(registry._entries), 2)
            self.assertNotIn(("u-1-10", "1"), registry._entries)

    def test_active_scope_is_not_evicted(self) -> None:
        with tempfile.TemporaryDirectory() as tmp:
            settings = _settings(tmp)
            registry = MemoryRegistry()

            active = registry.get("u-1-10", "1", settings)
            assert active is not None
            active.begin_turn()
            registry.get("u-2-20", "1", settings)
            registry.get("u-3-30", "1", settings)

            self.assertIn(("u-1-10", "1"), registry._entries)

    def test_in_use_scope_is_not_evicted_even_when_idle(self) -> None:
        with tempfile.TemporaryDirectory() as tmp:
            settings = _settings(tmp)
            registry = MemoryRegistry()
            in_use = lambda owner, _w: owner == "u-1-10"  # noqa: E731

            registry.get("u-1-10", "1", settings, in_use=in_use)
            registry.get("u-2-20", "1", settings, in_use=in_use)
            registry.get("u-3-30", "1", settings, in_use=in_use)

            self.assertIn(("u-1-10", "1"), registry._entries)
            self.assertNotIn(("u-2-20", "1"), registry._entries)

    def test_clear_keeps_the_handle_while_a_session_still_uses_it(self) -> None:
        with tempfile.TemporaryDirectory() as tmp:
            settings = _settings(tmp)
            registry = MemoryRegistry()
            registry.get("u-1-10", "1", settings)

            async def scenario():
                return await registry.clear_scope(
                    "u-1-10",
                    "1",
                    settings,
                    in_use=lambda owner, _w: owner == "u-1-10",
                )

            self.assertEqual(asyncio.run(scenario()), 1)
            self.assertIn(("u-1-10", "1"), registry._entries)
            self.assertFalse((Path(settings.memory_dir) / "u-1-10" / "w-1").exists())

    def test_clear_scope_only_removes_that_workspace(self) -> None:
        with tempfile.TemporaryDirectory() as tmp:
            settings = _settings(tmp)
            registry = MemoryRegistry()
            root = Path(settings.memory_dir)
            for owner, workspace in (("u-1-10", "1"), ("u-1-10", "2"), ("u-2-20", "1")):
                path = root / owner / f"w-{workspace}" / "daily" / "2026-11-20" / "card.md"
                path.parent.mkdir(parents=True, exist_ok=True)
                path.write_text("x", encoding="utf-8")

            cleared = asyncio.run(registry.clear_scope("u-1-10", "1", settings))

            self.assertEqual(cleared, 1)
            self.assertFalse((root / "u-1-10" / "w-1").exists())
            self.assertTrue((root / "u-1-10" / "w-2" / "daily").exists())
            self.assertTrue((root / "u-2-20" / "w-1" / "daily").exists())

    def test_clear_owner_only_removes_that_user(self) -> None:
        with tempfile.TemporaryDirectory() as tmp:
            settings = _settings(tmp)
            registry = MemoryRegistry()
            root = Path(settings.memory_dir)
            for owner, workspace in (("u-1-10", "1"), ("u-1-10", "2"), ("u-2-20", "1")):
                (root / owner / f"w-{workspace}").mkdir(parents=True, exist_ok=True)

            cleared = asyncio.run(registry.clear_owner("u-1-10", settings))

            self.assertEqual(cleared, 2)
            self.assertFalse((root / "u-1-10").exists())
            self.assertTrue((root / "u-2-20" / "w-1").exists())

    def test_overview_returns_paths_and_sizes_not_content(self) -> None:
        with tempfile.TemporaryDirectory() as tmp:
            settings = _settings(tmp)
            root = Path(settings.memory_dir) / "u-1-10" / "w-1"
            daily = root / "daily" / "2026-11-20" / "card.md"
            daily.parent.mkdir(parents=True)
            daily.write_text("secret body", encoding="utf-8")
            digest = root / "digest" / "note.md"
            digest.parent.mkdir(parents=True)
            digest.write_text("digest", encoding="utf-8")

            overview = memory_overview(settings, "u-1-10", "1")

            self.assertEqual(
                [item["path"] for item in overview["files"]],
                ["daily/2026-11-20/card.md", "digest/note.md"],
            )
            self.assertEqual(overview["totalBytes"], len("secret body") + len("digest"))
            self.assertNotIn("secret body", str(overview))


if __name__ == "__main__":
    unittest.main()
