"""Generate the read-only analysis SDK from the operation registry.

The generated `yak_agent_sdk.py` is placed in the script's working directory by
the executor. It exposes one function per `readonly` operation, all pinned to
the configured project and authenticated through `yak_client` (credentials come
from the process environment, never from the script). Write operations are
hard-disabled inside analysis scripts at import time.
"""

from __future__ import annotations

import re
from pathlib import Path
from typing import Any

from yak_agent.tools import _load_operations

_TEMPLATE_HEADER = '''"""Auto-generated read-only SDK for analysis scripts. Do not edit."""

import sys

sys.path.insert(0, r"{agent_root}")

from yak_agent.config import Settings
from yak_agent.yak_client import YakAuth, yak_download, yak_get

_SETTINGS = Settings.load()
_AUTH = YakAuth(
    base_url=_SETTINGS.yak_ops_base_url,
    token=_SETTINGS.yak_ops_token,
    username=_SETTINGS.yak_ops_username,
    password=_SETTINGS.yak_ops_password,
)

# Writes are disabled inside analysis scripts: HITL-confirmed mutations must go
# through the form-1 tools, never through a script.
from yak_agent import yak_client as _yak_client
from yak_agent.yak_client import yak_write as _raw_yak_write


def _writes_disabled(*_args, **_kwargs):
    raise RuntimeError("write operations are disabled in analysis scripts")


_yak_client.yak_write = _writes_disabled

PROJECT = _SETTINGS.project_code
'''


def _snake_to_camel(name: str) -> str:
    head, *rest = name.split("_")
    return head + "".join(part.capitalize() for part in rest)


def _function_source(op: dict[str, Any]) -> str:
    params = [p for p in op.get("params") or [] if not p.get("inject") and "const" not in p]
    signature = []
    body_lines = []
    query = []
    body = []
    path = op["path"]
    for p in params:
        name = p["name"]
        wire = p.get("as", name)
        if "default" in p:
            sig_default = f"={p['default']!r}"
        elif not p.get("required"):
            sig_default = "=None"
        else:
            sig_default = ""
        signature.append(f"{name}{sig_default}")
        if p.get("location") == "query":
            query.append(f'"{wire}": {name}')
        elif p.get("location") == "body":
            body.append(f'"{wire}": {name}')
        elif p.get("location") == "path":
            body_lines.append(f'    path = path.replace("{{{wire}}}", str({name}))')
    if op["method"].upper() == "GET":
        if (op.get("response") or {}).get("raw"):
            body_lines.append("    params = None" if not query else f"    params = {{{', '.join(query)}}}")
            body_lines.append("    return yak_download(_AUTH, path, params)")
        else:
            body_lines.append(f"    params = {{{', '.join(query)}}}" if query else "    params = None")
            body_lines.append("    params = {k: v for k, v in params.items() if v is not None} if params else None")
            body_lines.append("    return yak_get(_AUTH, path, params)")
    else:
        body_lines.append(f"    body = {{{', '.join(body)}}}" if body else "    body = {}")
        body_lines.append("    body = {k: v for k, v in body.items() if v is not None}")
        body_lines.append(f'    return _post("{op["method"].upper()}", path, body)')
    if path.startswith("/projects/{project_code}"):
        path_expr = 'f"/projects/{PROJECT}' + path[len("/projects/{project_code}"):] + '"'
    else:
        path_expr = f'"{path}"'
    doc = (op.get("summary", "") + "\n\n" + op.get("description", "")).strip()
    doc_lines = "".join(f"    {line}\n" for line in doc.splitlines()) or "    Generated from the operation registry.\n"
    src = f"""

def {op['id']}({', '.join(signature) or ''}):
    \"\"\"{doc_lines}    \"\"\"
    path = {path_expr}
{chr(10).join(body_lines)}
"""
    return src


def generate_sdk(agent_root: Path) -> str:
    """Return the SDK source for the readonly registry subset."""
    operations = [op for op in _load_operations() if op.get("risk") == "readonly"]
    body = _TEMPLATE_HEADER.format(agent_root=str(agent_root))
    body += (
        "\n\ndef _post(method, path, body):\n"
        "    # The only POST reachable from analysis scripts is the side-effect-free\n"
        "    # preview validation; every other mutation is rejected at import scope.\n"
        "    if method == \"POST\" and path.endswith(\"/tasks/preview\"):\n"
        "        return _raw_yak_write(_AUTH, method, path, body)\n"
        "    raise RuntimeError(\"write operations are disabled in analysis scripts\")\n"
    )
    for op in operations:
        body += _function_source(op)
    return body


def write_sdk(workdir: Path, agent_root: Path) -> Path:
    """Materialise the SDK into a script working directory."""
    sdk_path = workdir / "yak_agent_sdk.py"
    sdk_path.write_text(generate_sdk(agent_root), encoding="utf-8")
    return sdk_path


_FORBIDDEN_PATTERNS = (
    "yak_write",
    "create_sync_task",
    "update_sync_task",
    "publish_sync_task",
    "unpublish_sync_task",
    "delete_sync_task",
    "stop_task_instance",
    "force_success_task_instance",
    "os.system",
    "subprocess",
)


def script_rejected(code: str) -> str | None:
    """Return a rejection reason if the script touches writes or escapes the SDK."""
    lowered = code.lower()
    for pattern in _FORBIDDEN_PATTERNS:
        if pattern.lower() in lowered:
            return f"script references '{pattern}', which is not available to analysis scripts"
    return None
