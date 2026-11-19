"""HTTP service exposing the data-sync agent to web frontends.

Endpoints
---------
POST   /agent/sessions                    create a session -> {sessionId}
POST   /agent/sessions/{sid}/messages     send a message -> SSE event stream
                                          (text_delta / thinking_start /
                                           thinking_delta / thinking_end /
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

Run:  python -m yak_agent.server            (defaults 127.0.0.1:8090)
"""

from __future__ import annotations

import asyncio
import json
import os
import re
import shutil
import uuid
from dataclasses import dataclass, field
from pathlib import Path
from typing import Any

import uvicorn
from agentscope.event import (
    ConfirmResult,
    EventType,
    RequireUserConfirmEvent,
    UserConfirmResultEvent,
)
from agentscope.message import UserMsg
from agentscope.state import AgentState
from fastapi import FastAPI, HTTPException, Request
from pydantic import BaseModel
from sse_starlette.sse import EventSourceResponse

from yak_agent import audit
from yak_agent.app import build_agent
from yak_agent.config import Settings
from yak_agent.workspace import SessionWorkspace
from yak_agent.yak_client import YakApiError, YakAuth, yak_get

_SESSION_DIR = Path(
    os.environ.get("YAK_AGENT_SESSION_DIR", Path(__file__).resolve().parent.parent / "sessions")
)


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
            json.dumps(
                {"owner": session.owner, "workspace": session.workspace_id},
                ensure_ascii=False,
            ),
            encoding="utf-8",
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
            raise HTTPException(
                status_code=401, detail="unauthorized: missing or invalid service token"
            )
        return
    client_host = request.client.host if request.client else "?"
    if client_host not in ("127.0.0.1", "::1", "localhost"):
        raise HTTPException(
            status_code=401,
            detail="unauthorized: set YAK_AGENT_SERVICE_TOKEN for non-local callers",
        )


def _request_identity(request: Request, settings: Settings) -> tuple[str, str, YakAuth]:
    """Resolve (user key, workspace id, per-user auth) from frontend headers.

    The frontend always sends:

    - `X-Yak-Session-Id`: the yak-ops login session (validated by the platform);
    - `X-Yak-User-Id`: the platform user id (cross-checked, never trusted);
    - `X-Yak-Workspace-Id`: the current workspace, i.e. the project code.

    The user id is verified against `GET /users/get-user-info`; a mismatch is
    rejected. The owner key uses the verified id plus the immutable createTime,
    so a reused auto-increment id cannot inherit old sessions.
    """
    _service_guard(request)
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
    user = _fetch_user(auth)
    if claimed_user_id and str(user.get("id")) != claimed_user_id:
        raise HTTPException(status_code=403, detail="user id does not match the session")
    return _user_key(user), workspace_id, auth


def _fetch_user(auth: YakAuth) -> dict[str, Any]:
    try:
        data = yak_get(auth, "/users/get-user-info")
    except YakApiError as ex:
        if ex.status_code == 401:
            raise HTTPException(
                status_code=401,
                detail="yak-ops session invalid or expired; re-login and retry",
            ) from ex
        raise HTTPException(
            status_code=502, detail=f"yak-ops user verification failed: {ex}"
        ) from ex
    if not isinstance(data, dict) or data.get("id") is None:
        raise HTTPException(status_code=502, detail="unexpected user info response")
    return data


def _user_key(user: dict[str, Any]) -> str:
    """Stable owner key: platform id plus the immutable createTime.

    `create_time` is nullable in legacy rows; those users fall back to
    ``u-{id}-na`` so the agent stays usable (long-term memory will be disabled
    for them because the id alone is not reuse-safe).
    """
    digits = re.sub(r"\D", "", str(user.get("createTime") or ""))
    return f"u-{user['id']}-{digits or 'na'}"


def _verify_workspace(auth: YakAuth, workspace_id: str) -> None:
    """Platform-side workspace (project) permission check — the sessionId is
    validated by the platform on this call, and the requested workspace must be
    granted to the session's user."""
    try:
        data = yak_get(auth, "/projects", {"pageNo": 1, "pageSize": 100})
    except YakApiError as ex:
        if ex.status_code == 401:
            raise HTTPException(
                status_code=401, detail="yak-ops session invalid or expired; re-login and retry"
            ) from ex
        raise HTTPException(
            status_code=502, detail=f"yak-ops workspace verification failed: {ex}"
        ) from ex
    codes = {str(item.get("code")) for item in ((data or {}).get("totalList") or [])}
    if str(workspace_id) not in codes:
        raise HTTPException(
            status_code=403, detail="the requested workspace is not granted to this user"
        )


def _refresh_session_auth(session: Session, auth: YakAuth | None) -> None:
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


def _read_meta(sid: str) -> dict[str, Any]:
    meta_path = _meta_path(sid)
    if not meta_path.exists():
        return {}
    try:
        return json.loads(meta_path.read_text(encoding="utf-8"))
    except Exception:  # noqa: BLE001
        return {}


def _load_session(sid: str, owner: str, workspace_id: str, auth: YakAuth) -> Session:
    if sid in _sessions:
        session = _sessions[sid]
        if session.owner != owner or session.workspace_id != workspace_id:
            raise HTTPException(
                status_code=403, detail="session belongs to another caller or workspace"
            )
        return session

    path = _state_path(sid)
    if not path.exists():
        raise HTTPException(status_code=404, detail=f"session {sid} not found")
    meta = _read_meta(sid)
    if meta.get("owner") != owner or str(meta.get("workspace", "")) != workspace_id:
        raise HTTPException(
            status_code=403, detail="session belongs to another caller or workspace"
        )
    settings = _get_settings()
    try:
        state = AgentState.model_validate_json(path.read_text(encoding="utf-8"))
    except Exception as ex:  # noqa: BLE001
        raise HTTPException(status_code=422, detail=f"session state unreadable: {ex}") from ex
    workspace = SessionWorkspace(_SESSION_DIR / sid / "workspace")
    agent = build_agent(
        settings,
        state=state,
        workspace=workspace,
        auth=auth,
        project_code=int(workspace_id),
    )
    session = Session(
        sid=sid,
        agent=agent,
        workspace=workspace,
        owner=owner,
        workspace_id=workspace_id,
        auth=auth,
    )
    _sessions[sid] = session
    return session


async def _forward(session: Session, event: Any) -> None:
    """Translate AgentScope stream events into the frontend SSE contract."""
    event_type = event.type
    reply_id = getattr(event, "reply_id", "")
    if event_type == EventType.TEXT_BLOCK_DELTA:
        delta = getattr(event, "delta", "")
        if delta:
            await session.queue.put(
                {
                    "type": "text_delta",
                    "delta": delta,
                    "replyId": reply_id,
                    "blockId": event.block_id,
                }
            )
    elif event_type == EventType.THINKING_BLOCK_START:
        await session.queue.put(
            {"type": "thinking_start", "replyId": reply_id, "blockId": event.block_id}
        )
    elif event_type == EventType.THINKING_BLOCK_DELTA:
        delta = getattr(event, "delta", "")
        if delta:
            await session.queue.put(
                {
                    "type": "thinking_delta",
                    "delta": delta,
                    "replyId": reply_id,
                    "blockId": event.block_id,
                }
            )
    elif event_type == EventType.THINKING_BLOCK_END:
        await session.queue.put(
            {"type": "thinking_end", "replyId": reply_id, "blockId": event.block_id}
        )
    elif event_type == EventType.TOOL_CALL_START:
        await session.queue.put(
            {
                "type": "tool_start",
                "tool": event.tool_call_name,
                "toolCallId": event.tool_call_id,
                "replyId": reply_id,
            }
        )
    elif event_type == EventType.TOOL_CALL_DELTA:
        await session.queue.put(
            {
                "type": "tool_call_delta",
                "delta": event.delta,
                "toolCallId": event.tool_call_id,
                "replyId": reply_id,
            }
        )
    elif event_type == EventType.TOOL_RESULT_TEXT_DELTA:
        await session.queue.put(
            {
                "type": "tool_result_delta",
                "delta": event.delta,
                "toolCallId": event.tool_call_id,
                "replyId": reply_id,
            }
        )
    elif event_type == EventType.TOOL_RESULT_END:
        await session.queue.put(
            {
                "type": "tool_result",
                "toolCallId": event.tool_call_id,
                "state": getattr(event.state, "value", event.state),
                "replyId": reply_id,
            }
        )
    elif event_type == EventType.MODEL_CALL_END:
        await session.queue.put(
            {
                "type": "usage",
                "inputTokens": event.input_tokens,
                "outputTokens": event.output_tokens,
                "cacheInputTokens": event.cache_input_tokens,
                "cacheCreationInputTokens": event.cache_creation_input_tokens,
                "replyId": reply_id,
            }
        )
    elif event_type == EventType.REPLY_END:
        await session.queue.put(
            {
                "type": "reply_end",
                "finishedReason": getattr(event.finished_reason, "value", event.finished_reason),
                "error": event.error.model_dump(mode="json") if event.error else None,
                "replyId": reply_id,
            }
        )


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
            await session.queue.put(
                {
                    "type": "confirm_required",
                    "replyId": paused.reply_id,
                    "toolCalls": [
                        {"id": tc.id, "name": tc.name, "input": getattr(tc, "input", "")}
                        for tc in paused.tool_calls
                    ],
                }
            )
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
    settings = _get_settings()
    owner, workspace_id, auth = _request_identity(request, settings)
    _verify_workspace(auth, workspace_id)
    sid = uuid.uuid4().hex
    workspace = SessionWorkspace(_SESSION_DIR / sid / "workspace")
    agent = build_agent(
        settings,
        workspace=workspace,
        auth=auth,
        project_code=int(workspace_id),
    )
    session = Session(
        sid=sid,
        agent=agent,
        workspace=workspace,
        owner=owner,
        workspace_id=workspace_id,
        auth=auth,
    )
    _sessions[sid] = session
    _persist(session)
    return {"sessionId": sid}


@app.get("/agent/sessions")
async def list_sessions(request: Request) -> list[dict[str, Any]]:
    owner, workspace_id, _ = _request_identity(request, _get_settings())
    items = []
    if _SESSION_DIR.exists():
        for path in sorted(
            _SESSION_DIR.glob("*.json"), key=lambda p: p.stat().st_mtime, reverse=True
        ):
            meta = _read_meta(path.stem)
            if meta.get("owner") != owner or str(meta.get("workspace", "")) != workspace_id:
                continue  # callers only see their own sessions in this workspace
            items.append(
                {
                    "sessionId": path.stem,
                    "updatedAt": path.stat().st_mtime,
                }
            )
    return items


@app.post("/agent/sessions/{sid}/messages")
async def send_message(sid: str, body: MessageBody, request: Request) -> EventSourceResponse:
    owner, workspace_id, auth = _request_identity(request, _get_settings())
    session = _load_session(sid, owner, workspace_id, auth)
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
    owner, workspace_id, auth = _request_identity(request, _get_settings())
    session = _load_session(sid, owner, workspace_id, auth)
    _refresh_session_auth(session, auth)
    if session.confirm_future is None or session.confirm_future.done():
        raise HTTPException(status_code=409, detail="no pending confirmation for this session")
    approved = body.approved if isinstance(body.approved, bool) else all(body.approved)
    audit.record_tool_call(
        "session_confirm",
        {"session": sid, "caller": owner, "approved": approved},
        True,
        0,
        None,
    )
    session.confirm_future.set_result(body.approved)
    return {"accepted": True}


@app.delete("/agent/sessions/{sid}")
async def delete_session(sid: str, request: Request) -> dict[str, bool]:
    owner, workspace_id, auth = _request_identity(request, _get_settings())
    session = _load_session(sid, owner, workspace_id, auth)  # ownership check before removal
    _refresh_session_auth(session, auth)
    _sessions.pop(sid, None)
    for path in (_state_path(sid), _meta_path(sid)):
        if path.exists():
            path.unlink()
    shutil.rmtree(_SESSION_DIR / sid, ignore_errors=True)
    del session
    return {"deleted": True}


def main() -> None:
    host = os.environ.get("YAK_AGENT_HOST", "127.0.0.1")
    port = int(os.environ.get("YAK_AGENT_PORT", "8090"))
    uvicorn.run(app, host=host, port=port, log_level="info")


if __name__ == "__main__":
    main()
