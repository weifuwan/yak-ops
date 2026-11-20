"""HTTP service exposing the data-sync agent to web frontends.

Endpoints
---------
POST   /agent/sessions                    create a session -> {sessionId}
POST   /agent/sessions/{sid}/messages     send a message -> SSE event stream
                                          (progress / text_delta / thinking_start / thinking_end /
                                           tool_start / tool_call_delta /
                                           tool_result_delta / tool_result /
                                           usage / reply_end /
                                           confirm_required / done / error)
POST   /agent/sessions/{sid}/confirm      approve or deny pending writes
                                          {approved: bool | [bool, ...]}
GET    /agent/sessions                    list persisted sessions
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

Run:  python -m yak_agent.server             (defaults 127.0.0.1:8090)
"""

from __future__ import annotations

import asyncio
import json
import os
import time
import uuid
from datetime import datetime
from typing import Any

import uvicorn
from agentscope.state import AgentState
from fastapi import FastAPI, HTTPException, Request
from pydantic import BaseModel
from sse_starlette.sse import EventSourceResponse

from yak_agent import audit
from yak_agent.api import messages
from yak_agent.api.deps import request_identity
from yak_agent.api.errors import session_error_handler
from yak_agent.api.sse import forward_event
from yak_agent.config import Settings
from yak_agent.service import persistence
from yak_agent.service.chat import INTERRUPT_SENTINEL, run_turn
from yak_agent.service.errors import (
    AgentSessionError,
    ConfirmationInvalid,
    ConfirmationNotPending,
    SessionBusy,
    SessionForbidden,
    SessionNotFound,
    SessionStateInvalid,
)
from yak_agent.service.events import close as close_events
from yak_agent.service.events import subscribe
from yak_agent.service.history import serialize_history
from yak_agent.service.session import (
    SESSIONS,
    Turn,
    load_session,
    refresh_session_auth,
    remove_session,
)
from yak_agent.service.session import (
    create_session as create_agent_session,
)

app = FastAPI(title="yak-ops-agent", version="0.1.0")
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


@app.get("/health")
async def health() -> dict[str, str]:
    return {"status": "ok"}


@app.post("/agent/sessions")
async def create_session(request: Request) -> dict[str, str]:
    settings = _get_settings()
    identity = request_identity(request, settings)
    sid = uuid.uuid4().hex
    session = create_agent_session(sid, identity, settings)
    persistence.persist(session)
    return {"sessionId": sid}


@app.get("/agent/sessions")
async def list_sessions(request: Request) -> list[dict[str, Any]]:
    identity = request_identity(request, _get_settings())
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


@app.post("/agent/sessions/{sid}/messages")
async def send_message(sid: str, body: MessageBody, request: Request) -> EventSourceResponse:
    identity = request_identity(request, _get_settings())
    session = load_session(sid, identity, _get_settings())
    refresh_session_auth(session, identity.auth)
    if session.busy:
        raise SessionBusy()
    await session.event_log.reset()
    turn_cursor = session.event_log.next_seq - 1
    if not session.title:
        session.title, session.preview = _conversation_title(body.message)
    turn = Turn(
        turn_id=uuid.uuid4().hex,
        started_at=time.time(),
        message=body.message,
    )
    session.turn = turn
    session.busy = True
    persistence.persist(session)
    turn.task = asyncio.create_task(
        run_turn(
            session,
            body.message,
            forward_event,
            _get_settings().confirm_timeout_seconds,
            turn,
        )
    )

    async def event_stream():
        async for item in subscribe(session, turn_cursor):
            yield _sse_frame(item)

    return EventSourceResponse(event_stream())


@app.get("/agent/sessions/{sid}/stream")
async def resume_stream(sid: str, request: Request, after: int = 0) -> EventSourceResponse:
    """Resume an SSE stream: replay retained events, then follow live ones."""
    identity = request_identity(request, _get_settings())
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


@app.post("/agent/sessions/{sid}/confirm")
async def confirm(sid: str, body: ConfirmBody, request: Request) -> dict[str, bool]:
    identity = request_identity(request, _get_settings())
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
    identity = request_identity(request, _get_settings())
    session = SESSIONS.get(sid)
    if session is not None:
        if session.owner != identity.owner or session.workspace_id != identity.workspace_id:
            raise SessionForbidden()
        meta = persistence.read_meta(sid)
        turn = session.turn
        pending = session.pending_event
        return {
            "sessionId": sid,
            "state": turn.status if turn else "idle",
            "busy": session.busy,
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
    identity = request_identity(request, _get_settings())
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
    persistence.meta_path(sid).write_text(
        json.dumps(meta, ensure_ascii=False),
        encoding="utf-8",
    )
    return {"sessionId": sid, "title": title}


@app.get("/agent/sessions/{sid}/messages")
async def session_messages(sid: str, request: Request, limit: int = 0) -> dict[str, Any]:
    """Return the persisted conversation history for one session."""
    identity = request_identity(request, _get_settings())
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
    identity = request_identity(request, _get_settings())
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
    identity = request_identity(request, _get_settings())
    session = load_session(sid, identity, _get_settings())  # ownership check before removal
    refresh_session_auth(session, identity.auth)
    if session.busy:
        # The worker would keep running and call persist() after the state and
        # workspace files are gone. Make the caller stop/wait for it first.
        raise SessionBusy()
    await close_events(session, "会话已删除")
    remove_session(sid)
    persistence.delete_files(sid)
    del session
    return {"deleted": True}


def main() -> None:
    host = os.environ.get("YAK_AGENT_HOST", "127.0.0.1")
    port = int(os.environ.get("YAK_AGENT_PORT", "8090"))
    uvicorn.run(app, host=host, port=port, log_level="info")


if __name__ == "__main__":
    main()
