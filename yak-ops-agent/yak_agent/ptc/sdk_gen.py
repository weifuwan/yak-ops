"""Generate the read-only analysis SDK from the operation registry.

The generated `yak_agent_sdk.py` is placed in a per-run temporary directory by
the executor. It exposes one function per `readonly` operation, all pinned to
the configured project. The SDK carries **no credentials**: every request goes
through the host-side proxy with a per-run token, and the proxy injects the
caller's identity. Write operations are hard-disabled inside analysis scripts.
"""

from __future__ import annotations

from pathlib import Path
from typing import Any

from yak_agent.tools.registry import is_read_only, load_operations

_TEMPLATE_HEADER = '''"""Auto-generated read-only SDK for analysis scripts. Do not edit."""

import json
import os
import urllib.error
import urllib.parse
import urllib.request

_BASE_URL = __BASE_URL__
PROJECT = __PROJECT__


class YakApiError(RuntimeError):
    pass


class _ProxyAuth:
    """Sends only the per-run proxy token; real credentials stay on the host."""

    base_url = _BASE_URL
    timeout = 30

    def headers(self):
        return {
            "Accept": "application/json",
            "X-Yak-Ptc-Token": os.environ.get("YAK_PTC_TOKEN", ""),
        }


_AUTH = _ProxyAuth()


def _to_query(params):
    clean = {k: str(v) for k, v in (params or {}).items() if v is not None}
    return urllib.parse.urlencode(clean)


def _request(method, url, body=None):
    headers = _AUTH.headers()
    if body is not None:
        headers["Content-Type"] = "application/json"
    request = urllib.request.Request(url, data=body, headers=headers, method=method)
    try:
        with urllib.request.urlopen(request, timeout=_AUTH.timeout) as response:
            return response.read().decode("utf-8")
    except urllib.error.HTTPError as ex:
        detail = ex.read().decode("utf-8", errors="replace")[:500]
        raise YakApiError(f"HTTP {ex.code}: {detail}") from ex
    except urllib.error.URLError as ex:
        raise YakApiError(f"cannot reach yak-ops: {ex.reason}") from ex


def _envelope(method, path, body=None, params=None):
    url = f"{_BASE_URL}{path}"
    query = _to_query(params)
    if query:
        url = f"{url}?{query}"
    payload = None if body is None else json.dumps(body, ensure_ascii=False).encode("utf-8")
    raw = _request(method, url, payload)
    if not raw:
        return None
    envelope = json.loads(raw)
    if not isinstance(envelope, dict) or "code" not in envelope:
        raise YakApiError(f"unexpected response shape from {path}")
    if envelope.get("code") != 0:
        raise YakApiError(
            f"yak-ops business error on {path}: code={envelope.get('code')} "
            f"msg={envelope.get('msg')}"
        )
    return envelope.get("data")


def yak_get(_auth, path, params=None):
    return _envelope("GET", path, params=params)


def yak_download(_auth, path, params=None):
    url = f"{_BASE_URL}{path}"
    query = _to_query(params)
    if query:
        url = f"{url}?{query}"
    return _request("GET", url)


def yak_write(_auth, method, path, body=None, params=None):
    return _envelope(method.upper(), path, body=body, params=params)


_raw_yak_write = yak_write
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
            body_lines.append(
                "    params = None" if not query else f"    params = {{{', '.join(query)}}}"
            )
            body_lines.append("    return yak_download(_AUTH, path, params)")
        else:
            body_lines.append(
                f"    params = {{{', '.join(query)}}}" if query else "    params = None"
            )
            body_lines.append(
                "    params = {k: v for k, v in params.items() if v is not None} if params else None"
            )
            body_lines.append("    return yak_get(_AUTH, path, params)")
    else:
        body_lines.append(f"    body = {{{', '.join(body)}}}" if body else "    body = {}")
        body_lines.append("    body = {k: v for k, v in body.items() if v is not None}")
        body_lines.append(f'    return _post("{op["method"].upper()}", path, body)')
    if path.startswith("/projects/{project_code}"):
        path_expr = 'f"/projects/{PROJECT}' + path[len("/projects/{project_code}") :] + '"'
    else:
        path_expr = f'"{path}"'
    doc = (op.get("summary", "") + "\n\n" + op.get("description", "")).strip()
    doc_lines = (
        "".join(f"    {line}\n" for line in doc.splitlines())
        or "    Generated from the operation registry.\n"
    )
    src = f"""

def {op["id"]}({", ".join(signature) or ""}):
    \"\"\"{doc_lines}    \"\"\"
    path = {path_expr}
{chr(10).join(body_lines)}
"""
    return src


def generate_sdk(project_code: Any = None, base_url: str = "") -> str:
    """Return the SDK source for the readonly registry subset.

    The SDK contains no credential material: the host-side proxy resolves the
    per-run token to the caller's identity. `project_code` pins every generated
    call to one workspace/project; without it the SDK cannot build a
    project-scoped path.
    """
    body = _TEMPLATE_HEADER.replace("__BASE_URL__", repr(base_url)).replace(
        "__PROJECT__", repr(project_code)
    )
    body += (
        "\n\ndef _post(method, path, body):\n"
        "    # The only POST reachable from analysis scripts is the side-effect-free\n"
        "    # preview validation; every other mutation is rejected at import scope.\n"
        '    if method == "POST" and urllib.parse.urlsplit(path).path.endswith(\n'
        '        "/tasks/preview"\n'
        "    ):\n"
        "        return _raw_yak_write(_AUTH, method, path, body)\n"
        '    raise RuntimeError("write operations are disabled in analysis scripts")\n'
    )
    for op in [op for op in load_operations() if is_read_only(op)]:
        body += _function_source(op)
    return body


def write_sdk(
    workdir: Path,
    project_code: Any = None,
    base_url: str = "",
) -> Path:
    """Materialise the SDK into a per-run directory that the caller removes."""
    sdk_path = workdir / "yak_agent_sdk.py"
    sdk_path.write_text(generate_sdk(project_code, base_url), encoding="utf-8")
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
