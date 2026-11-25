"""HTTP service exposing the data-sync agent to web frontends.

Endpoints
---------
POST   /agent/sessions                    create a session -> {sessionId}
POST   /agent/sessions/{sid}/messages     send a message -> SSE event stream
                                          (progress / text_delta / thinking_start / thinking_end /
                                           tool_start / tool_call_delta /
                                           tool_result_delta / tool_result /
                                           usage / reply_end /
                                           confirm_required / done / error;
                                           ?queue=true adds queue_waiting /
                                           queue_started / queue_cancelled)
POST   /agent/sessions/{sid}/confirm      approve or deny pending writes
                                          {approved: bool | [bool, ...]}
GET    /agent/sessions                    list persisted sessions
GET    /agent/sessions/{sid}/queue        list queued messages
DELETE /agent/sessions/{sid}/queue/{qid}  cancel one queued message
GET    /agent/memory                      memory overview for (user, workspace)
DELETE /agent/memory                      clear one workspace (?all=true: all)
GET    /health                            liveness probe

Design notes
------------
- One session = one AgentScope `Agent` + one event queue + one confirm future.
  The SSE stream stays open across a confirmation pause: the server emits
  `confirm_required`, waits for the frontend's POST /confirm, then continues
  pushing events on the same stream.
- Sessions are persisted as serialized `AgentState` JSON under
  $YAK_AGENT_SESSION_DIR (default ./sessions) after every turn, and lazily
  restored on the next message.
- Credentials live in the server process only; the frontend never sees the
  yak-ops token and cannot pass one.
- One process serves one session: SESSIONS, EventLog, the confirm future and
  the FIFO message queue are all in-process. Deploy with a single uvicorn
  worker and sticky sessions; a restart drops not-yet-started queue items.

Run:  python -m yak_agent.server             (defaults 127.0.0.1:8090)
"""

from __future__ import annotations

import asyncio
import json
import os
import time
import uuid
from collections.abc import AsyncIterator
from contextlib import asynccontextmanager
from datetime import datetime
from typing import Any

import uvicorn
from agentscope.state import AgentState
from fastapi import FastAPI, HTTPException, Query, Request
from pydantic import BaseModel
from sse_starlette.sse import EventSourceResponse

from yak_agent import audit
from yak_agent.api import messages
from yak_agent.api.deps import request_identity
from yak_agent.api.errors import session_error_handler
from yak_agent.api.sse import forward_event
from yak_agent.config import Settings
from yak_agent.memory import MEMORY, memory_overview
from yak_agent.service import persistence
from yak_agent.service.chat import INTERRUPT_SENTINEL, run_turn
from yak_agent.service.errors import (
    AgentSessionError,
    ConfirmationInvalid,
    ConfirmationNotPending,
    MemoryDisabled,
    QueueItemNotFound,
    SessionBusy,
    SessionForbidden,
    SessionNotFound,
    SessionStateInvalid,
)
from yak_agent.service.events import close as close_events
from yak_agent.service.events import emit, subscribe
from yak_agent.service.history import serialize_history
from yak_agent.service.retention import retention_loop
from yak_agent.service.session import (
    SESSIONS,
    Identity,
    QueuedMessage,
    Session,
    Turn,
    cancel_queued_message,
    enqueue_message,
    load_session,
    mark_deleting,
    queue_position,
    refresh_session_auth,
    remove_session,
    session_eviction_loop,
    unmark_deleting,
)
from yak_agent.service.session import (
    create_session as create_agent_session,
)
from yak_agent.tools.validation import validate_registry

_QUEUE_PREVIEW_CHARS = 120
_QUEUE_WAIT_HEARTBEAT_SECONDS = 15.0
_QUEUE_ATTACH_TIMEOUT_SECONDS = 5.0


@asynccontextmanager
async def _lifespan(_app: FastAPI):
    settings = _get_settings()
    tasks: list[asyncio.Task] = []
    if settings.session_retention_days > 0 or settings.session_max_mb > 0:
        tasks.append(asyncio.create_task(retention_loop(settings)))
    if settings.session_idle_seconds > 0:
        tasks.append(asyncio.create_task(session_eviction_loop(settings)))
    yield
    for task in tasks:
        task.cancel()
        try:
            await task
        except asyncio.CancelledError:
            pass
    await MEMORY.close_all()


app = FastAPI(title="yak-ops-agent", version="0.1.0", lifespan=_lifespan)
app.add_exception_handler(AgentSessionError, session_error_handler)
_settings: Settings | None = None


class MessageBody(BaseModel):
    message: str


class ConfirmBody(BaseModel):
    approved: bool | list[bool]


class InterruptBody(BaseModel):
    turnId: str | None = None


class RenameSessionBody(BaseModel):
    title: str


def _get_settings() -> Settings:
    global _settings
    if _settings is None:
        _settings = Settings.load()
    return _settings


async def _identify(request: Request, settings: Settings | None = None) -> Identity:
    """Resolve the caller off the event loop.

    Identity resolution performs two upstream HTTP calls; running them inline
    would freeze every session in this single-worker process.
    """
    return await asyncio.to_thread(
        request_identity,
        request,
        settings if settings is not None else _get_settings(),
    )


@app.get("/health")
async def health() -> dict[str, str]:
    return {"status": "ok"}


@app.post("/agent/sessions")
async def create_session(request: Request) -> dict[str, str]:
    settings = _get_settings()
    identity = await _identify(request, settings)
    sid = uuid.uuid4().hex
    session = create_agent_session(sid, identity, settings)
    persistence.persist(session)
    return {"sessionId": sid}


@app.get("/agent/sessions")
async def list_sessions(request: Request) -> list[dict[str, Any]]:
    identity = await _identify(request, _get_settings())
    if not identity.persistent:
        return []  # legacy identities have no on-disk sessions
    items = []
    if persistence.SESSION_DIR.exists():
        for path in sorted(
            persistence.SESSION_DIR.glob("*.json"),
            key=lambda p: p.stat().st_mtime,
            reverse=True,
        ):
            if path.name.endswith(".meta.json"):
                continue
            meta = persistence.read_meta(path.stem)
            if (
                meta.get("owner") != identity.owner
                or str(meta.get("workspace", "")) != identity.workspace_id
            ):
                continue  # callers only see their own sessions in this workspace
            items.append(
                {
                    "sessionId": path.stem,
                    "title": meta.get("title") or f"历史会话 · {path.stem[:6]}",
                    "preview": meta.get("preview", ""),
                    "usage": meta.get("usage", {}),
                    "updatedAt": path.stat().st_mtime,
                }
            )
    return items


def _conversation_title(text: str) -> tuple[str, str]:
    clean = " ".join(text.split())
    if not clean:
        return "新会话", ""
    return clean[:40], clean[:120]


@app.get("/agent/memory")
async def get_memory(request: Request) -> dict[str, Any]:
    """Summarize this user's memory in the current workspace (no content)."""
    settings = _get_settings()
    identity = await _identify(request, settings)
    if not settings.memory_enabled:
        raise MemoryDisabled()
    return {
        "enabled": True,
        "workspaceId": identity.workspace_id,
        **memory_overview(settings, identity.owner, identity.workspace_id),
    }


@app.delete("/agent/memory")
async def clear_memory(
    request: Request,
    all_scopes: bool = Query(False, alias="all"),
) -> dict[str, Any]:
    """Clear this user's memory for one workspace, or all workspaces."""
    settings = _get_settings()
    identity = await _identify(request, settings)

    def in_use(owner: str, workspace_id: str) -> bool:
        return any(
            session.owner == owner and session.workspace_id == workspace_id
            for session in SESSIONS.values()
        )

    for session in SESSIONS.values():
        if session.owner != identity.owner or not session.busy:
            continue
        if all_scopes or session.workspace_id == identity.workspace_id:
            raise SessionBusy()
    if all_scopes:
        scopes = await MEMORY.clear_owner(identity.owner, settings, in_use)
    else:
        scopes = await MEMORY.clear_scope(
            identity.owner,
            identity.workspace_id,
            settings,
            in_use,
        )
    audit.record_tool_call(
        "memory_clear",
        {
            "caller": identity.owner,
            "workspace": "*" if all_scopes else identity.workspace_id,
            "scopes": scopes,
        },
        True,
        0,
        None,
    )
    return {"deleted": True, "scopes": scopes}


async def _begin_turn(
    session: Session,
    message: str,
    settings: Settings,
    queue_id: str = "",
) -> tuple[Turn, int]:
    """Claim the session, reset the event log, and start the turn task.

    busy/turn are set before any await so a concurrent send or the retention
    loop never observes an idle window between the claim and the reset. Returns
    `(turn, cursor)`; the cursor is the replay start for the caller's SSE.
    """
    if session.deleting:
        # A delete cleared the queue and is about to remove the files; never
        # start a turn that would persist them back.
        raise SessionNotFound()
    session.last_active_at = time.time()
    turn = Turn(
        turn_id=uuid.uuid4().hex,
        started_at=time.time(),
        message=message,
        queue_id=queue_id,
    )
    session.busy = True
    session.turn = turn
    session.idle_event.clear()
    try:
        await session.event_log.reset()
        if not session.title:
            session.title, session.preview = _conversation_title(message)
        turn_cursor = session.event_log.next_seq - 1
        if queue_id:
            await emit(
                session,
                {"type": "queue_started", "queueId": queue_id, "turnId": turn.turn_id},
            )
        persistence.persist(session)
    except BaseException:
        session.busy = False
        session.turn = None
        session.idle_event.set()
        raise
    turn.task = asyncio.create_task(
        run_turn(session, message, forward_event, settings.confirm_timeout_seconds, turn)
    )
    # Yield one loop tick so run_turn has entered its try block. A cancel
    # arriving right after task creation would otherwise abort the coroutine
    # before its `finally`, leaving the session claimed busy forever.
    await asyncio.sleep(0)
    return turn, turn_cursor


def _queue_waiting_frame(item: QueuedMessage, position: int) -> dict[str, Any]:
    return {
        "type": "queue_waiting",
        "queueId": item.queue_id,
        "position": position,
        "messagePreview": item.message[:_QUEUE_PREVIEW_CHARS],
    }


async def _wait_queue_signal(item: QueuedMessage, timeout: float) -> None:
    """Wake on started/cancelled, or return after `timeout` for the heartbeat."""
    started = asyncio.ensure_future(item.started.wait())
    cancelled = asyncio.ensure_future(item.cancelled.wait())
    try:
        await asyncio.wait(
            {started, cancelled},
            timeout=timeout,
            return_when=asyncio.FIRST_COMPLETED,
        )
    finally:
        started.cancel()
        cancelled.cancel()
        await asyncio.gather(started, cancelled, return_exceptions=True)


async def _queue_stream(
    session: Session,
    item: QueuedMessage,
    initial_position: int,
) -> AsyncIterator[dict[str, str]]:
    """SSE for one queued message: position heartbeats, then the turn events.

    Waiting events are request-local (not in the EventLog); once the item starts
    the stream switches to the full log replay, and the first delivered event
    marks the client as attached so the worker may start the next item (and
    reset the log) without wiping events this client has not seen yet. The
    initial position is captured at enqueue time — by the time the generator
    first runs, the worker may already have started the item ahead of this one.
    """
    yield _sse_frame(_queue_waiting_frame(item, initial_position))
    while True:
        await _wait_queue_signal(item, _QUEUE_WAIT_HEARTBEAT_SECONDS)
        if item.cancelled.is_set():
            yield _sse_frame(
                {
                    "type": "queue_cancelled",
                    "queueId": item.queue_id,
                    "reason": item.cancel_reason or "cancelled",
                }
            )
            return
        if item.started.is_set():
            break
        # An item the worker already popped but has not marked started yet is
        # next in line; position 0 would be meaningless to the frontend.
        position = queue_position(session, item.queue_id) or 1
        yield _sse_frame(_queue_waiting_frame(item, position))
    attached = False
    async for event in subscribe(session, item.turn_cursor):
        turn_id = event.get("turnId")
        if turn_id and turn_id != item.turn_id:
            # The worker moved on (attach timed out, or this client was very
            # slow) and reset the log for a later turn. Never show another
            # turn's events as this message's stream.
            return
        if not attached:
            attached = True
            item.attached.set()
        yield _sse_frame(event)


async def _run_queue_worker(session: Session, settings: Settings) -> None:
    """Execute queued items strictly one at a time, in FIFO order.

    Items stay in `session.queue` until the worker has passed the idle wait, so
    a direct send arriving in between still sees a non-empty queue (409). The
    pop and the synchronous claim inside `_begin_turn` are adjacent statements
    with no await in between, which makes the claim atomic in the event loop.
    """
    worker = asyncio.current_task()
    try:
        while session.queue:
            await session.idle_event.wait()
            if not session.queue:
                break
            item = session.queue.popleft()
            if item.cancelled.is_set():
                continue
            try:
                turn, cursor = await _begin_turn(
                    session,
                    item.message,
                    settings,
                    queue_id=item.queue_id,
                )
            except Exception as ex:  # noqa: BLE001 - notify the waiting stream, keep draining
                item.cancel_reason = "start_failed"
                item.cancelled.set()
                audit.record_tool_call(
                    "session_queue_start",
                    {
                        "session": session.sid,
                        "caller": session.owner,
                        "queueId": item.queue_id,
                        "outcome": "start_failed",
                    },
                    False,
                    0,
                    f"{type(ex).__name__}: {ex}",
                )
                continue
            item.turn_id = turn.turn_id
            item.turn_cursor = cursor
            item.started.set()
            audit.record_tool_call(
                "session_queue_start",
                {
                    "session": session.sid,
                    "caller": session.owner,
                    "queueId": item.queue_id,
                    "turnId": turn.turn_id,
                    "outcome": "started",
                },
                True,
                0,
                None,
            )
            try:
                await asyncio.wait_for(
                    item.attached.wait(),
                    timeout=_QUEUE_ATTACH_TIMEOUT_SECONDS,
                )
            except TimeoutError:
                pass  # slow or gone client: never roll the queue back for it
            if turn.task is not None:
                try:
                    await turn.task
                except asyncio.CancelledError:
                    pass  # interrupted turn: run_turn already emitted its `done`
                except Exception:  # noqa: BLE001 - run_turn already surfaced the error
                    pass
    finally:
        if worker is not None and session.queue_worker is worker:
            session.queue_worker = None


def _ensure_queue_worker(session: Session, settings: Settings) -> None:
    if session.queue_worker is not None and not session.queue_worker.done():
        return
    session.queue_worker = asyncio.create_task(_run_queue_worker(session, settings))


@app.post("/agent/sessions/{sid}/messages")
async def send_message(
    sid: str,
    body: MessageBody,
    request: Request,
    queue: bool = False,
) -> EventSourceResponse:
    settings = _get_settings()
    identity = await _identify(request, settings)
    session = load_session(sid, identity, settings)
    refresh_session_auth(session, identity.auth)
    if session.busy or session.queue:
        # Strict FIFO: while items are queued, a newer message must not jump
        # ahead of them even when the current turn has just finished.
        if not queue or settings.session_queue_max <= 0:
            raise SessionBusy()
        # enqueue_message is synchronous, so the capacity check and append are
        # atomic against concurrent sends.
        item = enqueue_message(session, body.message, settings.session_queue_max)
        if not session.title:
            session.title, session.preview = _conversation_title(body.message)
        persistence.persist(session)
        position = queue_position(session, item.queue_id)
        _ensure_queue_worker(session, settings)
        audit.record_tool_call(
            "session_queue_enqueue",
            {
                "session": sid,
                "caller": identity.owner,
                "queueId": item.queue_id,
                "position": position,
            },
            True,
            0,
            None,
        )
        return EventSourceResponse(_queue_stream(session, item, position))

    turn, turn_cursor = await _begin_turn(session, body.message, settings)

    async def event_stream():
        async for item in subscribe(session, turn_cursor):
            yield _sse_frame(item)

    return EventSourceResponse(event_stream())


@app.get("/agent/sessions/{sid}/stream")
async def resume_stream(sid: str, request: Request, after: int = 0) -> EventSourceResponse:
    """Resume an SSE stream: replay retained events, then follow live ones."""
    identity = await _identify(request, _get_settings())
    session = load_session(sid, identity, _get_settings())
    refresh_session_auth(session, identity.auth)
    cursor = after
    if cursor <= 0:
        last_event_id = request.headers.get("last-event-id", "")
        if last_event_id.isdigit():
            cursor = int(last_event_id)

    async def event_stream():
        async for item in subscribe(session, cursor):
            yield _sse_frame(item)

    return EventSourceResponse(event_stream())


def _sse_frame(item: dict[str, Any]) -> dict[str, str]:
    frame = {
        "event": item["type"],
        "data": json.dumps(item, ensure_ascii=False),
    }
    if "seq" in item:
        frame["id"] = str(item["seq"])
    return frame


@app.get("/agent/sessions/{sid}/queue")
async def get_session_queue(sid: str, request: Request) -> dict[str, Any]:
    """Queued messages of one session; disk-only sessions have none in-process."""
    identity = await _identify(request, _get_settings())
    session = SESSIONS.get(sid)
    if session is None:
        # The queue lives in memory only: after a restart every session is
        # disk-only and has no queue, but the caller still needs an ownership
        # check before learning that.
        _authorize_meta(sid, identity)
        return {"items": [], "max": _get_settings().session_queue_max}
    if session.owner != identity.owner or session.workspace_id != identity.workspace_id:
        raise SessionForbidden()
    return {
        "items": [
            {
                "queueId": item.queue_id,
                "position": position,
                "createdAt": _iso(item.created_at),
                "messagePreview": item.message[:_QUEUE_PREVIEW_CHARS],
            }
            for position, item in enumerate(session.queue, start=1)
        ],
        "max": _get_settings().session_queue_max,
    }


@app.delete("/agent/sessions/{sid}/queue/{queue_id}")
async def cancel_queue_item(sid: str, queue_id: str, request: Request) -> dict[str, bool]:
    identity = await _identify(request, _get_settings())
    session = SESSIONS.get(sid)
    if session is not None:
        if session.owner != identity.owner or session.workspace_id != identity.workspace_id:
            raise SessionForbidden()
    else:
        # Deliberately NOT load_session: loading a disk-only session just to
        # find an empty queue would keep it resident and immune to retention.
        _authorize_meta(sid, identity)
        raise QueueItemNotFound()
    cancel_queued_message(session, queue_id)
    audit.record_tool_call(
        "session_queue_cancel",
        {
            "session": sid,
            "caller": identity.owner,
            "queueId": queue_id,
        },
        True,
        0,
        None,
    )
    return {"cancelled": True}


@app.post("/agent/sessions/{sid}/confirm")
async def confirm(sid: str, body: ConfirmBody, request: Request) -> dict[str, bool]:
    identity = await _identify(request, _get_settings())
    session = load_session(sid, identity, _get_settings())
    refresh_session_auth(session, identity.auth)
    pending = session.pending_event
    if pending is None or session.confirm_future is None or session.confirm_future.done():
        raise ConfirmationNotPending()
    tool_calls = pending.tool_calls
    if not tool_calls:
        raise ConfirmationNotPending()
    if isinstance(body.approved, bool):
        flags = [body.approved] * len(tool_calls)
    else:
        if len(body.approved) != len(tool_calls):
            raise ConfirmationInvalid(messages.CONFIRMATION_COUNT_MISMATCH)
        flags = list(body.approved)
    for tool_call, confirmed in zip(tool_calls, flags, strict=True):
        audit.record_tool_call(
            "session_confirm",
            {
                "session": sid,
                "caller": identity.owner,
                "replyId": pending.reply_id,
                "toolCallId": tool_call.id,
                "tool": tool_call.name,
                "confirmed": confirmed,
            },
            True,
            0,
            None,
        )
    session.confirm_future.set_result(flags)
    return {"accepted": True}


def _iso(timestamp: float | None) -> str | None:
    if not timestamp:
        return None
    return datetime.fromtimestamp(timestamp).astimezone().isoformat(timespec="seconds")


def _updated_at(sid: str) -> float | None:
    for path in (persistence.state_path(sid), persistence.meta_path(sid)):
        if path.exists():
            return path.stat().st_mtime
    return None


def _authorize_meta(sid: str, identity: Any) -> dict[str, Any]:
    meta = persistence.read_meta(sid)
    if not meta:
        raise SessionNotFound()
    if (
        meta.get("owner") != identity.owner
        or str(meta.get("workspace", "")) != identity.workspace_id
    ):
        raise SessionForbidden()
    return meta


@app.get("/agent/sessions/{sid}")
async def session_status(sid: str, request: Request) -> dict[str, Any]:
    """Current turn status, restored from memory or the persisted meta."""
    identity = await _identify(request, _get_settings())
    session = SESSIONS.get(sid)
    if session is not None:
        if session.owner != identity.owner or session.workspace_id != identity.workspace_id:
            raise SessionForbidden()
        meta = persistence.read_meta(sid)
        turn = session.turn
        pending = session.pending_event
        return {
            "sessionId": sid,
            "state": turn.status if turn else ("queued" if session.queue else "idle"),
            "busy": session.busy,
            "queued": len(session.queue),
            "stale": False,
            "turnId": turn.turn_id if turn else None,
            "replyId": turn.reply_id if turn and turn.reply_id else None,
            "startedAt": _iso(turn.started_at) if turn else None,
            "activeTurnMessage": turn.message if turn else None,
            "pendingConfirmation": (
                {
                    "replyId": pending.reply_id,
                    "toolCalls": [
                        {"id": tc.id, "name": tc.name, "input": getattr(tc, "input", "")}
                        for tc in pending.tool_calls
                    ],
                }
                if pending is not None
                else None
            ),
            "title": session.title or meta.get("title", ""),
            "preview": session.preview or meta.get("preview", ""),
            "updatedAt": _updated_at(sid),
            "usage": session.usage,
        }

    meta = _authorize_meta(sid, identity)
    active = meta.get("activeTurn") or {}
    return {
        "sessionId": sid,
        "state": "stale" if active else "idle",
        "busy": False,
        "queued": 0,
        "stale": bool(active),
        "turnId": active.get("turnId"),
        "replyId": None,
        "startedAt": _iso(active.get("startedAt")),
        "activeTurnMessage": active.get("message"),
        "pendingConfirmation": None,
        "title": meta.get("title", ""),
        "preview": meta.get("preview", ""),
        "updatedAt": _updated_at(sid),
        "usage": meta.get("usage", {}),
    }


@app.patch("/agent/sessions/{sid}")
async def rename_session(
    sid: str,
    body: RenameSessionBody,
    request: Request,
) -> dict[str, str]:
    """Rename a session without constructing an Agent for disk-only sessions."""
    identity = await _identify(request, _get_settings())
    title = " ".join(body.title.split()).strip()
    if not title:
        raise HTTPException(status_code=400, detail="会话名称不能为空")
    title = title[:40]

    session = SESSIONS.get(sid)
    if session is not None:
        if session.owner != identity.owner or session.workspace_id != identity.workspace_id:
            raise SessionForbidden()
        session.title = title
        meta = persistence.read_meta(sid)
    else:
        meta = _authorize_meta(sid, identity)

    meta.update(
        {
            "owner": identity.owner,
            "workspace": identity.workspace_id,
            "title": title,
        }
    )
    persistence.write_atomic(
        persistence.meta_path(sid),
        json.dumps(meta, ensure_ascii=False),
    )
    return {"sessionId": sid, "title": title}


@app.get("/agent/sessions/{sid}/messages")
async def session_messages(sid: str, request: Request, limit: int = 0) -> dict[str, Any]:
    """Return the persisted conversation history for one session."""
    identity = await _identify(request, _get_settings())
    session = SESSIONS.get(sid)
    if session is not None:
        if session.owner != identity.owner or session.workspace_id != identity.workspace_id:
            raise SessionForbidden()
        state = getattr(session.agent, "state", None)
        if state is None:
            raise SessionStateInvalid()
    else:
        _authorize_meta(sid, identity)
        path = persistence.state_path(sid)
        if not path.exists():
            raise SessionNotFound()
        try:
            state = AgentState.model_validate_json(path.read_text(encoding="utf-8"))
        except Exception as ex:  # noqa: BLE001
            raise SessionStateInvalid() from ex
    settings = _get_settings()
    effective = min(limit or settings.history_max_messages, 500)
    result = serialize_history(state, max(effective, 1), settings.history_tool_chars)
    usage = session.usage if session is not None else persistence.read_meta(sid).get("usage", {})
    return {"sessionId": sid, "usage": usage, **result}


@app.post("/agent/sessions/{sid}/interrupt", status_code=202)
async def interrupt(sid: str, body: InterruptBody, request: Request) -> dict[str, Any]:
    """Stop the current turn; a stale turnId never stops a newer turn."""
    identity = await _identify(request, _get_settings())
    session = load_session(sid, identity, _get_settings())
    refresh_session_auth(session, identity.auth)
    turn = session.turn
    if turn is None or (turn.task is not None and turn.task.done()):
        return {
            "accepted": False,
            "state": "idle",
            "turnId": turn.turn_id if turn else None,
        }
    if body.turnId and body.turnId != turn.turn_id:
        return {"accepted": False, "state": turn.status, "turnId": turn.turn_id}
    if turn.status == "interrupting":
        return {"accepted": True, "state": turn.status, "turnId": turn.turn_id}

    parked = (
        session.pending_event is not None
        and session.confirm_future is not None
        and not session.confirm_future.done()
    )
    turn.interrupt_requested = True
    turn.status = "interrupting"
    audit.record_tool_call(
        "session_interrupt",
        {
            "session": sid,
            "caller": identity.owner,
            "turnId": turn.turn_id,
            "replyId": session.pending_event.reply_id if parked else "",
            "scope": "parked" if parked else "running",
            "toolCalls": len(session.pending_event.tool_calls) if parked else 0,
        },
        True,
        0,
        None,
    )
    if parked:
        session.confirm_future.set_result(INTERRUPT_SENTINEL)
    else:
        turn.task.cancel()
    return {"accepted": True, "state": "interrupting", "turnId": turn.turn_id}


@app.delete("/agent/sessions/{sid}")
async def delete_session(sid: str, request: Request) -> dict[str, bool]:
    identity = await _identify(request, _get_settings())
    session = load_session(sid, identity, _get_settings())  # ownership check before removal
    refresh_session_auth(session, identity.auth)
    if session.deleting:
        return {"deleted": True}
    if session.busy:
        # The worker would keep running and call persist() after the state and
        # workspace files are gone. Make the caller stop/wait for it first.
        raise SessionBusy()
    # Mark the session synchronously, before the first await: any request that
    # loaded it earlier must not start a turn while the delete is in flight,
    # and load_session refuses to rebuild it from disk meanwhile.
    session.deleting = True
    mark_deleting(sid)
    # Cancel queued items first so their streams close with queue_cancelled
    # instead of waiting on a session that is about to disappear.
    for item in list(session.queue):
        item.cancel_reason = "session_deleted"
        item.cancelled.set()
    session.queue.clear()
    if session.queue_worker is not None:
        session.queue_worker.cancel()
        session.queue_worker = None
    try:
        if session.memory is not None:
            await session.memory.flush()
            state = getattr(session.agent, "state", None)
            session.memory.delete_session_transcript(
                str(getattr(state, "session_id", "") or sid),
            )
        await close_events(session, "会话已删除")
    finally:
        # Always remove the registry entry and files, even if a flush fails;
        # the marker must not outlive the delete either.
        try:
            remove_session(sid)
            persistence.delete_files(sid)
        finally:
            unmark_deleting(sid)
    return {"deleted": True}


def main() -> None:
    # Fail before binding the port when operations/allowlist or reserved tool
    # names are invalid. Per-session assembly performs the same check.
    validate_registry()
    host = os.environ.get("YAK_AGENT_HOST", "127.0.0.1")
    port = int(os.environ.get("YAK_AGENT_PORT", "8090"))
    uvicorn.run(app, host=host, port=port, log_level="info")


if __name__ == "__main__":
    main()
