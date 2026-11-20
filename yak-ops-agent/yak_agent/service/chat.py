"""One chat turn: drive the agent, pause for confirmation, persist state."""

from __future__ import annotations

import asyncio
from collections.abc import Awaitable, Callable
from typing import Any

from agentscope.event import (
    ConfirmResult,
    EventType,
    RequireUserConfirmEvent,
    UserConfirmResultEvent,
)
from agentscope.message import UserMsg

from yak_agent import audit
from yak_agent.service import persistence
from yak_agent.service.session import Session

ForwardEvent = Callable[[Session, Any], Awaitable[None]]


def _build_confirm_results(
    flags: list[bool],
    tool_calls: list[Any],
) -> list[ConfirmResult]:
    """Build one result per pending call; never silently drop a call."""
    if len(flags) != len(tool_calls):
        raise ValueError("confirmation result count does not match pending tool calls")
    return [
        ConfirmResult(confirmed=bool(flag), tool_call=tool_call)
        for flag, tool_call in zip(flags, tool_calls, strict=True)
    ]


async def _await_confirmation(
    session: Session,
    paused: Any,
    timeout_seconds: float,
) -> list[bool]:
    """Wait for the frontend decision; a timeout denies every pending call."""
    loop = asyncio.get_running_loop()
    session.confirm_future = loop.create_future()
    try:
        approved = await asyncio.wait_for(session.confirm_future, timeout=timeout_seconds)
    except TimeoutError:
        for tool_call in paused.tool_calls:
            audit.record_tool_call(
                "session_confirm_timeout",
                {
                    "session": session.sid,
                    "caller": session.owner,
                    "replyId": paused.reply_id,
                    "toolCallId": tool_call.id,
                    "tool": tool_call.name,
                    "confirmed": False,
                    "timeoutSeconds": timeout_seconds,
                },
                False,
                0,
                f"no confirmation within {timeout_seconds}s",
            )
        await session.queue.put(
            {
                "type": "progress",
                "phase": "confirm",
                "title": "确认超时，已按拒绝处理",
                "status": "error",
                "replyId": paused.reply_id,
            }
        )
        flags = [False] * len(paused.tool_calls)
    else:
        flags = approved if isinstance(approved, list) else [approved] * len(paused.tool_calls)
    finally:
        session.confirm_future = None
        session.pending_event = None
    return flags


async def run_turn(
    session: Session,
    text: str,
    forward: ForwardEvent,
    confirm_timeout_seconds: float = 600.0,
) -> None:
    success = False
    analysis_progress_active = True
    try:
        await session.queue.put(
            {
                "type": "progress",
                "phase": "analysis",
                "title": "正在分析你的需求",
                "status": "running",
            }
        )
        msg: Any = UserMsg(name="user", content=text)
        while True:
            paused = None
            async for event in session.agent.reply_stream(msg):
                if analysis_progress_active and (
                    event.type in (EventType.TOOL_CALL_START, EventType.REPLY_END)
                    or isinstance(event, RequireUserConfirmEvent)
                ):
                    await session.queue.put(
                        {
                            "type": "progress",
                            "phase": "analysis",
                            "title": "需求分析完成",
                            "status": "completed",
                            "replyId": getattr(event, "reply_id", ""),
                        }
                    )
                    analysis_progress_active = False

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
            flags = await _await_confirmation(session, paused, confirm_timeout_seconds)
            msg = UserConfirmResultEvent(
                reply_id=paused.reply_id,
                confirm_results=_build_confirm_results(flags, paused.tool_calls),
            )
        success = True
    except Exception as ex:  # noqa: BLE001 - surface, never crash the worker
        if analysis_progress_active:
            await session.queue.put(
                {
                    "type": "progress",
                    "phase": "analysis",
                    "title": "分析过程出现异常",
                    "status": "error",
                }
            )
        await session.queue.put({"type": "error", "message": f"{type(ex).__name__}: {ex}"})
    finally:
        session.busy = False
        persistence.persist(session)
        if success:
            await session.queue.put({"type": "done"})
