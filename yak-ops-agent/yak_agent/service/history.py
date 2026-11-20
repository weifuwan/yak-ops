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
        if parts:
            return "".join(parts)
    if value is None:
        return ""
    return str(value)


def _tool_input(value: Any) -> str:
    if isinstance(value, str):
        return value
    return json.dumps(value, ensure_ascii=False, default=str)


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
        blocks: list[dict[str, Any]] = []
        for block in content:
            block_type = (
                block.get("type") if isinstance(block, dict) else getattr(block, "type", None)
            )
            if block_type == "text":
                text = (
                    block.get("text", "") if isinstance(block, dict) else getattr(block, "text", "")
                )
                if text:
                    blocks.append({"type": "text", "text": text})
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
