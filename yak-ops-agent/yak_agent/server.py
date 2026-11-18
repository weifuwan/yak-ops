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
from fastapi import FastAPI, HTTPException
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

from yak_agent.app import build_agent
from yak_agent.config import Settings

_SESSION_DIR = Path(os.environ.get("YAK_AGENT_SESSION_DIR", Path(__file__).resolve().parent.parent / "sessions"))
_FORWARD_EVENTS = {
    EventType.TOOL_CALL_START: "tool_start",
    EventType.TOOL_RESULT_END: "tool_result",
}


@dataclass
class Session:
    sid: str
    agent: Any
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


def _persist(session: Session) -> None:
    try:
        state = getattr(session.agent, "state", None)
        if state is not None:
            _state_path(session.sid).write_text(state.model_dump_json(), encoding="utf-8")
    except Exception:  # noqa: BLE001 - persistence must never break a turn
        pass


def _load_session(sid: str) -> Session:
    if sid in _sessions:
        return _sessions[sid]
    path = _state_path(sid)
    if not path.exists():
        raise HTTPException(status_code=404, detail=f"session {sid} not found")
    settings = _get_settings()
    try:
        state = AgentState.model_validate_json(path.read_text(encoding="utf-8"))
    except Exception as ex:  # noqa: BLE001
        raise HTTPException(status_code=422, detail=f"session state unreadable: {ex}") from ex
    agent = build_agent(settings, state=state)
    session = Session(sid=sid, agent=agent)
    _sessions[sid] = session
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
                    for flag, tc in zip(flags, paused.tool_calls)
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
async def create_session() -> dict[str, str]:
    sid = uuid.uuid4().hex
    session = Session(sid=sid, agent=build_agent(_get_settings()))
    _sessions[sid] = session
    _persist(session)
    return {"sessionId": sid}


@app.get("/agent/sessions")
async def list_sessions() -> list[dict[str, Any]]:
    items = []
    if _SESSION_DIR.exists():
        for path in sorted(_SESSION_DIR.glob("*.json"), key=lambda p: p.stat().st_mtime, reverse=True):
            items.append({
                "sessionId": path.stem,
                "updatedAt": path.stat().st_mtime,
            })
    return items


@app.post("/agent/sessions/{sid}/messages")
async def send_message(sid: str, body: MessageBody) -> EventSourceResponse:
    session = _load_session(sid)
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
async def confirm(sid: str, body: ConfirmBody) -> dict[str, bool]:
    session = _sessions.get(sid) or _load_session(sid)
    if session.confirm_future is None or session.confirm_future.done():
        raise HTTPException(status_code=409, detail="no pending confirmation for this session")
    session.confirm_future.set_result(body.approved)
    return {"accepted": True}


@app.delete("/agent/sessions/{sid}")
async def delete_session(sid: str) -> dict[str, bool]:
    _sessions.pop(sid, None)
    path = _state_path(sid)
    if path.exists():
        path.unlink()
    return {"deleted": True}


def main() -> None:
    host = os.environ.get("YAK_AGENT_HOST", "127.0.0.1")
    port = int(os.environ.get("YAK_AGENT_PORT", "8090"))
    uvicorn.run(app, host=host, port=port, log_level="info")


if __name__ == "__main__":
    main()
