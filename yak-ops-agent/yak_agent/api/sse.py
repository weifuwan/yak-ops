"""Translate AgentScope stream events into the frontend SSE contract."""

from __future__ import annotations

from typing import Any

from agentscope.event import EventType

from yak_agent.service.events import emit
from yak_agent.service.session import Session


async def forward_event(session: Session, event: Any) -> None:
    event_type = event.type
    reply_id = getattr(event, "reply_id", "")
    if event_type == EventType.TEXT_BLOCK_DELTA:
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
