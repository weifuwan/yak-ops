"""Filesystem scope for user + workspace long-term memory."""

from __future__ import annotations

import re
from pathlib import Path

_COMPONENT = re.compile(r"[A-Za-z0-9._-]+")


def validate_component(value: str, kind: str) -> str:
    """Return a safe single path component or raise ValueError."""
    if not value or value in (".", "..") or not _COMPONENT.fullmatch(value):
        raise ValueError(f"invalid {kind}: {value!r}")
    return value


def scope_dir(root: Path, owner: str, workspace_id: str) -> Path:
    """Return ``<root>/<owner>/w-<workspace_id>`` and verify containment.

    Both components come from verified request identity: ``owner`` is derived
    by ``api.deps.user_key`` and ``workspace_id`` is validated as digits before
    this point. The extra validation here keeps a future caller from turning a
    raw header into a path.
    """
    owner = validate_component(owner, "owner")
    workspace = validate_component(workspace_id, "workspace")
    root_path = Path(root).resolve()
    target = (root_path / owner / f"w-{workspace}").resolve()
    if not target.is_relative_to(root_path):
        raise ValueError(f"memory path escapes the root: {target}")
    return target


def session_dialog_path(scope: Path, session_id: str) -> Path:
    """ReMe writes one transcript per agent session under ``session/dialog``."""
    session_id = validate_component(session_id, "session_id")
    return Path(scope) / "session" / "dialog" / f"{session_id}.jsonl"


def session_images_dir(scope: Path, session_id: str) -> Path:
    session_id = validate_component(session_id, "session_id")
    return Path(scope) / "session" / "images" / session_id
