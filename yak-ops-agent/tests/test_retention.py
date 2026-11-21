"""Session retention cleanup and atomic persistence (4.12 / 4.14)."""

from __future__ import annotations

import asyncio
import json
import os
import tempfile
import unittest
from pathlib import Path
from types import SimpleNamespace
from unittest.mock import patch

from yak_agent.service import persistence, retention

# Fixed "now" so stale/fresh mtimes stay deterministic (≈ 2027-01-15).
_NOW = 1_800_000_000.0
_STALE = _NOW - 10 * 86400.0


def _settings(**overrides) -> SimpleNamespace:
    values = {
        "session_retention_days": 0,
        "session_max_mb": 0,
        "session_cleanup_batch": 50,
        "session_cleanup_dry_run": False,
    }
    values.update(overrides)
    return SimpleNamespace(**values)


def _make_session(
    root: Path,
    sid: str,
    *,
    owner: str = "u-1",
    workspace: str = "1",
    state: bool = True,
    workspace_size: int = 100,
    mtime: float | None = None,
) -> None:
    (root / f"{sid}.meta.json").write_text(
        json.dumps({"owner": owner, "workspace": workspace}),
        encoding="utf-8",
    )
    if state:
        (root / f"{sid}.json").write_text("{}", encoding="utf-8")
    offload = root / sid / "workspace" / "offload.txt"
    offload.parent.mkdir(parents=True, exist_ok=True)
    offload.write_text("x" * workspace_size, encoding="utf-8")
    if mtime is not None:
        for path in (root / f"{sid}.meta.json", root / f"{sid}.json", offload):
            if path.exists():
                os.utime(path, (mtime, mtime))


def _session_files(sid: str) -> tuple[Path, Path, Path]:
    return (persistence.state_path(sid), persistence.meta_path(sid), persistence.SESSION_DIR / sid)


class RetentionTests(unittest.TestCase):
    def setUp(self) -> None:
        tmp = tempfile.TemporaryDirectory()
        self.root = Path(tmp.name)
        patcher = patch.object(persistence, "SESSION_DIR", self.root)
        patcher.start()
        self.addCleanup(patcher.stop)
        self.addCleanup(tmp.cleanup)

    def test_days_cleanup_deletes_only_stale_sessions(self) -> None:
        _make_session(self.root, "old", mtime=_STALE)
        _make_session(self.root, "fresh", mtime=_NOW - 60)

        stats = retention.cleanup_sessions(
            _settings(session_retention_days=1),
            now=_NOW,
        )

        self.assertEqual((stats.scanned, stats.deleted, stats.skipped_loaded), (2, 1, 0))
        self.assertGreater(stats.bytes_freed, 0)
        for path in _session_files("old"):
            self.assertFalse(path.exists())
        for path in _session_files("fresh"):
            self.assertTrue(path.exists())

    def test_loaded_sessions_are_never_deleted(self) -> None:
        _make_session(self.root, "loaded", mtime=_STALE)

        stats = retention.cleanup_sessions(
            _settings(session_retention_days=1),
            now=_NOW,
            sessions={"loaded": object()},
        )

        self.assertEqual((stats.deleted, stats.skipped_loaded), (0, 1))
        for path in _session_files("loaded"):
            self.assertTrue(path.exists())

    def test_capacity_deletes_oldest_until_under_limit(self) -> None:
        _make_session(self.root, "old", workspace_size=700 * 1024, mtime=1000.0)
        _make_session(self.root, "new", workspace_size=700 * 1024, mtime=2000.0)

        stats = retention.cleanup_sessions(_settings(session_max_mb=1))

        self.assertEqual(stats.deleted, 1)
        self.assertFalse(persistence.state_path("old").exists())
        self.assertTrue(persistence.state_path("new").exists())
        self.assertLessEqual(stats.bytes_freed, 700 * 1024 + 102)

    def test_capacity_counts_loaded_sessions_but_never_deletes_them(self) -> None:
        _make_session(self.root, "big-loaded", workspace_size=800 * 1024)
        _make_session(self.root, "big-unloaded", workspace_size=800 * 1024)

        stats = retention.cleanup_sessions(
            _settings(session_max_mb=1),
            sessions={"big-loaded": object()},
        )

        # loaded counts toward the total but cannot be deleted; the unloaded
        # one alone cannot bring 1.6MB back under 1MB
        self.assertEqual(stats.deleted, 1)
        self.assertTrue(persistence.state_path("big-loaded").exists())

    def test_batch_limits_deletions_per_pass(self) -> None:
        _make_session(self.root, "a", mtime=_STALE)
        _make_session(self.root, "b", mtime=_STALE + 1)

        stats = retention.cleanup_sessions(
            _settings(session_retention_days=1, session_cleanup_batch=1),
            now=_NOW,
        )

        self.assertEqual(stats.deleted, 1)
        self.assertTrue(persistence.state_path("b").exists())

    def test_batch_deletes_the_oldest_stale_sessions_first(self) -> None:
        # Created newest-first so directory enumeration order cannot pass this.
        _make_session(self.root, "newer", mtime=_STALE + 100)
        _make_session(self.root, "older", mtime=_STALE)

        stats = retention.cleanup_sessions(
            _settings(session_retention_days=1, session_cleanup_batch=1),
            now=_NOW,
        )

        self.assertEqual(stats.deleted, 1)
        self.assertFalse(persistence.state_path("older").exists())
        self.assertTrue(persistence.state_path("newer").exists())

    def test_dry_run_counts_but_never_deletes(self) -> None:
        _make_session(self.root, "old", mtime=_STALE)

        stats = retention.cleanup_sessions(
            _settings(session_retention_days=1, session_cleanup_dry_run=True),
            now=_NOW,
        )

        self.assertEqual(stats.deleted, 1)
        for path in _session_files("old"):
            self.assertTrue(path.exists())

    def test_state_without_meta_is_never_a_candidate(self) -> None:
        (self.root / "orphan.json").write_text("{}", encoding="utf-8")

        stats = retention.cleanup_sessions(_settings(session_retention_days=1), now=_NOW)

        self.assertEqual(stats.scanned, 0)
        self.assertEqual(stats.deleted, 0)
        self.assertTrue((self.root / "orphan.json").exists())

    def test_bare_meta_file_never_resolves_to_the_sessions_dir(self) -> None:
        """A file named exactly ".meta.json" would make rmtree target SESSION_DIR."""
        (self.root / ".meta.json").write_text(
            json.dumps({"owner": "u-1", "workspace": "1"}),
            encoding="utf-8",
        )
        os.utime(self.root / ".meta.json", (_STALE, _STALE))

        stats = retention.cleanup_sessions(_settings(session_retention_days=1), now=_NOW)

        self.assertEqual(stats.scanned, 0)
        self.assertEqual(stats.deleted, 0)
        self.assertTrue(self.root.exists())
        self.assertTrue((self.root / ".meta.json").exists())

    def test_meta_without_owner_is_never_a_candidate(self) -> None:
        (self.root / "no-owner.meta.json").write_text(
            json.dumps({"workspace": "1"}),
            encoding="utf-8",
        )

        stats = retention.cleanup_sessions(_settings(session_retention_days=1), now=_NOW)

        self.assertEqual(stats.scanned, 0)
        self.assertTrue((self.root / "no-owner.meta.json").exists())

    def test_unrelated_directories_are_untouched(self) -> None:
        memory = self.root / "memory" / "session"
        memory.mkdir(parents=True)
        (memory / "keep.jsonl").write_text("data", encoding="utf-8")
        _make_session(self.root, "old", mtime=_STALE)

        retention.cleanup_sessions(_settings(session_retention_days=1), now=_NOW)

        self.assertTrue((memory / "keep.jsonl").exists())

    def test_loop_runs_once_when_interval_is_non_positive(self) -> None:
        with patch.object(
            retention, "cleanup_sessions", return_value=retention.RetentionStats()
        ) as cleanup:
            asyncio.run(retention.retention_loop(_settings(session_cleanup_interval_seconds=0)))
        self.assertEqual(cleanup.call_count, 1)


class AtomicWriteTests(unittest.TestCase):
    def test_write_atomic_replaces_content_without_temp_leftovers(self) -> None:
        with tempfile.TemporaryDirectory() as tmp:
            path = Path(tmp) / "state.json"

            persistence.write_atomic(path, "old")
            persistence.write_atomic(path, "new")

            self.assertEqual(path.read_text(encoding="utf-8"), "new")
            self.assertEqual(list(Path(tmp).iterdir()), [path])

    def test_failed_replace_keeps_previous_file_and_cleans_temp(self) -> None:
        with tempfile.TemporaryDirectory() as tmp:
            path = Path(tmp) / "state.json"
            persistence.write_atomic(path, "old")

            with patch.object(persistence.os, "replace", side_effect=OSError("busy")):
                with self.assertRaises(OSError):
                    persistence.write_atomic(path, "new")

            self.assertEqual(path.read_text(encoding="utf-8"), "old")
            self.assertEqual(list(Path(tmp).iterdir()), [path])

    def test_persist_writes_complete_json_after_replacement(self) -> None:
        with tempfile.TemporaryDirectory() as tmp:
            patcher = patch.object(persistence, "SESSION_DIR", Path(tmp))
            patcher.start()
            self.addCleanup(patcher.stop)
            session = SimpleNamespace(
                persistent=True,
                sid="sid",
                owner="u-1",
                workspace_id="1",
                title="标题",
                preview="预览",
                usage={"input": 10},
                turn=None,
                agent=SimpleNamespace(state=None),
            )

            persistence.persist(session)
            persistence.persist(session)  # second write replaces the first atomically

            meta = json.loads((Path(tmp) / "sid.meta.json").read_text(encoding="utf-8"))
            self.assertEqual(meta["owner"], "u-1")
            self.assertEqual(meta["usage"], {"input": 10})
            self.assertNotIn("activeTurn", meta)
            self.assertEqual(list(Path(tmp).iterdir()), [Path(tmp) / "sid.meta.json"])


if __name__ == "__main__":
    unittest.main()
