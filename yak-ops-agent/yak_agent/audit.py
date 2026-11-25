"""Tool-call audit trail (JSONL, append-only).

Every tool execution is recorded with its arguments (truncated), outcome and
duration, so the agent's behaviour is reviewable after the fact — file-based
to stay zero-dependency.

Location: $YAK_AGENT_AUDIT_DIR (default <module>/logs/audit/YYYY-MM-DD.jsonl).
"""

from __future__ import annotations

import json
import logging
import os
import threading
import time
from pathlib import Path
from typing import Any

_LOGGER = logging.getLogger(__name__)
_LOCK = threading.Lock()
_DEFAULT_DIR = Path(__file__).resolve().parent.parent / "logs" / "audit"
_MAX_PARAM_CHARS = 500


def _audit_dir() -> Path:
    directory = Path(os.environ.get("YAK_AGENT_AUDIT_DIR", str(_DEFAULT_DIR)))
    directory.mkdir(parents=True, exist_ok=True)
    return directory


def record_tool_call(
    tool: str,
    params: dict[str, Any] | None,
    ok: bool,
    duration_ms: int,
    error: str | None = None,
) -> None:
    """Append one audit record; never raises (auditing must not break the turn)."""
    try:
        entry = {
            "ts": time.strftime("%Y-%m-%dT%H:%M:%S%z"),
            "tool": tool,
            "params": json.dumps(params or {}, ensure_ascii=False, default=str)[:_MAX_PARAM_CHARS],
            "ok": ok,
            "duration_ms": duration_ms,
        }
        if error:
            entry["error"] = error[:500]
        line = json.dumps(entry, ensure_ascii=False) + "\n"
        with _LOCK:
            path = _audit_dir() / f"{time.strftime('%Y-%m-%d')}.jsonl"
            with path.open("a", encoding="utf-8") as handle:
                handle.write(line)
    except Exception as ex:  # noqa: BLE001 - audit failures must not break the turn
        # Keep the turn alive, but never hide that the audit trail is missing.
        _LOGGER.warning("audit write failed: %s", ex)
