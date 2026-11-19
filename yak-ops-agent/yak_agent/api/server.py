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

Run:  python -m yak_agent.server             (defaults 127.0.0.1:8090)
"""

from __future__ import annotations

import asyncio
import json
import os
import uuid
from typing import Any

import uvicorn
from fastapi import FastAPI, Request
from pydantic import BaseModel
from sse_starlette.sse import EventSourceResponse

from yak_agent import audit
from yak_agent.api.deps import request_identity
from yak_agent.api.errors import session_error_handler
from yak_agent.api.sse import forward_event
from yak_agent.config import Settings
from yak_agent.service import persistence
from yak_agent.service.chat import run_turn
from yak_agent.service.errors import AgentSessionError, ConfirmationNotPending, SessionBusy
from yak_agent.service.session import (
    create_session as create_agent_session,
)
from yak_agent.service.session import (
    load_session,
    refresh_session_auth,
    remove_session,
)

app = FastAPI(title="yak-ops-agent", version="0.1.0")
app.add_exception_handler(AgentSessionError, session_error_handler)
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
            meta = persistence.read_meta(path.stem)
            if (
                meta.get("owner") != identity.owner
                or str(meta.get("workspace", "")) != identity.workspace_id
            ):
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
    identity = request_identity(request, _get_settings())
    session = load_session(sid, identity, _get_settings())
    refresh_session_auth(session, identity.auth)
    if session.busy:
        raise SessionBusy()
    session.busy = True
    worker = asyncio.create_task(run_turn(session, body.message, forward_event))

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
    identity = request_identity(request, _get_settings())
    session = load_session(sid, identity, _get_settings())
    refresh_session_auth(session, identity.auth)
    if session.confirm_future is None or session.confirm_future.done():
        raise ConfirmationNotPending()
    approved = body.approved if isinstance(body.approved, bool) else all(body.approved)
    audit.record_tool_call(
        "session_confirm",
        {"session": sid, "caller": identity.owner, "approved": approved},
        True,
        0,
        None,
    )
    session.confirm_future.set_result(body.approved)
    return {"accepted": True}


@app.delete("/agent/sessions/{sid}")
async def delete_session(sid: str, request: Request) -> dict[str, bool]:
    identity = request_identity(request, _get_settings())
    session = load_session(sid, identity, _get_settings())  # ownership check before removal
    refresh_session_auth(session, identity.auth)
    if session.busy:
        # The worker would keep running and call persist() after the state and
        # workspace files are gone. Make the caller stop/wait for it first.
        raise SessionBusy()
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
