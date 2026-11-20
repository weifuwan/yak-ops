"""Startup validation for the tool surface and inventory guardrails."""

from __future__ import annotations

import logging
import threading
from dataclasses import dataclass
from typing import Any

from yak_agent.agent.budget import tool_schema_tokens
from yak_agent.tools.registry import load_allowlist, load_operations
from yak_agent.yak_client import YakApiError

_LOGGER = logging.getLogger(__name__)

# Names registered by AgentScope outside the FunctionTool list.
RESERVED_TOOL_NAMES = frozenset({"CompressContext", "Skill", "reset_tools"})
# Allowlist entries that are not operations.yaml records but are built by
# `tools/builder.py` when their backend is available.
ALLOWLIST_ONLY_NAMES = frozenset({"run_analysis_script"})

# Rough size of one framework-provided tool (CompressContext / Skill). Exact
# schemas are owned by AgentScope, so keep this conservative.
_INTERNAL_TOOL_TOKENS = 250

_WARN_LOCK = threading.Lock()
_WARNED_INVENTORIES: set[tuple[int, int]] = set()


class ToolConfigurationError(RuntimeError):
    """Raised when the tool registry or assembled tool names are invalid."""


def validate_registry() -> None:
    """Validate the static operation registry and reviewed allowlist."""
    try:
        operations = load_operations()
        allowlist = load_allowlist()
    except YakApiError as ex:
        # Registry loading itself validates risk levels; surface those as the
        # same tool-configuration contract (422) instead of a bare 500.
        raise ToolConfigurationError(str(ex)) from ex
    ids = [str(op.get("id", "")) for op in operations]
    missing = [identifier for identifier in ids if not identifier]
    duplicates = sorted({identifier for identifier in ids if ids.count(identifier) > 1})
    unknown = sorted(allowlist - set(ids) - ALLOWLIST_ONLY_NAMES)
    reserved = sorted(set(ids) & RESERVED_TOOL_NAMES)
    problems = []
    if missing:
        problems.append("operations without an id")
    if duplicates:
        problems.append(f"duplicate operation ids: {', '.join(duplicates)}")
    if unknown:
        problems.append(f"allowlist entries without a tool: {', '.join(unknown)}")
    if reserved:
        problems.append(f"operations use reserved tool names: {', '.join(reserved)}")
    if problems:
        raise ToolConfigurationError("; ".join(problems))


def validate_tool_names(tools: list[Any]) -> None:
    """Fail when the assembled tool list has duplicate or reserved names."""
    names = [str(getattr(tool, "name", "")) for tool in tools]
    missing = [name for name in names if not name]
    duplicates = sorted({name for name in names if names.count(name) > 1})
    reserved = sorted(set(names) & RESERVED_TOOL_NAMES)
    problems = []
    if missing:
        problems.append("tools without a name")
    if duplicates:
        problems.append(f"duplicate tool names: {', '.join(duplicates)}")
    if reserved:
        problems.append(f"reserved tool names: {', '.join(reserved)}")
    if problems:
        raise ToolConfigurationError("; ".join(problems))


@dataclass(frozen=True)
class ToolInventory:
    count: int
    schema_tokens: int
    names: tuple[str, ...]

    def as_dict(self) -> dict[str, Any]:
        return {
            "count": self.count,
            "schemaTokens": self.schema_tokens,
            "names": list(self.names),
        }


def check_tool_inventory(
    tools: list[Any],
    *,
    internal_count: int,
    settings: Any,
) -> ToolInventory:
    """Log the model-visible tool inventory and enforce the optional cap."""
    count = len(tools) + max(0, internal_count)
    schema_tokens = tool_schema_tokens(tools) + max(0, internal_count) * _INTERNAL_TOOL_TOKENS
    inventory = ToolInventory(
        count=count,
        schema_tokens=schema_tokens,
        names=tuple(str(getattr(tool, "name", "")) for tool in tools),
    )
    _LOGGER.info(
        "tool inventory: count=%d schema_tokens=%d names=%s",
        inventory.count,
        inventory.schema_tokens,
        ", ".join(inventory.names),
    )

    hard_max = int(getattr(settings, "tool_count_max", 0) or 0)
    if hard_max > 0 and inventory.count > hard_max:
        raise ToolConfigurationError(
            f"tool inventory has {inventory.count} tools, above the hard cap {hard_max}"
        )

    warn_count = int(getattr(settings, "tool_count_warn", 20) or 0)
    warn_tokens = int(getattr(settings, "tool_schema_warn_tokens", 6000) or 0)
    if inventory.count > warn_count or inventory.schema_tokens > warn_tokens:
        key = (inventory.count, inventory.schema_tokens)
        with _WARN_LOCK:
            first_warning = key not in _WARNED_INVENTORIES
            _WARNED_INVENTORIES.add(key)
        if first_warning:
            _LOGGER.warning(
                "tool inventory above grouping trigger: count=%d schema_tokens=%d "
                "(warn_count=%d warn_tokens=%d); consider ToolGroup migration",
                inventory.count,
                inventory.schema_tokens,
                warn_count,
                warn_tokens,
            )
    return inventory


def reset_inventory_warnings() -> None:
    """Clear the once-per-process warning cache (tests only)."""
    with _WARN_LOCK:
        _WARNED_INVENTORIES.clear()
