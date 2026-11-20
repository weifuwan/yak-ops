"""Agent-level audit for every toolkit execution.

`on_acting` wraps ``toolkit.call_tool`` only: permission checks, HITL and
context writes happen outside this hook. That makes it the single place that
sees every actually-executed tool, including framework-provided
``CompressContext`` / ``Skill`` tools, and it provides the ``toolCallId``.
"""

from __future__ import annotations

import asyncio
import json
import logging
import time
from collections.abc import AsyncGenerator, Callable
from typing import Any

from agentscope.message import ToolResultState
from agentscope.middleware import MiddlewareBase
from agentscope.tool import ToolResponse

from yak_agent import audit

_LOGGER = logging.getLogger(__name__)
_MAX_VALUE_CHARS = 200
_RESULT_METADATA_KEYS = ("backend", "exitCode", "durationMs")
_SENSITIVE_MARKERS = (
    "password",
    "passwd",
    "secret",
    "token",
    "authorization",
    "cookie",
    "api_key",
)


class ToolAuditMiddleware(MiddlewareBase):
    """Write one JSONL audit record for every executed tool call."""

    def __init__(self, *, caller: str = "", workspace_id: int | None = None) -> None:
        self._caller = caller
        self._workspace_id = workspace_id

    async def on_acting(
        self,
        agent: Any,
        input_kwargs: dict[str, Any],
        next_handler: Callable[..., AsyncGenerator],
    ) -> AsyncGenerator:
        tool_call = input_kwargs.get("tool_call")
        tool_name = str(getattr(tool_call, "name", "") or "")
        started = time.monotonic()
        final_response: ToolResponse | None = None
        ok = False
        error: str | None = "tool returned no final response"
        try:
            async for item in next_handler(**input_kwargs):
                if isinstance(item, ToolResponse):
                    final_response = item
                yield item
            if final_response is not None:
                ok = final_response.state == ToolResultState.SUCCESS
                error = (
                    None
                    if ok
                    else f"tool state: {getattr(final_response.state, 'value', final_response.state)}"
                )
        except asyncio.CancelledError:
            ok, error = False, "cancelled"
            raise
        except Exception as ex:  # noqa: BLE001 - audit and re-raise the tool failure
            ok, error = False, f"{type(ex).__name__}: {ex}"
            raise
        finally:
            try:
                params = self._sanitize(tool_call)
                state = getattr(agent, "state", None)
                params["session"] = str(getattr(state, "session_id", "") or "")
                if self._caller:
                    params["caller"] = self._caller
                if self._workspace_id is not None:
                    params["workspace"] = self._workspace_id
                if final_response is not None:
                    metadata = getattr(final_response, "metadata", None) or {}
                    for key in _RESULT_METADATA_KEYS:
                        if key in metadata:
                            params[key] = metadata[key]
                audit.record_tool_call(
                    tool_name,
                    params,
                    ok,
                    int((time.monotonic() - started) * 1000),
                    error,
                )
            except Exception:  # noqa: BLE001 - auditing must never break a turn
                _LOGGER.warning("tool audit failed", exc_info=True)

    @staticmethod
    def _sanitize(tool_call: Any) -> dict[str, Any]:
        raw = getattr(tool_call, "input", "")
        params: dict[str, Any] = {"toolCallId": str(getattr(tool_call, "id", "") or "")}
        if isinstance(raw, dict):
            data = raw
        elif isinstance(raw, str) and raw.strip():
            try:
                data = json.loads(raw)
            except (TypeError, ValueError):
                return {**params, "inputChars": len(raw)}
        else:
            data = {}
        if not isinstance(data, dict):
            return {**params, "inputChars": len(str(raw))}

        for key, value in data.items():
            key = str(key)
            lower = key.lower()
            if key == "code":
                params[key] = {"chars": len(value) if isinstance(value, (str, bytes)) else 0}
            elif key.endswith("_json"):
                params[key] = "<json omitted>"
            elif any(marker in lower for marker in _SENSITIVE_MARKERS):
                params[key] = "<redacted>"
            elif isinstance(value, str):
                params[key] = value[:_MAX_VALUE_CHARS]
            elif isinstance(value, (bool, int, float)) or value is None:
                params[key] = value
            else:
                params[key] = {"type": type(value).__name__}
        return params
