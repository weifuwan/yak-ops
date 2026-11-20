"""Serialize AgentState messages into the frontend history contract."""

from __future__ import annotations

import json
from typing import Any

_ALLOWED_ROLES = frozenset({"user", "assistant"})


def _tool_output(value: Any) -> str:
    if isinstance(value, str):
        return value
    if isinstance(value, list):
        parts = []
        for block in value:
            if isinstance(block, dict):
                if block.get("type") == "text":
                    parts.append(str(block.get("text", "")))
            elif isinstance(block, str):
                parts.append(block)
            elif getattr(block, "type", None) == "text":
                parts.append(str(getattr(block, "text", "")))
        if parts:
            return "".join(parts)
    if value is None:
        return ""
    return str(value)


def _tool_input(value: Any) -> str:
    if isinstance(value, str):
        return value
    return json.dumps(value, ensure_ascii=False, default=str)


def _created_at(block: Any) -> str | None:
    value = (
        block.get("created_at") if isinstance(block, dict) else getattr(block, "created_at", None)
    )
    return str(value) if value else None


def _next_timestamps(content: list[Any]) -> list[str | None]:
    """For each block, find the next block's ``created_at`` in the message.

    AgentScope's persisted thinking blocks do not carry ``finished_at``. The
    frontend treats a missing ``completedAt`` as "still running" and falls back
    to ``Date.now()``, so a restored session would measure from the thinking
    start to the present. The next block in the same assistant message marks
    where the reasoning ended; do not look across turns, where the next user
    message could be minutes or hours later.
    """
    timestamps = [_created_at(block) for block in content]
    next_values: list[str | None] = [None] * len(timestamps)
    upcoming: str | None = None
    for index in range(len(timestamps) - 1, -1, -1):
        next_values[index] = upcoming
        if timestamps[index]:
            upcoming = timestamps[index]
    return next_values


def serialize_history(state: Any, limit: int, tool_chars: int) -> dict[str, Any]:
    """Return user/assistant messages with text and tool activity blocks."""
    context = list(getattr(state, "context", []) or [])
    messages: list[dict[str, Any]] = []
    for msg in context:
        role = getattr(msg, "role", "")
        if role not in _ALLOWED_ROLES:
            continue
        content = getattr(msg, "content", [])
        if isinstance(content, str):
            content = [{"type": "text", "text": content}]
        next_timestamps = _next_timestamps(content)
        blocks: list[dict[str, Any]] = []
        for index, block in enumerate(content):
            next_timestamp = next_timestamps[index]
            block_type = (
                block.get("type") if isinstance(block, dict) else getattr(block, "type", None)
            )
            if block_type == "text":
                text = (
                    block.get("text", "") if isinstance(block, dict) else getattr(block, "text", "")
                )
                if text:
                    blocks.append({"type": "text", "text": text})
            elif block_type == "thinking":
                # Expose timing only. Never return the model's internal reasoning text.
                started_at = _created_at(block)
                completed_at = (
                    block.get("finished_at")
                    if isinstance(block, dict)
                    else getattr(block, "finished_at", None)
                )
                if not completed_at and next_timestamp:
                    completed_at = next_timestamp
                blocks.append(
                    {
                        "type": "thinking",
                        "id": (
                            block.get("id", "")
                            if isinstance(block, dict)
                            else getattr(block, "id", "")
                        ),
                        **({"startedAt": str(started_at)} if started_at else {}),
                        **({"completedAt": str(completed_at)} if completed_at else {}),
                    }
                )
            elif block_type == "tool_call":
                blocks.append(
                    {
                        "type": "tool_call",
                        "id": block.get("id", "")
                        if isinstance(block, dict)
                        else getattr(block, "id", ""),
                        "name": block.get("name", "")
                        if isinstance(block, dict)
                        else getattr(block, "name", ""),
                        "input": _tool_input(
                            block.get("input") if isinstance(block, dict) else block.input
                        ),
                    }
                )
            elif block_type == "tool_result":
                output = _tool_output(
                    block.get("output") if isinstance(block, dict) else block.output
                )
                truncated = len(output) > tool_chars
                state_value = block.get("state") if isinstance(block, dict) else block.state
                blocks.append(
                    {
                        "type": "tool_result",
                        "id": block.get("id", "")
                        if isinstance(block, dict)
                        else getattr(block, "id", ""),
                        "name": block.get("name", "")
                        if isinstance(block, dict)
                        else getattr(block, "name", ""),
                        "state": getattr(state_value, "value", state_value),
                        "output": output[:tool_chars],
                        "truncated": truncated,
                    }
                )
        if blocks:
            messages.append(
                {
                    "id": getattr(msg, "id", ""),
                    "role": role,
                    "createdAt": str(getattr(msg, "created_at", "")),
                    "blocks": blocks,
                }
            )
    truncated = limit > 0 and len(messages) > limit
    if truncated:
        messages = messages[-limit:]
    return {"truncated": truncated, "messages": messages}
