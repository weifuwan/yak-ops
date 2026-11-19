"""Request identity and authorization dependencies."""

from __future__ import annotations

import hashlib
import os
import re
from typing import Any

from fastapi import HTTPException, Request

from yak_agent.config import Settings
from yak_agent.service.session import Identity
from yak_agent.yak_client import YakApiError, YakAuth, yak_get


def service_token() -> str:
    return os.environ.get("YAK_AGENT_SERVICE_TOKEN", "").strip()


def service_guard(request: Request) -> None:
    """Service-level access control (layer 1 of the two-layer auth).

    With YAK_AGENT_SERVICE_TOKEN set, callers must present
    `Authorization: Bearer <token>` (401 otherwise). Without a token the
    service is loopback-only by construction — non-local clients are refused
    (401) so an accidental 0.0.0.0 binding cannot silently expose writes.
    """
    token = service_token()
    if token:
        if request.headers.get("Authorization", "") != f"Bearer {token}":
            raise HTTPException(
                status_code=401,
                detail="unauthorized: missing or invalid service token",
            )
        return
    client_host = request.client.host if request.client else "?"
    if client_host not in ("127.0.0.1", "::1", "localhost"):
        raise HTTPException(
            status_code=401,
            detail="unauthorized: set YAK_AGENT_SERVICE_TOKEN for non-local callers",
        )


def request_identity(request: Request, settings: Settings) -> Identity:
    """Resolve the caller identity from the frontend headers.

    The frontend always sends:

    - `X-Yak-Session-Id`: the yak-ops login session (validated by the platform);
    - `X-Yak-User-Id`: the platform user id (cross-checked, never trusted);
    - `X-Yak-Workspace-Id`: the current workspace, i.e. the project code.

    The user id is verified against `GET /users/get-user-info`; a mismatch is
    rejected. The owner key uses the verified id plus the immutable createTime,
    so a reused auto-increment id cannot inherit old sessions. Legacy rows
    without createTime get a process-local, non-persistent owner instead.
    """
    service_guard(request)
    session_id = request.headers.get("X-Yak-Session-Id", "").strip()
    workspace_id = request.headers.get("X-Yak-Workspace-Id", "").strip()
    claimed_user_id = request.headers.get("X-Yak-User-Id", "").strip()
    if not session_id or not workspace_id:
        raise HTTPException(
            status_code=401,
            detail="missing X-Yak-Session-Id or X-Yak-Workspace-Id",
        )
    if not workspace_id.isdigit():
        raise HTTPException(status_code=400, detail="X-Yak-Workspace-Id must be numeric")
    auth = YakAuth(base_url=settings.yak_ops_base_url, session_id=session_id)
    user = fetch_user(auth)
    if claimed_user_id and str(user.get("id")) != claimed_user_id:
        raise HTTPException(status_code=403, detail="user id does not match the session")
    owner, persistent = user_key(user, session_id)
    # Authorization is re-checked on every request, not only when the session
    # is created: a revoked workspace must not keep serving persisted history.
    verify_workspace(auth, workspace_id)
    return Identity(
        owner=owner,
        workspace_id=workspace_id,
        auth=auth,
        persistent=persistent,
    )


def fetch_user(auth: YakAuth) -> dict[str, Any]:
    try:
        data = yak_get(auth, "/users/get-user-info")
    except YakApiError as ex:
        if ex.status_code == 401:
            raise HTTPException(
                status_code=401,
                detail="yak-ops session invalid or expired; re-login and retry",
            ) from ex
        raise HTTPException(
            status_code=502,
            detail=f"yak-ops user verification failed: {ex}",
        ) from ex
    if not isinstance(data, dict) or data.get("id") is None:
        raise HTTPException(status_code=502, detail="unexpected user info response")
    return data


def user_key(user: dict[str, Any], session_id: str) -> tuple[str, bool]:
    """Derive the owner key and whether it may own on-disk sessions.

    Only `id` + the immutable `createTime` is reuse-safe, so only that key is
    allowed to persist sessions. `create_time` is nullable in legacy rows;
    those callers are bound to the login session and stay in memory only, so a
    recycled auto-increment id can never inherit old state.
    """
    user_id = user["id"]
    digits = re.sub(r"\D", "", str(user.get("createTime") or ""))
    if digits:
        return f"u-{user_id}-{digits}", True
    digest = hashlib.sha256(session_id.encode("utf-8")).hexdigest()[:16]
    return f"u-{user_id}-s-{digest}", False


def verify_workspace(auth: YakAuth, workspace_id: str) -> None:
    """Platform-side workspace (project) permission check — the sessionId is
    validated by the platform on this call, and the requested workspace must be
    granted to the session's user."""
    page_no = 1
    while True:
        try:
            data = yak_get(auth, "/projects", {"pageNo": page_no, "pageSize": 100})
        except YakApiError as ex:
            if ex.status_code == 401:
                raise HTTPException(
                    status_code=401,
                    detail="yak-ops session invalid or expired; re-login and retry",
                ) from ex
            raise HTTPException(
                status_code=502,
                detail=f"yak-ops workspace verification failed: {ex}",
            ) from ex
        payload = data or {}
        items = payload.get("totalList") or []
        codes = {str(item.get("code")) for item in items}
        if str(workspace_id) in codes:
            return
        total = int(payload.get("total") or 0)
        page_size = int(payload.get("pageSize") or 100)
        if not items or page_size <= 0 or page_no * page_size >= total:
            break
        page_no += 1
    raise HTTPException(
        status_code=403,
        detail="the requested workspace is not granted to this user",
    )
