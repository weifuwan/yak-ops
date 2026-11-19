"""Request identity and authorization dependencies."""

from __future__ import annotations

import hashlib
import os
import re
from typing import Any

from fastapi import HTTPException, Request
from starlette import status

from yak_agent.api import messages
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
                status_code=status.HTTP_401_UNAUTHORIZED,
                detail=messages.SERVICE_TOKEN_INVALID,
            )
        return
    client_host = request.client.host if request.client else "?"
    if client_host not in ("127.0.0.1", "::1", "localhost"):
        raise HTTPException(
            status_code=status.HTTP_401_UNAUTHORIZED,
            detail=messages.NON_LOCAL_CALLER,
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
            status_code=status.HTTP_401_UNAUTHORIZED,
            detail=messages.MISSING_IDENTITY_HEADERS,
        )
    if not workspace_id.isdigit():
        raise HTTPException(
            status_code=status.HTTP_400_BAD_REQUEST,
            detail=messages.INVALID_WORKSPACE_ID,
        )
    auth = YakAuth(base_url=settings.yak_ops_base_url, session_id=session_id)
    user = fetch_user(auth)
    if claimed_user_id and str(user.get("id")) != claimed_user_id:
        raise HTTPException(
            status_code=status.HTTP_403_FORBIDDEN,
            detail=messages.USER_MISMATCH,
        )
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
                status_code=status.HTTP_401_UNAUTHORIZED,
                detail=messages.UNAUTHENTICATED,
            ) from ex
        raise HTTPException(
            status_code=status.HTTP_502_BAD_GATEWAY,
            detail=messages.UPSTREAM_USER,
        ) from ex
    if not isinstance(data, dict) or data.get("id") is None:
        raise HTTPException(
            status_code=status.HTTP_502_BAD_GATEWAY,
            detail=messages.UNEXPECTED_USER,
        )
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


# PROJECT_NOT_FOUND / PROJECT_NOT_EXIST / USER_NO_OPERATION_PROJECT_PERM
_PROJECT_ACCESS_DENIED_CODES = frozenset({10018, 10190, 30002})


def verify_workspace(auth: YakAuth, workspace_id: str) -> None:
    """Platform-side workspace (project) permission check.

    `GET /projects/{code}` already performs existence and permission checks
    (`checkProjectAndAuthThrowException`), so a successful call means the
    caller may use this workspace. A business error for a missing project or
    a permission denial is reported as 403 to avoid leaking existence.
    """
    try:
        yak_get(auth, f"/projects/{workspace_id}")
    except YakApiError as ex:
        if ex.status_code == status.HTTP_401_UNAUTHORIZED:
            raise HTTPException(
                status_code=status.HTTP_401_UNAUTHORIZED,
                detail=messages.UNAUTHENTICATED,
            ) from ex
        if ex.business_code in _PROJECT_ACCESS_DENIED_CODES or ex.status_code in (
            status.HTTP_403_FORBIDDEN,
            status.HTTP_404_NOT_FOUND,
        ):
            raise HTTPException(
                status_code=status.HTTP_403_FORBIDDEN,
                detail=messages.WORKSPACE_FORBIDDEN,
            ) from ex
        raise HTTPException(
            status_code=status.HTTP_502_BAD_GATEWAY,
            detail=messages.UPSTREAM_WORKSPACE,
        ) from ex
