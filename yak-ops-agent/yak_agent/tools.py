"""Data-sync agent tools built from the declarative operation registry.

`operations.yaml` is the single source of truth: every record becomes one
AgentScope FunctionTool with a JSON Schema derived from its params. The executor
owns the cross-cutting rules — project injection, query/body/path routing,
clamping, JSON parsing, envelope checks, truncation — so new operations are
data, not code.

Risk levels map to the permission system:
- readonly  -> is_read_only=True  -> auto-allowed (read-only fast path);
- write / destructive -> is_read_only=False -> ASK -> HITL confirmation pause.
"""

from __future__ import annotations

import json
import time
from pathlib import Path
from typing import Any, Callable

import yaml

from agentscope.tool import FunctionTool

from yak_agent import audit
from yak_agent.config import Settings
from yak_agent.yak_client import YakApiError, YakAuth, truncate, yak_get, yak_write

_REGISTRY_PATH = Path(__file__).with_name("operations.yaml")
_MAX_PAYLOAD_CHARS = 16_000

_TYPE_MAP = {"string": "string", "integer": "integer", "boolean": "boolean", "object": "object"}


def _load_operations() -> list[dict[str, Any]]:
    data = yaml.safe_load(_REGISTRY_PATH.read_text(encoding="utf-8")) or {}
    operations = data.get("operations") or []
    if not operations:
        raise YakApiError(f"operation registry is empty: {_REGISTRY_PATH}")
    return operations


def _visible_params(op: dict[str, Any]) -> list[dict[str, Any]]:
    """Params the model may supply (no host injections, no constants)."""
    return [
        p for p in op.get("params") or []
        if not p.get("inject") and "const" not in p
    ]


def _build_schema(op: dict[str, Any]) -> dict[str, Any]:
    properties: dict[str, Any] = {}
    required: list[str] = []
    for p in _visible_params(op):
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


def _render_path(op: dict[str, Any], settings: Settings, values: dict[str, Any]) -> str:
    path = op["path"].replace("{project_code}", str(settings.project_code))
    for p in op.get("params") or []:
        if p.get("location") != "path":
            continue
        name = p["name"]
        if name in path:
            if values.get(name) is None:
                raise YakApiError(f"missing required path parameter '{name}' for {op['id']}")
            path = path.replace("{" + name + "}", str(int(values[name])))
    return path


def _split_params(
    op: dict[str, Any], values: dict[str, Any],
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
            raise YakApiError(f"invalid value '{value}' for '{name}'. Allowed: {', '.join(p['enum'])}")
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


def _execute(
    op: dict[str, Any], settings: Settings, auth: YakAuth, values: dict[str, Any],
) -> str:
    started = time.monotonic()
    ok = False
    error: str | None = None
    # Never persist free-form JSON params (may carry data-source credentials).
    audit_params = {
        k: ("<json omitted>" if k.endswith("_json") else v) for k, v in values.items()
    }
    try:
        path = _render_path(op, settings, values)
        query, body = _split_params(op, values)

        method = op["method"].upper()
        if method == "GET":
            data = yak_get(auth, path, query or None)
        else:
            data = yak_write(auth, method, path, body if body or method != "DELETE" else None, query or None)

        response_cfg = op.get("response") or {}
        if isinstance(data, dict) and response_cfg.get("truncate_field"):
            field = response_cfg["truncate_field"]
            if isinstance(data.get(field), str):
                data = {**data, field: truncate(data[field])}
        payload = truncate(
            json.dumps(data, ensure_ascii=False, default=str),
            int(response_cfg.get("truncate", _MAX_PAYLOAD_CHARS)),
        )
        ok = True
        return payload
    except Exception as ex:
        error = f"{type(ex).__name__}: {ex}"
        raise
    finally:
        audit.record_tool_call(
            op["id"], audit_params, ok, int((time.monotonic() - started) * 1000), error,
        )


def build_auth(settings: Settings) -> YakAuth:
    """Build the host-side auth (token preferred, username/password fallback)."""
    return YakAuth(
        base_url=settings.yak_ops_base_url,
        token=settings.yak_ops_token,
        username=settings.yak_ops_username,
        password=settings.yak_ops_password,
    )


def build_tools(settings: Settings, auth: YakAuth | None = None) -> list[FunctionTool]:
    """Materialize the registry as AgentScope tools bound to the given settings."""
    if not settings.project_code:
        raise YakApiError("YAK_OPS_PROJECT_CODE is not configured; refusing to build tools with an undefined project")
    auth = auth or build_auth(settings)

    tools: list[FunctionTool] = []
    for op in _load_operations():
        readonly = op.get("risk") == "readonly"

        def runner(_op: dict[str, Any] = op, **kwargs: Any) -> str:
            return _execute(_op, settings, auth, kwargs)

        tools.append(FunctionTool(
            runner,
            name=op["id"],
            description=f"[{op.get('domain', '')}] {op['summary']}\n\n{op.get('description', '').strip()}",
            input_schema=_build_schema(op),
            is_read_only=readonly,
            is_concurrency_safe=readonly,
        ))
    return tools
