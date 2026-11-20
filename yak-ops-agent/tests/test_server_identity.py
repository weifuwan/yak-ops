"""Regression tests for the review fixes in the agent service.

Covers:
- owner keys without createTime are non-persistent and never collapse to
  ``u-{id}-na``;
- non-persistent sessions never write state/meta files;
- deleting a busy session is rejected with 409;
- workspace authorization is re-checked on every request.
"""

from __future__ import annotations

import asyncio
import json
import tempfile
import unittest
from pathlib import Path
from types import SimpleNamespace
from unittest.mock import patch

from fastapi import HTTPException
from starlette.requests import Request

from yak_agent.api import deps, errors, server
from yak_agent.service import persistence
from yak_agent.service import session as session_service
from yak_agent.service.errors import (
    ConfirmationInvalid,
    ConfirmationNotPending,
    SessionBusy,
    SessionForbidden,
    SessionNotFound,
    SessionStateInvalid,
)
from yak_agent.yak_client import YakApiError


class UserKeyTests(unittest.TestCase):
    def test_create_time_key_is_persistent(self) -> None:
        owner, persistent = deps.user_key(
            {"id": 7, "createTime": "2026-01-02 03:04:05", "userName": "alice"},
            "login-a",
        )

        self.assertEqual(owner, "u-7-20260102030405")
        self.assertTrue(persistent)

    def test_legacy_user_is_non_persistent_and_login_scoped(self) -> None:
        owner, persistent = deps.user_key(
            {"id": 7, "createTime": None, "userName": "alice"},
            "login-a",
        )

        self.assertFalse(persistent)
        self.assertNotEqual(owner, "u-7-na")
        self.assertNotEqual(owner, "u-7")

        # Stable within one login, but a new login (or a recycled id) gets a
        # different owner and cannot inherit the in-memory session.
        same, _ = deps.user_key(
            {"id": 7, "createTime": None, "userName": "alice"},
            "login-a",
        )
        replayed, _ = deps.user_key(
            {"id": 7, "createTime": None, "userName": "alice"},
            "login-b",
        )
        self.assertEqual(owner, same)
        self.assertNotEqual(owner, replayed)

    def test_legacy_user_without_user_name_is_login_scoped(self) -> None:
        first, persistent = deps.user_key({"id": 7}, "login-a")
        second, _ = deps.user_key({"id": 7}, "login-b")

        self.assertFalse(persistent)
        self.assertNotEqual(first, second)
        self.assertNotEqual(first, "u-7-na")


class PersistenceTests(unittest.TestCase):
    def test_persistent_session_writes_state_and_meta(self) -> None:
        with tempfile.TemporaryDirectory() as tmp:
            original_dir = persistence.SESSION_DIR
            persistence.SESSION_DIR = Path(tmp)
            try:
                session = session_service.Session(
                    sid="sid",
                    agent=SimpleNamespace(
                        state=SimpleNamespace(model_dump_json=lambda: '{"ok": true}')
                    ),
                    workspace=object(),
                    owner="u-7-20260102",
                    workspace_id="1",
                )

                persistence.persist(session)

                self.assertTrue((Path(tmp) / "sid.json").exists())
                self.assertTrue((Path(tmp) / "sid.meta.json").exists())
            finally:
                persistence.SESSION_DIR = original_dir

    def test_non_persistent_session_never_writes_files(self) -> None:
        with tempfile.TemporaryDirectory() as tmp:
            original_dir = persistence.SESSION_DIR
            persistence.SESSION_DIR = Path(tmp)
            try:
                session = session_service.Session(
                    sid="sid",
                    agent=object(),
                    workspace=object(),
                    owner="u-7-s-abc",
                    workspace_id="1",
                    persistent=False,
                )

                persistence.persist(session)

                self.assertFalse((Path(tmp) / "sid.json").exists())
                self.assertFalse((Path(tmp) / "sid.meta.json").exists())
            finally:
                persistence.SESSION_DIR = original_dir

    def test_non_persistent_identity_cannot_load_from_disk(self) -> None:
        identity = session_service.Identity(
            owner="u-7-s-abc",
            workspace_id="1",
            auth=None,
            persistent=False,
        )

        with self.assertRaises(SessionNotFound):
            session_service.load_session("does-not-exist", identity, None)

    def test_create_session_with_legacy_identity_writes_no_files(self) -> None:
        with tempfile.TemporaryDirectory() as tmp:
            original_dir = persistence.SESSION_DIR
            persistence.SESSION_DIR = Path(tmp)
            identity = session_service.Identity(
                owner="u-7-s-abc",
                workspace_id="1",
                auth=None,
                persistent=False,
            )
            try:
                with (
                    patch.object(server, "_get_settings", return_value=object()),
                    patch.object(server, "request_identity", return_value=identity),
                    patch.object(session_service, "build_agent", return_value=object()),
                ):
                    result = asyncio.run(server.create_session(None))

                sid = result["sessionId"]
                self.assertFalse((Path(tmp) / f"{sid}.json").exists())
                self.assertFalse((Path(tmp) / f"{sid}.meta.json").exists())
                self.assertIn(sid, session_service.SESSIONS)
                session_service.SESSIONS.pop(sid, None)
            finally:
                persistence.SESSION_DIR = original_dir


class WorkspaceAuthorizationTests(unittest.TestCase):
    @staticmethod
    def _request(workspace_id: str = "1") -> Request:
        return Request(
            {
                "type": "http",
                "method": "GET",
                "path": "/",
                "headers": [
                    (b"x-yak-session-id", b"login-a"),
                    (b"x-yak-workspace-id", workspace_id.encode()),
                ],
                "query_string": b"",
                "server": ("test", 80),
                "client": ("127.0.0.1", 1234),
                "scheme": "http",
            }
        )

    def test_request_identity_checks_workspace_on_every_call(self) -> None:
        settings = SimpleNamespace(yak_ops_base_url="http://platform")
        with (
            patch.object(deps, "service_guard"),
            patch.object(
                deps,
                "fetch_user",
                return_value={"id": 7, "createTime": "2026-01-02"},
            ),
            patch.object(deps, "verify_workspace") as verify,
        ):
            identity = deps.request_identity(self._request(), settings)

        verify.assert_called_once()
        self.assertEqual(identity.workspace_id, "1")
        self.assertTrue(identity.persistent)

    def test_revoked_workspace_is_rejected(self) -> None:
        settings = SimpleNamespace(yak_ops_base_url="http://platform")
        with (
            patch.object(deps, "service_guard"),
            patch.object(
                deps,
                "fetch_user",
                return_value={"id": 7, "createTime": "2026-01-02"},
            ),
            patch.object(
                deps,
                "verify_workspace",
                side_effect=HTTPException(status_code=403, detail="revoked"),
            ),
        ):
            with self.assertRaises(HTTPException) as ctx:
                deps.request_identity(self._request(), settings)

        self.assertEqual(ctx.exception.status_code, 403)


class WorkspaceVerificationTests(unittest.TestCase):
    def test_successful_project_lookup_means_authorized(self) -> None:
        with patch.object(deps, "yak_get", return_value={"code": 7}) as get:
            deps.verify_workspace(SimpleNamespace(), "7")

        get.assert_called_once()
        self.assertEqual(get.call_args.args[1], "/projects/7")

    def test_none_project_is_rejected(self) -> None:
        with patch.object(deps, "yak_get", return_value=None):
            with self.assertRaises(HTTPException) as ctx:
                deps.verify_workspace(SimpleNamespace(), "7")

        self.assertEqual(ctx.exception.status_code, 403)

    def test_empty_project_is_rejected(self) -> None:
        with patch.object(deps, "yak_get", return_value={}):
            with self.assertRaises(HTTPException) as ctx:
                deps.verify_workspace(SimpleNamespace(), "7")

        self.assertEqual(ctx.exception.status_code, 403)

    def test_mismatched_project_is_rejected(self) -> None:
        with patch.object(deps, "yak_get", return_value={"code": 8}):
            with self.assertRaises(HTTPException) as ctx:
                deps.verify_workspace(SimpleNamespace(), "7")

        self.assertEqual(ctx.exception.status_code, 403)

    def test_string_project_code_is_accepted(self) -> None:
        with patch.object(deps, "yak_get", return_value={"code": "7"}):
            deps.verify_workspace(SimpleNamespace(), "7")

    def test_permission_business_error_maps_to_403(self) -> None:
        with patch.object(
            deps,
            "yak_get",
            side_effect=YakApiError("denied", business_code=30002),
        ):
            with self.assertRaises(HTTPException) as ctx:
                deps.verify_workspace(SimpleNamespace(), "7")

        self.assertEqual(ctx.exception.status_code, 403)

    def test_missing_project_business_error_maps_to_403(self) -> None:
        with patch.object(
            deps,
            "yak_get",
            side_effect=YakApiError("missing", business_code=10190),
        ):
            with self.assertRaises(HTTPException) as ctx:
                deps.verify_workspace(SimpleNamespace(), "7")

        self.assertEqual(ctx.exception.status_code, 403)

    def test_expired_session_maps_to_401(self) -> None:
        with patch.object(
            deps,
            "yak_get",
            side_effect=YakApiError("expired", status_code=401),
        ):
            with self.assertRaises(HTTPException) as ctx:
                deps.verify_workspace(SimpleNamespace(), "7")

        self.assertEqual(ctx.exception.status_code, 401)

    def test_upstream_failure_maps_to_502(self) -> None:
        with patch.object(
            deps,
            "yak_get",
            side_effect=YakApiError("boom", status_code=500),
        ):
            with self.assertRaises(HTTPException) as ctx:
                deps.verify_workspace(SimpleNamespace(), "7")

        self.assertEqual(ctx.exception.status_code, 502)


class DeleteSessionTests(unittest.TestCase):
    def test_deleting_busy_session_returns_409(self) -> None:
        identity = session_service.Identity(
            owner="u-1-20260102",
            workspace_id="1",
            auth=None,
            persistent=True,
        )
        session = session_service.Session(
            sid="sid",
            agent=object(),
            workspace=object(),
            owner=identity.owner,
            workspace_id=identity.workspace_id,
            busy=True,
        )
        session_service.SESSIONS["sid"] = session
        try:
            with patch.object(server, "request_identity", return_value=identity):
                with self.assertRaises(SessionBusy):
                    asyncio.run(server.delete_session("sid", None))

            self.assertIn("sid", session_service.SESSIONS)
        finally:
            session_service.SESSIONS.pop("sid", None)


class SessionErrorHandlerTests(unittest.TestCase):
    def test_session_errors_map_to_one_http_status_table(self) -> None:
        cases = (
            (SessionForbidden, 403),
            (SessionNotFound, 404),
            (SessionBusy, 409),
            (ConfirmationNotPending, 409),
            (ConfirmationInvalid, 422),
            (SessionStateInvalid, 422),
        )
        for error_type, expected_status in cases:
            with self.subTest(error=error_type.__name__):
                response = asyncio.run(errors.session_error_handler(None, error_type()))
                self.assertEqual(response.status_code, expected_status)
                payload = json.loads(response.body)
                self.assertEqual(payload["detail"], error_type.default_message)


if __name__ == "__main__":
    unittest.main()
