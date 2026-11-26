"""Session state and metadata files."""

from __future__ import annotations

import json
import logging
import os
import shutil
from pathlib import Path
from typing import Any

_LOGGER = logging.getLogger(__name__)

SESSION_DIR = Path(
    os.environ.get(
        "YAK_AGENT_SESSION_DIR",
        Path(__file__).resolve().parent.parent.parent / "sessions",
    )
)


def state_path(sid: str) -> Path:
    SESSION_DIR.mkdir(parents=True, exist_ok=True)
    return SESSION_DIR / f"{sid}.json"


def meta_path(sid: str) -> Path:
    SESSION_DIR.mkdir(parents=True, exist_ok=True)
    return SESSION_DIR / f"{sid}.meta.json"


def write_atomic(path: Path, text: str) -> None:
    """Replace a file atomically: crash leaves either the old or the new content.

    A failed write removes the temp file and keeps the previous content intact.
    """
    tmp = path.with_name(f"{path.name}.tmp-{os.getpid()}")
    try:
        tmp.write_text(text, encoding="utf-8")
        os.replace(tmp, path)
    except OSError:
        try:
            tmp.unlink(missing_ok=True)
        except OSError:
            pass
        raise


def persist(session: Any) -> bool:
    """Persist one session; returns False only when a write actually failed.

    Non-persistent (legacy identity) sessions return True: there is nothing to
    write, but keeping them in memory is equally correct.
    """
    if not session.persistent:
        return True
    try:
        state = getattr(session.agent, "state", None)
        if state is not None:
            write_atomic(state_path(session.sid), state.model_dump_json())
        existing = read_meta(session.sid)
        usage = getattr(session, "usage", None) or existing.get("usage", {})
        meta = {
            "owner": session.owner,
            "workspace": session.workspace_id,
            "title": session.title or existing.get("title", ""),
            "preview": session.preview or existing.get("preview", ""),
            "usage": {key: max(0, int(value)) for key, value in usage.items()},
            "contextTokens": max(0, int(getattr(session, "context_tokens", 0) or 0)),
            "rawUsage": {
                key: max(0, int(value))
                for key, value in (getattr(session, "raw_usage", None) or {}).items()
            },
        }
        snapshot = getattr(session, "context_snapshot", None)
        if isinstance(snapshot, dict) and snapshot:
            meta["context"] = snapshot
        turn = getattr(session, "turn", None)
        if turn is not None:
            meta["activeTurn"] = {
                "turnId": turn.turn_id,
                "startedAt": turn.started_at,
                "status": turn.status,
                "message": getattr(turn, "message", ""),
            }
        write_atomic(meta_path(session.sid), json.dumps(meta, ensure_ascii=False))
        return True
    except Exception as ex:  # noqa: BLE001 - persistence must never break a turn
        # Never silent: losing state must be visible to operators.
        _LOGGER.warning("session persist failed for %s: %s", session.sid, ex)
        return False


def read_meta(sid: str) -> dict[str, Any]:
    path = meta_path(sid)
    if not path.exists():
        return {}
    try:
        return json.loads(path.read_text(encoding="utf-8"))
    except Exception as ex:  # noqa: BLE001
        _LOGGER.warning("session meta unreadable: %s (%s)", path, ex)
        return {}


def delete_files(sid: str) -> None:
    for path in (state_path(sid), meta_path(sid)):
        if path.exists():
            path.unlink()
    shutil.rmtree(SESSION_DIR / sid, ignore_errors=True)
