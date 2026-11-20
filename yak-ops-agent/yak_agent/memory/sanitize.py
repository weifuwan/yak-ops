"""Write-back whitelist, secret redaction and truncation.

Only stable conversation text should reach ReMe's extraction prompt. Tool
results, tool-call arguments, injected hints and binary data are dropped: the
log lines an ops agent reads can contain credentials and text that looks like
instructions, and ReMe would otherwise persist them as long-term memory.
"""

from __future__ import annotations

import re
from typing import Any

from agentscope.message import Msg, TextBlock

MAX_MESSAGE_CHARS = 4000
MAX_TURN_CHARS = 12000
_MEMORY_MSG_NAME = "memory"

_URL_CREDENTIALS = re.compile(r"(?i)\b([a-z][a-z0-9+.-]*://)[^/\s:@]+:[^/\s@]+@")
_JDBC_URL = re.compile(r"(?i)\bjdbc:[a-z0-9]+://[^\s\"'<>]+")
_BEARER = re.compile(r"(?i)\bbearer\s+[A-Za-z0-9._~+/=-]+")
_SECRET_ASSIGNMENT = re.compile(
    r"(?i)\b(password|passwd|pwd|secret|token|api[_-]?key|authorization|cookie)"
    r"\b\s*[:=]\s*[^\s,;]+"
)

_REDACTIONS = (
    (_JDBC_URL, "jdbc:<redacted>"),
    (_URL_CREDENTIALS, r"\1<redacted>@"),
    (_BEARER, "Bearer <redacted>"),
    (_SECRET_ASSIGNMENT, r"\1=<redacted>"),
)


def redact(text: str) -> str:
    """Remove the common credential shapes from one piece of text."""
    for pattern, replacement in _REDACTIONS:
        text = pattern.sub(replacement, text)
    return text


def _text_of(msg: Any) -> str:
    """Collect top-level text blocks; ignore tool calls/results and hints."""
    content = getattr(msg, "content", None)
    if isinstance(content, str):
        return content.strip()
    if not isinstance(content, list):
        return ""
    parts: list[str] = []
    for block in content:
        if isinstance(block, dict):
            block_type = block.get("type")
            text = block.get("text")
        else:
            block_type = getattr(block, "type", None)
            text = getattr(block, "text", None)
        if block_type == "text" and isinstance(text, str) and text.strip():
            parts.append(text.strip())
    return "\n".join(parts).strip()


def _replace_text(msg: Msg, text: str) -> Msg:
    return msg.model_copy(update={"content": [TextBlock(text=text)]})


def sanitize_turn(messages: list[Any]) -> list[Msg]:
    """Return the messages worth writing to memory, already redacted.

    Keeps every user text message and only the final assistant text message of
    the turn. The final answer summarizes the tool work; intermediate assistant
    steps and tool output only add noise and injection surface.
    """
    if not messages:
        return []

    last_assistant_id = ""
    for msg in messages:
        if getattr(msg, "role", "") == "assistant" and _text_of(msg):
            last_assistant_id = getattr(msg, "id", "")

    kept: list[Msg] = []
    for msg in messages:
        if not isinstance(msg, Msg):
            continue
        if getattr(msg, "name", None) == _MEMORY_MSG_NAME:
            continue
        role = getattr(msg, "role", "")
        if role == "assistant" and getattr(msg, "id", "") != last_assistant_id:
            continue
        if role not in ("user", "assistant"):
            continue
        text = _text_of(msg)
        if not text:
            continue
        kept.append(_replace_text(msg, redact(text)[:MAX_MESSAGE_CHARS]))

    # Keep the newest turns if the whole increment is unusually large. The
    # order is preserved so ReMe sees the final answer last.
    total = 0
    bounded: list[Msg] = []
    for msg in kept:
        text = _text_of(msg)
        remaining = MAX_TURN_CHARS - total
        if remaining <= 0:
            break
        if len(text) > remaining:
            text = text[:remaining] + " (truncated)"
            msg = _replace_text(msg, text)
        bounded.append(msg)
        total += len(text)
    return bounded
