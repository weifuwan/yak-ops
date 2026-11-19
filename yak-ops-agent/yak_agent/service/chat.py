"""One chat turn: drive the agent, pause for confirmation, persist state."""

from __future__ import annotations

import asyncio
from collections.abc import Awaitable, Callable
from typing import Any

from agentscope.event import (
    ConfirmResult,
    RequireUserConfirmEvent,
    UserConfirmResultEvent,
)
from agentscope.message import UserMsg

from yak_agent.service import persistence
from yak_agent.service.session import Session

ForwardEvent = Callable[[Session, Any], Awaitable[None]]


async def run_turn(session: Session, text: str, forward: ForwardEvent) -> None:
    success = False
    try:
        msg: Any = UserMsg(name="user", content=text)
        while True:
            paused = None
            async for event in session.agent.reply_stream(msg):
                await forward(session, event)
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
        persistence.persist(session)
        if success:
            await session.queue.put({"type": "done"})
