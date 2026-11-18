"""Local analysis-script execution backends, in preference order:

1. **cocoon** (`cocoon-sandbox`, a default dependency): cross-platform
   Rust-kernel sandbox — virtual/read-only filesystems (host mounts are
   read-only by default), Job Object process limits, network denied unless
   `allow_network=True`, wall-clock timeout. This is the sandboxed tier; the
   read-only SDK runs inside it, so credentials are visible to the script but
   bounded by the sandbox (no host writes, no free egress) and by the audit
   trail.
2. **subprocess, unsafe tier — disabled by default**: no OS-level isolation.
   Enabled only when `YAK_AGENT_PTC_ALLOW_UNSAFE=true`, and then the script
   runs with a **minimal environment** (credential-bearing variables stripped,
   so the SDK is unusable — compute-only) and the same audit record.

Production deployments replace both with AgentScope's DockerWorkspace.
Either way: the generated SDK is the only I/O surface, writes are
disabled inside scripts, and every execution is audited.
"""

from __future__ import annotations

import json
import os
import subprocess
import sys
import tempfile
import time
from pathlib import Path
from typing import Any

from yak_agent import audit
from yak_agent.sdk_gen import script_rejected, write_sdk

_MAX_OUTPUT_CHARS = 16_000
_DEFAULT_TIMEOUT = 60
_HARD_TIMEOUT_CAP = 300

_AGENT_ROOT = Path(__file__).resolve().parent.parent

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
    keep = {"PATH", "PATHEXT", "SYSTEMROOT", "SYSTEMDRIVE", "TEMP", "TMP",
            "APPDATA", "LOCALAPPDATA", "COMSPEC", "WINDIR", "PYTHONIOENCODING",
            "PYTHONUTF8", "HOME", "USERPROFILE"}
    env = {k: v for k, v in os.environ.items() if k in keep}
    env["PYTHONIOENCODING"] = "utf-8"
    return env


def _truncate(text: str) -> str:
    if len(text) <= _MAX_OUTPUT_CHARS:
        return text
    return text[: _MAX_OUTPUT_CHARS // 2] + "\n...(truncated)...\n" + text[-_MAX_OUTPUT_CHARS // 2 :]


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


def run_analysis(
    code: str,
    timeout_seconds: int = _DEFAULT_TIMEOUT,
    workdir: Path | None = None,
    workspace: Any | None = None,
    allow_unsafe: bool = False,
) -> dict[str, Any]:
    """Execute one analysis script and return {stdout, stderr, exitCode, durationMs, backend}.

    Backend policy: cocoon (sandboxed, SDK available) by default; the
    no-isolation subprocess tier only when `allow_unsafe` is set, running with
    a minimal environment (compute-only — the SDK cannot authenticate there).
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
        audit.record_tool_call("run_analysis_script", {"code_len": len(code)}, False, 0, rejection)
        return result

    if backend == "disabled":
        result = {
            "stdout": "",
            "stderr": f"REFUSED: {_UNSAFE_REFUSAL}",
            "exitCode": -1,
            "durationMs": int((time.monotonic() - started) * 1000),
            "backend": "disabled",
        }
        audit.record_tool_call("run_analysis_script", {"code_len": len(code)}, False, result["durationMs"], "PTC disabled")
        return result

    keep = workdir is not None
    workdir = Path(workdir or Path(tempfile.mkdtemp(prefix="yak-ptc-"))).resolve()
    workdir.mkdir(parents=True, exist_ok=True)
    try:
        if backend == "cocoon":
            # Sandboxed tier: SDK + credentials reachable inside the sandbox.
            write_sdk(workdir, _AGENT_ROOT)
            preamble = (
                f"import sys; sys.path.insert(0, r\"{workdir}\"); sys.path.insert(0, r\"{_AGENT_ROOT}\")\n"
            )
        else:
            # Unsafe tier is compute-only: no SDK, no path into the agent
            # package — otherwise Settings.load() would read the host .env and
            # hand credentials to the script.
            preamble = f"import sys; sys.path.insert(0, r\"{workdir}\")\n"
        script = workdir / "analysis.py"
        script.write_text(preamble + code, encoding="utf-8")

        if backend == "cocoon":
            stdout, stderr, exit_code = _run_cocoon(script, workdir, timeout)
        else:
            stdout, stderr, exit_code = _run_subprocess(script, workdir, timeout)

        truncated = stdout != _truncate(stdout)
        if workspace is not None and truncated:
            rel = workspace.offload("script-stdout", stdout)
            stdout = _truncate(stdout) + f"\n(FULL stdout offloaded to workspace file: {rel}; read it with read_workspace_file)"

        result = {
            "stdout": stdout,
            "stderr": stderr,
            "exitCode": exit_code,
            "durationMs": int((time.monotonic() - started) * 1000),
            "backend": backend,
        }
        ok = exit_code == 0
        audit.record_tool_call(
            "run_analysis_script",
            {"code_len": len(code), "timeout": timeout, "backend": backend},
            ok,
            result["durationMs"],
            None if ok else (stderr or "non-zero exit")[:500],
        )
        return result
    finally:
        if not keep:
            try:
                import shutil

                shutil.rmtree(workdir, ignore_errors=True)
            except Exception:  # noqa: BLE001
                pass


def format_result(result: dict[str, Any]) -> str:
    return json.dumps(result, ensure_ascii=False)
