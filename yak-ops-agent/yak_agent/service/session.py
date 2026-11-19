"""Session records, identity, and lifecycle."""

from __future__ import annotations

import asyncio
from dataclasses import dataclass, field
from typing import Any

from agentscope.event import RequireUserConfirmEvent
from agentscope.state import AgentState

from yak_agent.agent.builder import build_agent
from yak_agent.config import Settings
from yak_agent.service import persistence
from yak_agent.service.errors import SessionForbidden, SessionNotFound, SessionStateInvalid
from yak_agent.workspace import SessionWorkspace
from yak_agent.yak_client import YakAuth


@dataclass
class Session:
    sid: str
    agent: Any
    workspace: SessionWorkspace
    owner: str = "local"
    workspace_id: str = ""
    auth: Any = None  # per-session YakAuth; refreshed in place on each request
    queue: asyncio.Queue = field(default_factory=asyncio.Queue)
    confirm_future: asyncio.Future | None = None
    pending_event: RequireUserConfirmEvent | None = None
    busy: bool = False
    persistent: bool = True


@dataclass(frozen=True)
class Identity:
    """Verified caller identity resolved from the frontend headers."""

    owner: str
    workspace_id: str
    auth: YakAuth
    persistent: bool


SESSIONS: dict[str, Session] = {}


def create_session(sid: str, identity: Identity, settings: Settings) -> Session:
    workspace = SessionWorkspace(persistence.SESSION_DIR / sid / "workspace")
    agent = build_agent(
        settings,
        workspace=workspace,
        auth=identity.auth,
        project_code=int(identity.workspace_id),
    )
    session = Session(
        sid=sid,
        agent=agent,
        workspace=workspace,
        owner=identity.owner,
        workspace_id=identity.workspace_id,
        auth=identity.auth,
        persistent=identity.persistent,
    )
    SESSIONS[sid] = session
    return session


def load_session(sid: str, identity: Identity, settings: Settings | None) -> Session:
    if sid in SESSIONS:
        session = SESSIONS[sid]
        if session.owner != identity.owner or session.workspace_id != identity.workspace_id:
            raise SessionForbidden()
        return session

    if not identity.persistent:
        # No immutable user key: the session was never written to disk, so it
        # cannot be restored after the process (or the in-memory entry) is gone.
        raise SessionNotFound()

    path = persistence.state_path(sid)
    if not path.exists():
        raise SessionNotFound()
    meta = persistence.read_meta(sid)
    if (
        meta.get("owner") != identity.owner
        or str(meta.get("workspace", "")) != identity.workspace_id
    ):
        raise SessionForbidden()
    try:
        state = AgentState.model_validate_json(path.read_text(encoding="utf-8"))
    except Exception as ex:  # noqa: BLE001
        raise SessionStateInvalid() from ex
    workspace = SessionWorkspace(persistence.SESSION_DIR / sid / "workspace")
    agent = build_agent(
        settings,
        state=state,
        workspace=workspace,
        auth=identity.auth,
        project_code=int(identity.workspace_id),
    )
    session = Session(
        sid=sid,
        agent=agent,
        workspace=workspace,
        owner=identity.owner,
        workspace_id=identity.workspace_id,
        auth=identity.auth,
    )
    SESSIONS[sid] = session
    return session


def remove_session(sid: str) -> None:
    SESSIONS.pop(sid, None)


def refresh_session_auth(session: Session, auth: YakAuth | None) -> None:
    """Refresh the session's per-user credentials in place.

    YakAuth is a mutable object captured by the tool closures at build time —
    updating `session_id` (and base_url) here makes the running tools use the
    fresh frontend session on the next call.
    """
    if auth is not None and session.auth is not None:
        session.auth.session_id = auth.session_id
        session.auth.base_url = auth.base_url
    elif auth is not None and session.auth is None:
        session.auth = auth
