"""Registry-driven tool execution: path rendering and parameter routing."""

from __future__ import annotations

import json
from typing import Any

from yak_agent.yak_client import (
    YakApiError,
    YakAuth,
    truncate,
    yak_download,
    yak_get,
    yak_write,
)

_MAX_PAYLOAD_CHARS = 16_000


def render_path(op: dict[str, Any], project_code: int, values: dict[str, Any]) -> str:
    path = op["path"].replace("{project_code}", str(int(project_code)))
    for p in op.get("params") or []:
        if p.get("location") != "path":
            continue
        name = p["name"]
        if name in path:
            if values.get(name) is None:
                raise YakApiError(f"missing required path parameter '{name}' for {op['id']}")
            path = path.replace("{" + name + "}", str(int(values[name])))
    return path


def split_params(
    op: dict[str, Any],
    values: dict[str, Any],
) -> tuple[dict[str, Any], dict[str, Any]]:
    """Route model-provided values into query/body slots, applying rules."""
    query: dict[str, Any] = {}
    body: dict[str, Any] = {}
    for p in op.get("params") or []:
        name = p["name"]
        location = p.get("location")
        if location == "path" or p.get("inject"):
            continue
        if "const" in p:
            value = p["const"]
        else:
            value = values.get(name)
            if value is None or (isinstance(value, str) and not value.strip()):
                if p.get("required"):
                    raise YakApiError(f"missing required parameter '{name}' for {op['id']}")
                if "default" in p:
                    value = p["default"]
                else:
                    continue
        if p.get("enum") and value not in p["enum"]:
            raise YakApiError(
                f"invalid value '{value}' for '{name}'. Allowed: {', '.join(p['enum'])}"
            )
        if p.get("json"):
            try:
                value = json.loads(value)
            except json.JSONDecodeError as ex:
                raise YakApiError(f"'{name}' is not valid JSON: {ex}") from ex
        if "min" in p or "max" in p:
            value = int(value)
            if "min" in p:
                value = max(int(p["min"]), value)
            if "max" in p:
                value = min(int(p["max"]), value)
        wire_name = p.get("as", name)
        if location == "query":
            query[wire_name] = value
        elif location == "body":
            body[wire_name] = value
    return query, body


def execute(
    op: dict[str, Any],
    auth: YakAuth,
    values: dict[str, Any],
    project_code: int,
) -> str:
    path = render_path(op, project_code, values)
    query, body = split_params(op, values)

    method = op["method"].upper()
    raw = bool((op.get("response") or {}).get("raw"))
    if method == "GET":
        data = (
            yak_download(auth, path, query or None) if raw else yak_get(auth, path, query or None)
        )
        if raw:
            data = {"log": data, "bytes": len(data or "")}
    else:
        data = yak_write(
            auth, method, path, body if body or method != "DELETE" else None, query or None
        )

    response_cfg = op.get("response") or {}
    if isinstance(data, dict) and response_cfg.get("truncate_field"):
        field = response_cfg["truncate_field"]
        if isinstance(data.get(field), str):
            data = {**data, field: truncate(data[field])}
    return truncate(
        json.dumps(data, ensure_ascii=False, default=str),
        int(response_cfg.get("truncate", _MAX_PAYLOAD_CHARS)),
    )
