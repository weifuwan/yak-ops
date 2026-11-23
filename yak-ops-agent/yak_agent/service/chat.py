"""One chat turn: drive the agent, pause for confirmation, persist state."""

from __future__ import annotations

import asyncio
import logging
import time
import uuid
from collections.abc import Awaitable, Callable
from typing import Any

from agentscope.event import (
    ConfirmResult,
    EventType,
    RequireUserConfirmEvent,
    UserConfirmResultEvent,
    UserInterruptEvent,
)
from agentscope.message import AssistantMsg, HintBlock, Msg, UserMsg

from yak_agent import audit
from yak_agent.service import persistence
from yak_agent.service.events import emit
from yak_agent.service.session import Session, Turn

ForwardEvent = Callable[[Session, Any], Awaitable[None]]

# A confirmation future may carry this sentinel instead of a decision list.
INTERRUPT_SENTINEL = object()
_LOGGER = logging.getLogger(__name__)
_MEMORY_MSG_NAME = "memory"


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
) -> list[bool] | object:
    """Wait for the frontend decision; a timeout denies every pending call."""
    loop = asyncio.get_running_loop()
    if session.confirm_future is None:
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
        await emit(
            session,
            {
                "type": "progress",
                "phase": "confirm",
                "title": "确认超时，已按拒绝处理",
                "status": "error",
                "replyId": paused.reply_id,
            },
        )
        flags: list[bool] | object = [False] * len(paused.tool_calls)
    else:
        if approved is INTERRUPT_SENTINEL:
            flags = INTERRUPT_SENTINEL
        else:
            flags = approved if isinstance(approved, list) else [approved] * len(paused.tool_calls)
    finally:
        session.confirm_future = None
        session.pending_event = None
    return flags


def _purge_memory_hints(state: Any) -> set[str]:
    """Drop the previous turn's hint and snapshot the turn-start message ids."""
    if state is None:
        return set()
    state.context[:] = [
        item for item in state.context if getattr(item, "name", None) != _MEMORY_MSG_NAME
    ]
    return {item.id for item in state.context if isinstance(item, Msg)}


async def _inject_memory(
    memory: Any,
    state: Any,
    text: str,
    pre_ids: set[str],
) -> set[str]:
    """Retrieve memories and inject one bounded hint before the first model call."""
    if memory is None or state is None:
        return pre_ids
    try:
        hint = await memory.retrieve(text)
        if hint:
            state.context.append(
                AssistantMsg(
                    name=_MEMORY_MSG_NAME,
                    content=[HintBlock(hint=hint)],
                ),
            )
    except Exception as ex:  # noqa: BLE001 - memory must never break a turn
        _LOGGER.warning("memory retrieval failed: %s", ex)
    return pre_ids


def _queue_memory(memory: Any, state: Any, pre_ids: set[str]) -> None:
    """Persist the completed turn off the reply path; failures stay silent."""
    if memory is None or state is None:
        return
    new_messages = [
        item
        for item in state.context
        if isinstance(item, Msg)
        and item.id not in pre_ids
        and getattr(item, "name", None) != _MEMORY_MSG_NAME
    ]
    try:
        memory.enqueue_turn(getattr(state, "session_id", ""), new_messages)
    except Exception as ex:  # noqa: BLE001 - memory must never break a turn
        _LOGGER.warning("memory write enqueue failed: %s", ex)


async def run_turn(
    session: Session,
    text: str,
    forward: ForwardEvent,
    confirm_timeout_seconds: float = 600.0,
    turn: Turn | None = None,
) -> None:
    turn = turn or Turn(turn_id=uuid.uuid4().hex, started_at=time.time())
    session.turn = turn
    success = False
    analysis_progress_active = True
    memory = session.memory
    state = getattr(session.agent, "state", None)
    pre_ids = _purge_memory_hints(state)
    if memory is not None:
        memory.begin_turn()
    try:
        await emit(
            session,
            {
                "type": "progress",
                "phase": "analysis",
                "title": "正在分析你的需求",
                "status": "running",
            },
        )
        if memory is not None:
            pre_ids = await _inject_memory(memory, state, text, pre_ids)
        msg: Any = UserMsg(name="user", content=text)
        while True:
            paused = None
            async for event in session.agent.reply_stream(msg):
                if analysis_progress_active and (
                    event.type in (EventType.TOOL_CALL_START, EventType.REPLY_END)
                    or isinstance(event, RequireUserConfirmEvent)
                ):
                    await emit(
                        session,
                        {
                            "type": "progress",
                            "phase": "analysis",
                            "title": "需求分析完成",
                            "status": "completed",
                            "replyId": getattr(event, "reply_id", ""),
                        },
                    )
                    analysis_progress_active = False

                await forward(session, event)
                if isinstance(event, RequireUserConfirmEvent):
                    paused = event
                    break
            if paused is None:
                break
            turn.status = "awaiting_confirmation"
            turn.reply_id = paused.reply_id
            session.pending_event = paused
            loop = asyncio.get_running_loop()
            session.confirm_future = loop.create_future()
            await emit(
                session,
                {
                    "type": "confirm_required",
                    "replyId": paused.reply_id,
                    "toolCalls": [
                        {"id": tc.id, "name": tc.name, "input": getattr(tc, "input", "")}
                        for tc in paused.tool_calls
                    ],
                },
            )
            outcome = await _await_confirmation(session, paused, confirm_timeout_seconds)
            if outcome is INTERRUPT_SENTINEL:
                for tool_call in paused.tool_calls:
                    audit.record_tool_call(
                        "session_confirm_interrupted",
                        {
                            "session": session.sid,
                            "caller": session.owner,
                            "replyId": paused.reply_id,
                            "toolCallId": tool_call.id,
                            "tool": tool_call.name,
                        },
                        True,
                        0,
                        None,
                    )
                msg = UserInterruptEvent(reply_id=paused.reply_id)
                continue
            turn.status = "running"
            flags = outcome
            msg = UserConfirmResultEvent(
                reply_id=paused.reply_id,
                confirm_results=_build_confirm_results(flags, paused.tool_calls),
            )
        success = True
    except asyncio.CancelledError:
        if turn.interrupt_requested:
            await emit(session, {"type": "done", "finishedReason": "interrupted"})
            return
        raise
    except Exception as ex:  # noqa: BLE001 - surface, never crash the worker
        if analysis_progress_active:
            await emit(
                session,
                {
                    "type": "progress",
                    "phase": "analysis",
                    "title": "分析过程出现异常",
                    "status": "error",
                },
            )
        await emit(session, {"type": "error", "message": f"{type(ex).__name__}: {ex}"})
    finally:
        if memory is not None:
            memory.end_turn()
            if success:
                _queue_memory(memory, state, pre_ids)
        if success:
            await emit(session, {"type": "done"})
        session.busy = False
        session.turn = None
        persistence.persist(session)
        session.idle_event.set()
