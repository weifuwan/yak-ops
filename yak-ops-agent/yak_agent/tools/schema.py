"""JSON Schema generation for registry-backed tools."""

from __future__ import annotations

from typing import Any

_TYPE_MAP = {"string": "string", "integer": "integer", "boolean": "boolean", "object": "object"}


def visible_params(op: dict[str, Any]) -> list[dict[str, Any]]:
    """Params the model may supply (no host injections, no constants)."""
    return [p for p in op.get("params") or [] if not p.get("inject") and "const" not in p]


def build_schema(op: dict[str, Any]) -> dict[str, Any]:
    properties: dict[str, Any] = {}
    required: list[str] = []
    for p in visible_params(op):
        prop: dict[str, Any] = {"type": _TYPE_MAP.get(p.get("type", "string"), "string")}
        if p.get("description"):
            prop["description"] = p["description"]
        if p.get("enum"):
            prop["enum"] = list(p["enum"])
        if "default" in p:
            prop["default"] = p["default"]
        if p.get("json"):
            prop["description"] = (prop.get("description", "") + "（JSON 对象字符串）").strip()
        properties[p["name"]] = prop
        if p.get("required"):
            required.append(p["name"])
    schema: dict[str, Any] = {"type": "object", "properties": properties}
    if required:
        schema["required"] = required
    return schema
