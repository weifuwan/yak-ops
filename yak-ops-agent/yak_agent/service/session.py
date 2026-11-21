"""Session records, identity, and lifecycle."""

from __future__ import annotations

import asyncio
import time
import uuid
from collections import deque
from dataclasses import dataclass, field
from typing import Any

from agentscope.event import RequireUserConfirmEvent
from agentscope.state import AgentState

from yak_agent.agent.budget import ContextBudgetError
from yak_agent.agent.builder import build_agent
from yak_agent.config import Settings
from yak_agent.memory import get_memory
from yak_agent.service import persistence
from yak_agent.service.errors import (
    ContextBudgetExceeded,
    QueueItemNotFound,
    QueueItemStarted,
    SessionForbidden,
    SessionNotFound,
    SessionQueueFull,
    SessionStateInvalid,
    ToolConfigurationExceeded,
)
from yak_agent.service.events import EventLog
from yak_agent.tools.validation import ToolConfigurationError
from yak_agent.workspace import SessionWorkspace
from yak_agent.yak_client import YakAuth


@dataclass
class Turn:
    """One user message turn and its lifecycle on this process."""

    turn_id: str
    started_at: float
    status: str = "running"  # running | awaiting_confirmation | interrupting
    task: asyncio.Task | None = None
    interrupt_requested: bool = False
    reply_id: str = ""
    message: str = ""
    queue_id: str = ""  # queue item this turn was started from; "" for direct sends


def _idle_event() -> asyncio.Event:
    """Start in the idle state so a freshly created session can turn immediately."""
    event = asyncio.Event()
    event.set()
    return event


@dataclass
class QueuedMessage:
    """One message waiting for the current turn to finish.

    Queue items live in memory only: a process restart drops them, and each
    item carries the session credentials captured at enqueue time.
    """

    queue_id: str
    message: str
    created_at: float
    started: asyncio.Event = field(default_factory=asyncio.Event)
    attached: asyncio.Event = field(default_factory=asyncio.Event)
    cancelled: asyncio.Event = field(default_factory=asyncio.Event)
    cancel_reason: str = ""  # cancelled | session_deleted | start_failed
    turn_id: str = ""
    turn_cursor: int = 0  # replay start for this item's SSE, set when it starts


@dataclass
class Session:
    sid: str
    agent: Any
    workspace: SessionWorkspace
    owner: str = "local"
    workspace_id: str = ""
    auth: Any = None  # per-session YakAuth; refreshed in place on each request
    memory: Any = None  # YakReMeMiddleware shared by (owner, workspace)
    confirm_future: asyncio.Future | None = None
    pending_event: RequireUserConfirmEvent | None = None
    busy: bool = False
    persistent: bool = True
    turn: Turn | None = None
    event_log: EventLog = field(default_factory=EventLog)
    queue: deque[QueuedMessage] = field(default_factory=deque)
    queue_worker: asyncio.Task | None = None
    idle_event: asyncio.Event = field(default_factory=_idle_event)
    title: str = ""
    preview: str = ""
    usage: dict[str, int] = field(default_factory=dict)


@dataclass(frozen=True)
class Identity:
    """Verified caller identity resolved from the frontend headers."""

    owner: str
    workspace_id: str
    auth: YakAuth
    persistent: bool


SESSIONS: dict[str, Session] = {}


def _memory_scope_in_use(owner: str, workspace_id: str) -> bool:
    """Keep one ReMe runtime per scope while any loaded session references it."""
    return any(
        session.owner == owner and session.workspace_id == workspace_id
        for session in SESSIONS.values()
    )


def create_session(sid: str, identity: Identity, settings: Settings) -> Session:
    workspace = SessionWorkspace(persistence.SESSION_DIR / sid / "workspace")
    memory = (
        get_memory(identity.owner, identity.workspace_id, settings, _memory_scope_in_use)
        if identity.persistent
        else None
    )
    try:
        agent = build_agent(
            settings,
            state=AgentState(session_id=sid),
            workspace=workspace,
            auth=identity.auth,
            project_code=int(identity.workspace_id),
            memory=memory,
            audit_caller=identity.owner,
        )
    except ContextBudgetError as ex:
        raise ContextBudgetExceeded(str(ex)) from ex
    except ToolConfigurationError as ex:
        raise ToolConfigurationExceeded(str(ex)) from ex
    session = Session(
        sid=sid,
        agent=agent,
        workspace=workspace,
        owner=identity.owner,
        workspace_id=identity.workspace_id,
        auth=identity.auth,
        memory=memory,
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
    memory = (
        get_memory(identity.owner, identity.workspace_id, settings, _memory_scope_in_use)
        if identity.persistent and settings is not None
        else None
    )
    try:
        agent = build_agent(
            settings,
            state=state,
            workspace=workspace,
            auth=identity.auth,
            project_code=int(identity.workspace_id),
            memory=memory,
            audit_caller=identity.owner,
        )
    except ContextBudgetError as ex:
        raise ContextBudgetExceeded(str(ex)) from ex
    except ToolConfigurationError as ex:
        raise ToolConfigurationExceeded(str(ex)) from ex
    stored_usage = meta.get("usage") or {}
    session = Session(
        sid=sid,
        agent=agent,
        workspace=workspace,
        owner=identity.owner,
        workspace_id=identity.workspace_id,
        auth=identity.auth,
        memory=memory,
        title=str(meta.get("title", "")),
        preview=str(meta.get("preview", "")),
        usage={
            key: max(0, int(value))
            for key, value in stored_usage.items()
            if isinstance(value, (int, float))
        },
    )
    SESSIONS[sid] = session
    return session


def remove_session(sid: str) -> None:
    SESSIONS.pop(sid, None)


def queue_position(session: Session, queue_id: str) -> int:
    """1-based position of one queued item; 0 when it is no longer queued."""
    for position, item in enumerate(session.queue, start=1):
        if item.queue_id == queue_id:
            return position
    return 0


def enqueue_message(session: Session, message: str, max_len: int) -> QueuedMessage:
    """Append one message to the FIFO queue; the caller starts the SSE stream."""
    if max_len <= 0 or len(session.queue) >= max_len:
        raise SessionQueueFull()
    item = QueuedMessage(
        queue_id=uuid.uuid4().hex,
        message=message,
        created_at=time.time(),
    )
    session.queue.append(item)
    return item


def cancel_queued_message(session: Session, queue_id: str) -> QueuedMessage:
    """Cancel one not-yet-started item; raises when it is gone or already running."""
    for item in session.queue:
        if item.queue_id == queue_id:
            session.queue.remove(item)
            item.cancel_reason = "cancelled"
            item.cancelled.set()
            return item
    turn = session.turn
    if turn is not None and turn.queue_id == queue_id:
        raise QueueItemStarted()
    raise QueueItemNotFound()


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
