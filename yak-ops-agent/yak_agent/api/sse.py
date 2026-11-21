"""Translate AgentScope stream events into the frontend SSE contract."""

from __future__ import annotations

import logging
from typing import Any

from agentscope.event import EventType

from yak_agent.service.events import emit
from yak_agent.service.session import Session

_LOGGER = logging.getLogger(__name__)


async def forward_event(session: Session, event: Any) -> None:
    event_type = event.type
    reply_id = getattr(event, "reply_id", "")
    if event_type == EventType.REPLY_START:
        await emit(
            session,
            {
                "type": "reply_start",
                "replyId": reply_id,
                "name": getattr(event, "name", ""),
                "role": getattr(event, "role", "assistant"),
            },
        )
    elif event_type == EventType.TEXT_BLOCK_DELTA:
        delta = getattr(event, "delta", "")
        if delta:
            await emit(
                session,
                {
                    "type": "text_delta",
                    "delta": delta,
                    "replyId": reply_id,
                    "blockId": event.block_id,
                },
            )
    elif event_type == EventType.THINKING_BLOCK_START:
        await emit(
            session, {"type": "thinking_start", "replyId": reply_id, "blockId": event.block_id}
        )
    elif event_type == EventType.THINKING_BLOCK_DELTA:
        # Keep raw model reasoning inside the agent process. The browser receives
        # only thinking_start/thinking_end boundaries and safe progress summaries.
        return
    elif event_type == EventType.THINKING_BLOCK_END:
        await emit(
            session, {"type": "thinking_end", "replyId": reply_id, "blockId": event.block_id}
        )
    elif event_type == EventType.TOOL_CALL_START:
        await emit(
            session,
            {
                "type": "tool_start",
                "tool": event.tool_call_name,
                "toolCallId": event.tool_call_id,
                "replyId": reply_id,
            },
        )
    elif event_type == EventType.TOOL_CALL_DELTA:
        await emit(
            session,
            {
                "type": "tool_call_delta",
                "delta": event.delta,
                "toolCallId": event.tool_call_id,
                "replyId": reply_id,
            },
        )
    elif event_type == EventType.TOOL_CALL_END:
        await emit(
            session,
            {
                "type": "tool_call_end",
                "toolCallId": event.tool_call_id,
                "replyId": reply_id,
            },
        )
    elif event_type == EventType.TOOL_RESULT_START:
        await emit(
            session,
            {
                "type": "tool_result_start",
                "tool": event.tool_call_name,
                "toolCallId": event.tool_call_id,
                "replyId": reply_id,
            },
        )
    elif event_type == EventType.TOOL_RESULT_TEXT_DELTA:
        await emit(
            session,
            {
                "type": "tool_result_delta",
                "delta": event.delta,
                "toolCallId": event.tool_call_id,
                "replyId": reply_id,
            },
        )
    elif event_type == EventType.TOOL_RESULT_END:
        await emit(
            session,
            {
                "type": "tool_result",
                "toolCallId": event.tool_call_id,
                "state": getattr(event.state, "value", event.state),
                "replyId": reply_id,
            },
        )
    elif event_type == EventType.MODEL_CALL_END:
        for key, value in {
            "inputTokens": event.input_tokens,
            "outputTokens": event.output_tokens,
            "cacheInputTokens": event.cache_input_tokens,
            "cacheCreationInputTokens": event.cache_creation_input_tokens,
        }.items():
            session.usage[key] = session.usage.get(key, 0) + max(0, int(value or 0))
        await emit(
            session,
            {
                "type": "usage",
                "inputTokens": event.input_tokens,
                "outputTokens": event.output_tokens,
                "cacheInputTokens": event.cache_input_tokens,
                "cacheCreationInputTokens": event.cache_creation_input_tokens,
                "replyId": reply_id,
            },
        )
    elif event_type == EventType.EXCEED_MAX_ITERS:
        # Deprecated in AgentScope; reply_end.finishedReason remains authoritative.
        await emit(
            session,
            {
                "type": "exceed_max_iters",
                "replyId": reply_id,
                "name": getattr(event, "name", ""),
            },
        )
    elif event_type == EventType.REPLY_END:
        await emit(
            session,
            {
                "type": "reply_end",
                "finishedReason": getattr(event.finished_reason, "value", event.finished_reason),
                "error": event.error.model_dump(mode="json") if event.error else None,
                "replyId": reply_id,
            },
        )
    elif event_type == EventType.HINT_BLOCK:
        # Runtime hints contain internal time/task/usage state; never expose
        # the body to the browser, only record the correlation ids.
        _LOGGER.debug(
            "hint_block not forwarded: block_id=%s source=%s",
            getattr(event, "block_id", ""),
            getattr(event, "source", None),
        )
    elif event_type == EventType.CUSTOM:
        _LOGGER.debug(
            "custom event not forwarded: name=%s id=%s",
            getattr(event, "name", ""),
            getattr(event, "id", ""),
        )
    elif event_type in (
        EventType.DATA_BLOCK_START,
        EventType.DATA_BLOCK_DELTA,
        EventType.DATA_BLOCK_END,
        EventType.TOOL_RESULT_DATA_DELTA,
        EventType.REQUIRE_EXTERNAL_EXECUTION,
    ):
        # No current tool produces these; keep them out of the frontend
        # contract until a consumer exists.
        _LOGGER.debug("%s not forwarded", event_type)
