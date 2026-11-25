"""Local analysis-script execution backends, in preference order:

1. **cocoon** (`cocoon-sandbox`, a default dependency): cross-platform
   Rust-kernel sandbox — virtual/read-only filesystems (host mounts are
   read-only by default), Job Object process limits, network denied unless
   `allow_network=True`, wall-clock timeout. This is the sandboxed tier: the
   SDK carries only a per-run proxy token, and the host-side proxy injects the
   caller's identity, so script-visible material contains no platform
   credentials.
2. **subprocess, unsafe tier — disabled by default**: no OS-level isolation.
   Enabled only when `YAK_AGENT_PTC_ALLOW_UNSAFE=true`, and then the script
   runs with a **minimal environment** (credential-bearing variables stripped,
   so the SDK is unusable — compute-only).

PTC itself is opt-in (`YAK_AGENT_PTC_ENABLED`), and every run requires HITL
confirmation. Production deployments should replace both tiers with
AgentScope's DockerWorkspace. Agent-initiated calls are audited by the Agent
`on_acting` middleware; direct `run_analysis()` calls are not.
"""

from __future__ import annotations

import json
import os
import re
import secrets
import shutil
import subprocess
import sys
import tempfile
import threading
import time
from http.server import BaseHTTPRequestHandler, ThreadingHTTPServer
from pathlib import Path
from typing import Any
from urllib.parse import unquote, urlparse

from yak_agent.ptc.sdk_gen import script_rejected, write_sdk

_MAX_OUTPUT_CHARS = 16_000
_DEFAULT_TIMEOUT = 60
_HARD_TIMEOUT_CAP = 300

# Routes an analysis script may reach through the host-side proxy. They mirror
# the readonly subset of operations.yaml plus the side-effect-free preview
# validation; the generated SDK is not a security boundary on its own.
_READONLY_PATHS = (
    re.compile(r"/projects/[^/]+/data-sync/tasks"),
    re.compile(r"/projects/[^/]+/data-sync/tasks/[^/]+"),
    re.compile(r"/projects/[^/]+/data-sync/overview"),
    re.compile(r"/projects/[^/]+/data-sync/instances"),
    re.compile(r"/projects/[^/]+/data-sync/instances/[^/]+"),
    re.compile(r"/log/detail"),
    re.compile(r"/log/download-log"),
)
_PREVIEW_PATH = re.compile(r"/projects/[^/]+/data-sync/tasks/preview")


def _strip_base_path(path: str, base_path: str) -> str:
    """Remove the configured API context path before allowlist matching."""
    prefix = base_path.rstrip("/")
    if not prefix:
        return path
    if path == prefix:
        return "/"
    if path.startswith(prefix + "/"):
        return path[len(prefix) :]
    return path


def _path_allowed(method: str, path: str) -> bool:
    """Whether an analysis script may send this method/path through the proxy."""
    decoded = unquote(path)
    # A residual "%" means the request was encoded more than once; upstream may
    # decode it again, so refuse instead of guessing.
    if "%" in decoded or ".." in decoded.split("/"):
        return False
    normalized = decoded.rstrip("/") or "/"
    if method in ("GET", "HEAD"):
        return any(pattern.fullmatch(normalized) for pattern in _READONLY_PATHS)
    if method == "POST":
        return bool(_PREVIEW_PATH.fullmatch(normalized))
    return False


# Environment variables that must never reach an analysis script.
_SENSITIVE_PREFIXES = ("YAK_", "LLM_", "COCOON_")
_UNSAFE_REFUSAL = (
    "PTC disabled: no sandboxed backend is available. "
    "Install cocoon-sandbox (a default dependency) for sandboxed execution, or set "
    "YAK_AGENT_PTC_ALLOW_UNSAFE=true to allow compute-only scripts on the no-isolation "
    "subprocess backend (the read-only SDK is unavailable there — credentials are stripped)."
)


def cocoon_available() -> bool:
    try:
        import cocoon  # noqa: F401

        return True
    except ImportError:
        return False


def _pick_backend(allow_unsafe: bool) -> str:
    """Return the execution backend: 'cocoon' | 'subprocess' | 'disabled'."""
    if cocoon_available():
        return "cocoon"
    return "subprocess" if allow_unsafe else "disabled"


def _minimal_env() -> dict[str, str]:
    """A subprocess environment with credential-bearing variables stripped.

    Without credentials the generated SDK refuses to authenticate, which makes
    the unsafe tier compute-only by construction.
    """
    keep = {
        "PATH",
        "PATHEXT",
        "SYSTEMROOT",
        "SYSTEMDRIVE",
        "TEMP",
        "TMP",
        "APPDATA",
        "LOCALAPPDATA",
        "COMSPEC",
        "WINDIR",
        "PYTHONIOENCODING",
        "PYTHONUTF8",
        "HOME",
        "USERPROFILE",
    }
    env = {k: v for k, v in os.environ.items() if k in keep}
    env["PYTHONIOENCODING"] = "utf-8"
    return env


def _truncate(text: str) -> str:
    if len(text) <= _MAX_OUTPUT_CHARS:
        return text
    return (
        text[: _MAX_OUTPUT_CHARS // 2] + "\n...(truncated)...\n" + text[-_MAX_OUTPUT_CHARS // 2 :]
    )


def _run_cocoon(script: Path, workdir: Path, timeout: int) -> tuple[str, str, int]:
    import cocoon

    result = cocoon.run(
        file=str(script),
        runtime="python",
        work=str(workdir),
        timeout=timeout,
        allow_network=True,  # the read-only SDK needs the yak-ops API; the SDK is the only I/O surface
        max_procs=32,
    )
    exit_code = 0 if result.success else (getattr(result, "exit_code", None) or 1)
    return (result.stdout or "", result.stderr or "", exit_code)


def _run_subprocess(script: Path, workdir: Path, timeout: int) -> tuple[str, str, int]:
    try:
        proc = subprocess.run(  # noqa: S603 - fixed interpreter, fixed script path, minimal env
            [sys.executable, str(script)],
            cwd=str(workdir),
            capture_output=True,
            text=True,
            encoding="utf-8",
            errors="replace",
            timeout=timeout,
            env=_minimal_env(),  # never inherit credential-bearing variables
        )
        return (proc.stdout or "", proc.stderr or "", proc.returncode)
    except subprocess.TimeoutExpired:
        return ("", f"TIMEOUT after {timeout}s", -1)


_PROXY_LOCK = threading.Lock()
_PROXIES: dict[tuple[str, str], ThreadingHTTPServer] = {}
# Per-run tokens: the script only ever sees an opaque token, while the proxy
# resolves it to the calling user's YakAuth and injects the real headers.
_RUN_AUTHS: dict[str, Any] = {}


def register_run_auth(token: str, auth: Any) -> None:
    with _PROXY_LOCK:
        _RUN_AUTHS[token] = auth


def unregister_run_auth(token: str) -> None:
    with _PROXY_LOCK:
        _RUN_AUTHS.pop(token, None)


def _auth_for_token(token: str) -> Any:
    with _PROXY_LOCK:
        return _RUN_AUTHS.get(token)


def _make_handler(allowed_netloc: str, base_path: str) -> type[BaseHTTPRequestHandler]:
    import httpx

    class Handler(BaseHTTPRequestHandler):
        protocol_version = "HTTP/1.1"

        def log_message(self, *args: Any) -> None:
            pass

        def _blocked(self, reason: str = "") -> None:
            self.send_error(
                403,
                "blocked: analysis scripts can only reach the configured yak-ops API host"
                + (f" ({reason})" if reason else ""),
            )

        def _forward(self) -> None:
            parsed = urlparse(self.path)
            if not parsed.scheme:
                target = f"http://{self.headers.get('Host', '')}{self.path}"
            else:
                target = self.path
            target_url = urlparse(target)
            if target_url.netloc != allowed_netloc or not _path_allowed(
                self.command,
                _strip_base_path(target_url.path, base_path),
            ):
                self._blocked()
                return
            auth = _auth_for_token(self.headers.get("X-Yak-Ptc-Token", ""))
            if auth is None:
                self._blocked("invalid or expired run token")
                return
            try:
                identity_headers = auth.headers()
            except Exception:  # noqa: BLE001 - never fall back to script headers
                self._blocked("no caller identity")
                return
            length = int(self.headers.get("Content-Length") or 0)
            body = self.rfile.read(length) if length else None
            # drop client auth/encoding marks and hop-by-hop headers; the proxy
            # owns the upstream identity.
            headers = {
                k: v
                for k, v in self.headers.items()
                if k.lower()
                not in (
                    "host",
                    "proxy-connection",
                    "content-length",
                    "accept-encoding",
                    "connection",
                    "keep-alive",
                    "cookie",
                    "token",
                    "authorization",
                    "x-yak-ptc-token",
                )
            }
            headers.update(identity_headers)
            try:
                resp = httpx.request(
                    self.command, target, headers=headers, content=body, timeout=60.0
                )
            except Exception as ex:  # noqa: BLE001
                self.send_error(502, f"upstream error: {ex}")
                return
            self.send_response(resp.status_code)
            for k, v in resp.headers.items():
                # content is already decompressed by httpx — drop encoding marks
                if k.lower() in (
                    "content-length",
                    "transfer-encoding",
                    "connection",
                    "keep-alive",
                    "content-encoding",
                    "set-cookie",
                ):
                    continue
                self.send_header(k, v)
            self.send_header("Content-Length", str(len(resp.content)))
            self.end_headers()
            self.wfile.write(resp.content)

        do_GET = _forward
        do_HEAD = _forward
        do_POST = _forward
        do_PUT = _blocked
        do_DELETE = _blocked
        do_PATCH = _blocked
        do_OPTIONS = _blocked
        do_CONNECT = _blocked

    return Handler


def _get_proxy(allowed_netloc: str, base_path: str = "") -> str:
    """Start (once per host/context path) a localhost forward proxy.

    The sandboxed script routes its HTTP through this proxy via
    HTTP_PROXY/HTTPS_PROXY set inside the script process — so SDK calls reach
    yak-ops while any other destination gets a 403 from the proxy itself.
    `base_path` (e.g. `/yak-ops`) is stripped before allowlist matching because
    SDK URLs include the API context path while the registry stores paths
    relative to that context.
    """
    key = (allowed_netloc, base_path)
    with _PROXY_LOCK:
        server = _PROXIES.get(key)
        if server is None:
            server = ThreadingHTTPServer(
                ("127.0.0.1", 0),
                _make_handler(allowed_netloc, base_path),
            )
            threading.Thread(target=server.serve_forever, daemon=True).start()
            _PROXIES[key] = server
    return f"http://127.0.0.1:{server.server_address[1]}"


def run_analysis(
    code: str,
    timeout_seconds: int = _DEFAULT_TIMEOUT,
    workdir: Path | None = None,
    workspace: Any | None = None,
    allow_unsafe: bool = False,
    auth: Any = None,
    project_code: Any = None,
) -> dict[str, Any]:
    """Execute one analysis script and return {stdout, stderr, exitCode, durationMs, backend}.

    Backend policy: cocoon (sandboxed, SDK available) by default; the
    no-isolation subprocess tier only when `allow_unsafe` is set, running with
    a minimal environment (compute-only — the SDK cannot authenticate there).
    In the sandboxed tier the script routes HTTP through the host-side proxy
    (HTTP_PROXY); the proxy only relays the configured yak-ops API host and
    resolves the per-run token to the caller's identity, so the SDK carries no
    credentials.
    """
    started = time.monotonic()
    timeout = max(5, min(_HARD_TIMEOUT_CAP, int(timeout_seconds)))
    backend = _pick_backend(allow_unsafe)

    rejection = script_rejected(code)
    if rejection:
        result = {
            "stdout": "",
            "stderr": f"REJECTED: {rejection}",
            "exitCode": -1,
            "durationMs": 0,
            "backend": backend,
        }
        return result

    if backend == "disabled":
        result = {
            "stdout": "",
            "stderr": f"REFUSED: {_UNSAFE_REFUSAL}",
            "exitCode": -1,
            "durationMs": int((time.monotonic() - started) * 1000),
            "backend": "disabled",
        }
        return result

    keep = workdir is not None
    workdir = Path(workdir or Path(tempfile.mkdtemp(prefix="yak-ptc-"))).resolve()
    workdir.mkdir(parents=True, exist_ok=True)
    sdk_dir: Path | None = None
    run_token = ""
    try:
        if backend == "cocoon":
            # Sandboxed tier: the script only receives an opaque per-run token.
            # The host-side proxy resolves it to the caller's identity and
            # injects the real headers, so no credential is readable from the
            # script, its files, or the workspace.
            if auth is None:
                raise ValueError("auth is required for the sandboxed PTC tier")
            base = urlparse(auth.base_url)
            proxy_url = _get_proxy(base.netloc, base.path)
            # Keep the SDK next to the script so the sandbox always sees it,
            # but in a per-run directory that is deleted afterwards; it never
            # becomes part of the persistent workspace.
            sdk_dir = workdir / f".yak-ptc-sdk-{secrets.token_hex(8)}"
            sdk_dir.mkdir()
            run_token = secrets.token_urlsafe(24)
            register_run_auth(run_token, auth)
            write_sdk(sdk_dir, project_code, auth.base_url)
            preamble = (
                f'import sys; sys.path.insert(0, r"{sdk_dir}")\n'
                f"import os as _os\n"
                f'_os.environ["HTTP_PROXY"] = {proxy_url!r}\n'
                f'_os.environ["HTTPS_PROXY"] = {proxy_url!r}\n'
                f'_os.environ["NO_PROXY"] = ""\n'
                f'_os.environ["YAK_PTC_TOKEN"] = {run_token!r}\n'
            )
        else:
            # Unsafe tier is compute-only: no SDK, no path into the agent
            # package, and no proxy — nothing credential-bearing is reachable.
            preamble = f'import sys; sys.path.insert(0, r"{workdir}")\n'
        script = workdir / "analysis.py"
        script.write_text(preamble + code, encoding="utf-8")

        if backend == "cocoon":
            stdout, stderr, exit_code = _run_cocoon(script, workdir, timeout)
        else:
            stdout, stderr, exit_code = _run_subprocess(script, workdir, timeout)

        truncated = stdout != _truncate(stdout)
        if workspace is not None and truncated:
            rel = workspace.offload("script-stdout", stdout)
            stdout = (
                _truncate(stdout)
                + f"\n(FULL stdout offloaded to workspace file: {rel}; read it with read_workspace_file)"
            )

        result = {
            "stdout": stdout,
            "stderr": stderr,
            "exitCode": exit_code,
            "durationMs": int((time.monotonic() - started) * 1000),
            "backend": backend,
        }
        return result
    finally:
        unregister_run_auth(run_token)
        if sdk_dir is not None:
            shutil.rmtree(sdk_dir, ignore_errors=True)
        if not keep:
            try:
                shutil.rmtree(workdir, ignore_errors=True)
            except Exception:  # noqa: BLE001
                pass


def format_result(result: dict[str, Any]) -> str:
    return json.dumps(result, ensure_ascii=False)
