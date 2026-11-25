"""Session lifecycle regressions: idle unloading and the delete race guard."""

from __future__ import annotations

import unittest
from types import SimpleNamespace
from unittest.mock import patch

from yak_agent.api import server
from yak_agent.service import session as session_service
from yak_agent.service.errors import SessionNotFound
from yak_agent.service.session import Session, evict_idle_sessions, load_session


def _session(sid: str = "sid") -> Session:
    return Session(sid=sid, agent=object(), workspace=object(), owner="u-1", workspace_id="1")


class EvictionTests(unittest.TestCase):
    def test_idle_session_is_persisted_and_unloaded(self) -> None:
        session = _session()
        session.last_active_at = 100.0
        settings = SimpleNamespace(session_idle_seconds=60)

        with (
            patch.dict(session_service.SESSIONS, {"sid": session}, clear=True),
            patch.object(session_service.persistence, "persist") as persist,
        ):
            self.assertEqual(evict_idle_sessions(settings, now=200.0), 1)
            persist.assert_called_once_with(session)
            self.assertNotIn("sid", session_service.SESSIONS)

    def test_failed_persist_keeps_the_session_loaded(self) -> None:
        session = _session()
        session.last_active_at = 0.0
        settings = SimpleNamespace(session_idle_seconds=60)

        with (
            patch.dict(session_service.SESSIONS, {"sid": session}, clear=True),
            patch.object(session_service.persistence, "persist", return_value=False),
        ):
            self.assertEqual(evict_idle_sessions(settings, now=10_000.0), 0)
            self.assertIn("sid", session_service.SESSIONS)

    def test_active_sessions_are_kept(self) -> None:
        settings = SimpleNamespace(session_idle_seconds=60)
        cases = {
            "busy": _session("busy"),
            "queued": _session("queued"),
            "deleting": _session("deleting"),
        }
        cases["busy"].busy = True
        cases["queued"].queue.append(object())
        cases["deleting"].deleting = True
        for session in cases.values():
            session.last_active_at = 0.0

        with patch.dict(session_service.SESSIONS, cases, clear=True):
            self.assertEqual(evict_idle_sessions(settings, now=10_000.0), 0)
            self.assertEqual(set(session_service.SESSIONS), set(cases))

    def test_eviction_is_batched(self) -> None:
        settings = SimpleNamespace(session_idle_seconds=60)
        sessions = {f"s{i}": _session(f"s{i}") for i in range(5)}
        for session in sessions.values():
            session.last_active_at = 0.0

        with (
            patch.dict(session_service.SESSIONS, sessions, clear=True),
            patch.object(session_service.persistence, "persist", return_value=True),
        ):
            self.assertEqual(evict_idle_sessions(settings, now=10_000.0, max_batch=2), 2)
            self.assertEqual(len(session_service.SESSIONS), 3)

    def test_zero_ttl_disables_eviction(self) -> None:
        session = _session()
        session.last_active_at = 0.0
        with patch.dict(session_service.SESSIONS, {"sid": session}, clear=True):
            self.assertEqual(
                evict_idle_sessions(SimpleNamespace(session_idle_seconds=0), now=10_000.0),
                0,
            )


class DeleteRaceTests(unittest.IsolatedAsyncioTestCase):
    async def test_a_deleting_session_cannot_start_a_turn(self) -> None:
        session = _session()
        session.deleting = True

        with self.assertRaises(SessionNotFound):
            await server._begin_turn(session, "hello", SimpleNamespace())

        self.assertFalse(session.busy)
        self.assertIsNone(session.turn)

    def test_a_deleting_sid_cannot_be_reloaded_from_disk(self) -> None:
        identity = SimpleNamespace(owner="u-1", workspace_id="1", auth=None, persistent=True)
        with patch.dict(session_service.SESSIONS, {}, clear=True):
            session_service.mark_deleting("sid")
            try:
                with self.assertRaises(SessionNotFound):
                    load_session("sid", identity, None)
            finally:
                session_service.unmark_deleting("sid")


if __name__ == "__main__":
    unittest.main()
