"""HTTP service exposing the data-sync agent to web frontends.

Endpoints
---------
POST   /agent/sessions                    create a session -> {sessionId}
POST   /agent/sessions/{sid}/messages     send a message -> SSE event stream
                                          (text_delta / tool_start / tool_result /
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

Run:  python -m yak_agent.server            (defaults 127.0.0.1:8090)
"""

from __future__ import annotations

import asyncio
import json
import os
import uuid
from dataclasses import dataclass, field
from pathlib import Path
from typing import Any

import uvicorn
from fastapi import FastAPI, HTTPException, Request
from pydantic import BaseModel
from sse_starlette.sse import EventSourceResponse

from agentscope.event import (
    ConfirmResult,
    EventType,
    RequireUserConfirmEvent,
    UserConfirmResultEvent,
)
from agentscope.message import UserMsg
from agentscope.state import AgentState

import hashlib

from yak_agent.app import build_agent
from yak_agent import audit
from yak_agent.config import Settings
from yak_agent.workspace import SessionWorkspace
from yak_agent.yak_client import YakApiError, YakAuth, yak_get

_SESSION_DIR = Path(os.environ.get("YAK_AGENT_SESSION_DIR", Path(__file__).resolve().parent.parent / "sessions"))
_FORWARD_EVENTS = {
    EventType.TOOL_CALL_START: "tool_start",
    EventType.TOOL_RESULT_END: "tool_result",
}


@dataclass
class Session:
    sid: str
    agent: Any
    workspace: SessionWorkspace
    owner: str = "local"
    auth: Any = None  # per-session YakAuth; refreshed in place on each request
    queue: asyncio.Queue = field(default_factory=asyncio.Queue)
    confirm_future: asyncio.Future | None = None
    pending_event: RequireUserConfirmEvent | None = None
    busy: bool = False


app = FastAPI(title="yak-ops-agent", version="0.1.0")
_sessions: dict[str, Session] = {}
_settings: Settings | None = None


class MessageBody(BaseModel):
    message: str


class ConfirmBody(BaseModel):
    approved: bool | list[bool]


def _get_settings() -> Settings:
    global _settings
    if _settings is None:
        _settings = Settings.load()
    return _settings


def _state_path(sid: str) -> Path:
    _SESSION_DIR.mkdir(parents=True, exist_ok=True)
    return _SESSION_DIR / f"{sid}.json"


def _meta_path(sid: str) -> Path:
    _SESSION_DIR.mkdir(parents=True, exist_ok=True)
    return _SESSION_DIR / f"{sid}.meta.json"


def _persist(session: Session) -> None:
    try:
        state = getattr(session.agent, "state", None)
        if state is not None:
            _state_path(session.sid).write_text(state.model_dump_json(), encoding="utf-8")
        _meta_path(session.sid).write_text(
            json.dumps({"owner": session.owner}, ensure_ascii=False), encoding="utf-8"
        )
    except Exception:  # noqa: BLE001 - persistence must never break a turn
        pass


def _service_token() -> str:
    return os.environ.get("YAK_AGENT_SERVICE_TOKEN", "").strip()


def _service_guard(request: Request) -> None:
    """Service-level access control (layer 1 of the two-layer auth).

    With YAK_AGENT_SERVICE_TOKEN set, callers must present
    `Authorization: Bearer <token>` (401 otherwise). Without a token the
    service is loopback-only by construction — non-local clients are refused
    (401) so an accidental 0.0.0.0 binding cannot silently expose writes.
    """
    token = _service_token()
    if token:
        if request.headers.get("Authorization", "") != f"Bearer {token}":
            raise HTTPException(status_code=401, detail="unauthorized: missing or invalid service token")
        return
    client_host = request.client.host if request.client else "?"
    if client_host not in ("127.0.0.1", "::1", "localhost"):
        raise HTTPException(status_code=401, detail="unauthorized: set YAK_AGENT_SERVICE_TOKEN for non-local callers")


def _identity(request: Request, settings: Settings) -> tuple[str, YakAuth]:
    """Resolve (owner key, session auth) from the frontend headers.

    X-Yak-Session-Id carries the caller's yak-ops sessionId cookie. The owner
    key is derived from the sessionId itself — **no user id is accepted from
    the frontend**; the real identity is verified by the platform when the
    session is validated (see _verify_session_project) and lives on the
    platform side, which is also what makes the audit trail traceable.

    Layer 1 (service guard) runs first: service token or loopback-only.
    Without the header the service credentials are used (CLI / single-tenant
    mode).
    """
    _service_guard(request)
    session_id = request.headers.get("X-Yak-Session-Id", "").strip()
    auth = YakAuth(base_url=settings.yak_ops_base_url, session_id=session_id) if session_id else None
    if session_id:
        return "session:" + hashlib.sha256(session_id.encode()).hexdigest()[:12], auth
    return "local", auth or YakAuth(
        base_url=settings.yak_ops_base_url,
        username=settings.yak_ops_username,
        password=settings.yak_ops_password,
    )


def _verify_session_project(auth: YakAuth, project_code: int) -> None:
    """Platform-side session and project-permission check — the same
    interceptor semantics as the original backend: the sessionId is validated
    by the platform on this call, and the configured project must be granted
    to the session's user, otherwise the session is refused."""
    try:
        data = yak_get(auth, "/projects", {"pageNo": 1, "pageSize": 100})
    except YakApiError as ex:
        if ex.status_code == 401:
            raise HTTPException(status_code=401, detail="yak-ops session invalid or expired; re-login and retry") from ex
        raise HTTPException(status_code=502, detail=f"yak-ops session verification failed: {ex}") from ex
    codes = {str(item.get("code")) for item in ((data or {}).get("totalList") or [])}
    if str(project_code) not in codes:
        raise HTTPException(status_code=403, detail="the configured project is not granted to this user")


def _refresh_session_auth(session: Session, auth: YakAuth | None) -> None:
    """Refresh the session's per-user credentials in place.

    YakAuth is a mutable object captured by the tool closures at build time —
    updating `session_id` (and base_url) here makes the running tools use the
    fresh frontend session on the next call. No-op without user headers
    (CLI / single-tenant mode keeps the service credentials).
    """
    if auth is not None and session.auth is not None:
        session.auth.session_id = auth.session_id
        session.auth.base_url = auth.base_url
    elif auth is not None and session.auth is None:
        session.auth = auth


def _load_session(sid: str, caller: str) -> Session:
    if sid in _sessions:
        session = _sessions[sid]
    else:
        path = _state_path(sid)
        if not path.exists():
            raise HTTPException(status_code=404, detail=f"session {sid} not found")
        settings = _get_settings()
        try:
            state = AgentState.model_validate_json(path.read_text(encoding="utf-8"))
        except Exception as ex:  # noqa: BLE001
            raise HTTPException(status_code=422, detail=f"session state unreadable: {ex}") from ex
        workspace = SessionWorkspace(_SESSION_DIR / sid / "workspace")
        agent = build_agent(settings, state=state, workspace=workspace)
        owner = "local"
        meta_path = _meta_path(sid)
        if meta_path.exists():
            try:
                owner = json.loads(meta_path.read_text(encoding="utf-8")).get("owner", "local")
            except Exception:  # noqa: BLE001
                owner = "local"
        if owner != caller:
            raise HTTPException(status_code=403, detail="session belongs to another caller")
        session = Session(sid=sid, agent=agent, workspace=workspace, owner=owner)
        _sessions[sid] = session
        return session
    if session.owner != caller:
        raise HTTPException(status_code=403, detail="session belongs to another caller")
    return session


async def _forward(session: Session, event: Any) -> None:
    if event.type == EventType.TEXT_BLOCK_DELTA:
        delta = getattr(event, "delta", "")
        if delta:
            await session.queue.put({"type": "text_delta", "delta": delta})
        return
    mapped = _FORWARD_EVENTS.get(event.type)
    if mapped == "tool_start":
        await session.queue.put({
            "type": "tool_start",
            "tool": getattr(event, "tool_call_name", ""),
            "toolCallId": getattr(event, "tool_call_id", ""),
        })
    elif mapped == "tool_result":
        await session.queue.put({
            "type": "tool_result",
            "toolCallId": getattr(event, "tool_call_id", ""),
        })


async def _run_turn(session: Session, text: str) -> None:
    success = False
    try:
        msg: Any = UserMsg(name="user", content=text)
        while True:
            paused = None
            async for event in session.agent.reply_stream(msg):
                await _forward(session, event)
                if isinstance(event, RequireUserConfirmEvent):
                    paused = event
                    break
            if paused is None:
                break
            session.pending_event = paused
            await session.queue.put({
                "type": "confirm_required",
                "replyId": paused.reply_id,
                "toolCalls": [
                    {"id": tc.id, "name": tc.name, "input": getattr(tc, "input", "")}
                    for tc in paused.tool_calls
                ],
            })
            loop = asyncio.get_running_loop()
            session.confirm_future = loop.create_future()
            approved = await session.confirm_future
            session.confirm_future = None
            session.pending_event = None
            flags = approved if isinstance(approved, list) else [approved] * len(paused.tool_calls)
            msg = UserConfirmResultEvent(
                reply_id=paused.reply_id,
                confirm_results=[
                    ConfirmResult(confirmed=bool(flag), tool_call=tc)
                    for flag, tc in zip(flags, paused.tool_calls, strict=False)
                ],
            )
        success = True
    except Exception as ex:  # noqa: BLE001 - surface, never crash the worker
        await session.queue.put({"type": "error", "message": f"{type(ex).__name__}: {ex}"})
    finally:
        session.busy = False
        _persist(session)
        if success:
            await session.queue.put({"type": "done"})


@app.get("/health")
async def health() -> dict[str, str]:
    return {"status": "ok"}


@app.post("/agent/sessions")
async def create_session(request: Request) -> dict[str, str]:
    caller, auth = _identity(request, _get_settings())
    # Platform-side verification (same interceptor semantics as the original
    # backend): the sessionId must be valid and the configured project must be
    # granted to its user. 401/403 propagate to the frontend.
    if auth is not None and auth.session_id:
        _verify_session_project(auth, _get_settings().project_code)
    sid = uuid.uuid4().hex
    workspace = SessionWorkspace(_SESSION_DIR / sid / "workspace")
    session = Session(
        sid=sid,
        agent=build_agent(_get_settings(), workspace=workspace, auth=auth),
        workspace=workspace,
        owner=caller,
        auth=auth,
    )
    _sessions[sid] = session
    _persist(session)
    return {"sessionId": sid}


@app.get("/agent/sessions")
async def list_sessions(request: Request) -> list[dict[str, Any]]:
    caller, _ = _identity(request, _get_settings())
    items = []
    if _SESSION_DIR.exists():
        for path in sorted(_SESSION_DIR.glob("*.json"), key=lambda p: p.stat().st_mtime, reverse=True):
            owner = "local"
            meta = _meta_path(path.stem)
            if meta.exists():
                try:
                    owner = json.loads(meta.read_text(encoding="utf-8")).get("owner", "local")
                except Exception:  # noqa: BLE001
                    owner = "local"
            if owner != caller:
                continue  # session ownership: callers only see their own sessions
            items.append({
                "sessionId": path.stem,
                "updatedAt": path.stat().st_mtime,
            })
    return items


@app.post("/agent/sessions/{sid}/messages")
async def send_message(sid: str, body: MessageBody, request: Request) -> EventSourceResponse:
    caller, auth = _identity(request, _get_settings())
    session = _load_session(sid, caller)
    _refresh_session_auth(session, auth)
    if session.busy:
        raise HTTPException(status_code=409, detail="session is processing another message")
    session.busy = True
    worker = asyncio.create_task(_run_turn(session, body.message))

    async def event_stream():
        try:
            while True:
                item = await session.queue.get()
                yield {"event": item["type"], "data": json.dumps(item, ensure_ascii=False)}
                if item["type"] in ("done", "error"):
                    break
        finally:
            if not worker.done():
                worker.cancel()

    return EventSourceResponse(event_stream())


@app.post("/agent/sessions/{sid}/confirm")
async def confirm(sid: str, body: ConfirmBody, request: Request) -> dict[str, bool]:
    caller, auth = _identity(request, _get_settings())
    session = _load_session(sid, caller)
    _refresh_session_auth(session, auth)
    if session.confirm_future is None or session.confirm_future.done():
        raise HTTPException(status_code=409, detail="no pending confirmation for this session")
    approved = body.approved if isinstance(body.approved, bool) else all(body.approved)
    audit.record_tool_call(
        "session_confirm",
        {"session": sid, "caller": caller, "approved": approved},
        True, 0, None,
    )
    session.confirm_future.set_result(body.approved)
    return {"accepted": True}


@app.delete("/agent/sessions/{sid}")
async def delete_session(sid: str, request: Request) -> dict[str, bool]:
    caller, auth = _identity(request, _get_settings())
    session = _load_session(sid, caller)  # ownership check before removal
    _refresh_session_auth(session, auth)
    _sessions.pop(sid, None)
    for path in (_state_path(sid), _meta_path(sid)):
        if path.exists():
            path.unlink()
    del session
    return {"deleted": True}


def main() -> None:
    host = os.environ.get("YAK_AGENT_HOST", "127.0.0.1")
    port = int(os.environ.get("YAK_AGENT_PORT", "8090"))
    uvicorn.run(app, host=host, port=port, log_level="info")


if __name__ == "__main__":
    main()
