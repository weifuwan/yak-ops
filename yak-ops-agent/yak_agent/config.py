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
    # PTC safety: when True, analysis scripts may run on the no-isolation
    # subprocess backend (compute-only, credentials stripped). Default False —
    # without a real sandbox (cocoon/Docker) the tool is simply not registered.
    ptc_allow_unsafe: bool = False
    # Weighted token budget per reply (output tokens weigh 2x). When exceeded,
    # the agent is nudged to wrap up without further tool calls — the guardrail
    # against runaway multi-round turns.
    reply_token_budget: int = 25000

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
            ptc_allow_unsafe=os.environ.get("YAK_AGENT_PTC_ALLOW_UNSAFE", "").strip().lower()
            in ("1", "true", "yes"),
            reply_token_budget=int(os.environ.get("YAK_AGENT_REPLY_TOKEN_BUDGET", "25000")),
        )
