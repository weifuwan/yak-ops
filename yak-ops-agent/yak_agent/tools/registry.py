"""Operation registry and reviewed allowlist loading."""

from __future__ import annotations

from pathlib import Path
from typing import Any

import yaml

from yak_agent.yak_client import YakApiError

_REGISTRY_PATH = Path(__file__).resolve().parent.parent / "operations.yaml"
_ALLOWLIST_PATH = Path(__file__).resolve().parent.parent / "agent-allowlist.yaml"

RISK_LEVELS = frozenset({"readonly", "write", "destructive"})


def operation_risk(op: dict[str, Any]) -> str:
    """Return the validated risk level; unknown values fail closed."""
    risk = op.get("risk")
    if risk not in RISK_LEVELS:
        raise YakApiError(
            f"operation {op.get('id')!r} has unsupported risk {risk!r}; "
            f"expected one of {sorted(RISK_LEVELS)}"
        )
    return risk


def is_read_only(op: dict[str, Any]) -> bool:
    """The permission flag is derived only from the reviewed risk level."""
    return operation_risk(op) == "readonly"


def load_operations() -> list[dict[str, Any]]:
    data = yaml.safe_load(_REGISTRY_PATH.read_text(encoding="utf-8")) or {}
    operations = data.get("operations") or []
    if not operations:
        raise YakApiError(f"operation registry is empty: {_REGISTRY_PATH}")
    for op in operations:
        operation_risk(op)
    return operations


def load_allowlist() -> set[str]:
    """Tool ids the reviewed allowlist exposes.

    Runtime registration is strictly limited to this set — the allowlist is an
    enforcement boundary, not documentation. This file is the single source of
    truth.
    """
    with _ALLOWLIST_PATH.open(encoding="utf-8") as handle:
        data = yaml.safe_load(handle)
    return {entry["tool"] for entry in data.get("entries", [])}
