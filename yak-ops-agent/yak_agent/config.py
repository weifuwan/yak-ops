"""Settings loaded from environment variables and an optional .env file.

Zero-dependency on purpose: a tiny .env parser instead of python-dotenv.
Variable names follow the platform convention (LLM_BASE_URL, LLM_MODEL, ...)
so the same credentials used by standalone-server work unchanged.

The agent service authenticates each caller with the frontend-supplied
`X-Yak-Session-Id`, resolves the user via the platform, and takes the
workspace from `X-Yak-Workspace-Id`; there is no global platform account or
project in the configuration. `YAK_OPS_TOKEN` remains for CLI use.
"""

from __future__ import annotations

import os
from dataclasses import dataclass
from pathlib import Path

_ENV_FILE = Path(__file__).resolve().parent.parent / ".env"
_AGENT_ROOT = Path(__file__).resolve().parent.parent


def _load_env_file() -> dict:
    """Parse .env and return the values not already in os.environ.

    Deliberately does NOT mutate os.environ: the server process keeps a clean
    environment, so analysis-script subprocesses (PTC) never inherit
    credentials no matter which execution backend runs them.
    """
    if not _ENV_FILE.exists():
        return {}
    file_vars = {}
    for raw in _ENV_FILE.read_text(encoding="utf-8").splitlines():
        line = raw.strip()
        if not line or line.startswith("#") or "=" not in line:
            continue
        key, _, value = line.partition("=")
        key, value = key.strip(), value.strip().strip('"').strip("'")
        if key and key not in os.environ:
            file_vars[key] = value
    return file_vars


@dataclass(frozen=True)
class Settings:
    llm_base_url: str
    llm_api_key: str
    llm_model: str
    yak_ops_base_url: str
    yak_ops_token: str
    # Context window of the configured model, in tokens. Compression triggers
    # at 70% of this value, so it must match the model's real limit.
    llm_context_size: int = 128000
    # How long a HITL confirmation may stay pending. On timeout every pending
    # call is denied, audited and the turn continues instead of hanging.
    confirm_timeout_seconds: int = 600
    # Message history endpoint: default/capped message count and per-tool
    # output truncation (characters).
    history_max_messages: int = 200
    history_tool_chars: int = 2000
    # Tokens reserved for long-term memory (ReMe): the injection hint and the
    # memory_search tool output are both capped by this value, and the startup
    # headroom gate reserves it as well.
    llm_memory_budget_tokens: int = 1500
    # Long-term memory is shared by (user, workspace) and stored outside
    # sessions/. Disabled or failed initialization degrades to no memory.
    memory_enabled: bool = True
    memory_dir: Path = _AGENT_ROOT / "memory"
    memory_top_k: int = 5
    memory_search_timeout_seconds: float = 3.0
    memory_write_timeout_seconds: float = 180.0
    memory_max_scopes: int = 20
    memory_idle_seconds: float = 1800.0
    memory_write_queue: int = 32
    # PTC safety: when True, analysis scripts may run on the no-isolation
    # subprocess backend (compute-only, credentials stripped). Default False —
    # without a real sandbox (cocoon/Docker) the tool is simply not registered.
    ptc_allow_unsafe: bool = False
    # Weighted token budget per reply (output tokens weigh 2x). When exceeded,
    # the agent is nudged to wrap up without further tool calls — the guardrail
    # against runaway multi-round turns.
    reply_token_budget: int = 25000
    # Tool governance (PR B): audit every toolkit execution, warn when the
    # model-visible tool inventory grows, and optionally fail when it exceeds
    # an operator-set hard cap. 0 disables the hard cap.
    tool_audit_enabled: bool = True
    tool_count_warn: int = 20
    tool_count_max: int = 0
    tool_schema_warn_tokens: int = 6000

    @classmethod
    def load(cls) -> Settings:
        file_vars = _load_env_file()

        def get(key: str, default: str = "") -> str:
            return os.environ.get(key, file_vars.get(key, default))

        missing = [k for k in ("LLM_BASE_URL", "LLM_API_KEY", "LLM_MODEL") if not get(k)]
        if missing:
            raise SystemExit(
                "Missing required settings: "
                + ", ".join(missing)
                + ". Copy .env.example to .env and fill the values."
            )
        return cls(
            llm_base_url=get("LLM_BASE_URL").rstrip("/"),
            llm_api_key=get("LLM_API_KEY"),
            llm_model=get("LLM_MODEL"),
            yak_ops_base_url=get("YAK_OPS_BASE_URL", "http://127.0.0.1:12345/yak-ops").rstrip("/"),
            yak_ops_token=get("YAK_OPS_TOKEN").strip(),
            llm_context_size=int(get("LLM_CONTEXT_SIZE", "128000")),
            confirm_timeout_seconds=int(get("YAK_AGENT_CONFIRM_TIMEOUT_SECONDS", "600")),
            history_max_messages=int(get("YAK_AGENT_HISTORY_MAX_MESSAGES", "200")),
            history_tool_chars=int(get("YAK_AGENT_HISTORY_TOOL_CHARS", "2000")),
            llm_memory_budget_tokens=int(get("YAK_AGENT_MEMORY_BUDGET_TOKENS", "1500")),
            memory_enabled=get("YAK_AGENT_MEMORY_ENABLED", "true").strip().lower()
            in ("1", "true", "yes"),
            memory_dir=Path(get("YAK_AGENT_MEMORY_DIR", str(_AGENT_ROOT / "memory"))).expanduser(),
            memory_top_k=int(get("YAK_AGENT_MEMORY_TOP_K", "5")),
            memory_search_timeout_seconds=float(
                get("YAK_AGENT_MEMORY_SEARCH_TIMEOUT_SECONDS", "3")
            ),
            memory_write_timeout_seconds=float(
                get("YAK_AGENT_MEMORY_WRITE_TIMEOUT_SECONDS", "180")
            ),
            memory_max_scopes=int(get("YAK_AGENT_MEMORY_MAX_SCOPES", "20")),
            memory_idle_seconds=float(get("YAK_AGENT_MEMORY_IDLE_SECONDS", "1800")),
            memory_write_queue=int(get("YAK_AGENT_MEMORY_WRITE_QUEUE", "32")),
            ptc_allow_unsafe=os.environ.get("YAK_AGENT_PTC_ALLOW_UNSAFE", "").strip().lower()
            in ("1", "true", "yes"),
            reply_token_budget=int(os.environ.get("YAK_AGENT_REPLY_TOKEN_BUDGET", "25000")),
            tool_audit_enabled=get("YAK_AGENT_TOOL_AUDIT_ENABLED", "true").strip().lower()
            in ("1", "true", "yes"),
            tool_count_warn=int(get("YAK_AGENT_TOOL_COUNT_WARN", "20")),
            tool_count_max=int(get("YAK_AGENT_TOOL_COUNT_MAX", "0")),
            tool_schema_warn_tokens=int(get("YAK_AGENT_TOOL_SCHEMA_WARN_TOKENS", "6000")),
        )
