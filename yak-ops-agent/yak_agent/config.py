"""Settings loaded from environment variables and an optional .env file.

Zero-dependency on purpose: a tiny .env parser instead of python-dotenv.
Variable names follow the platform convention (LLM_BASE_URL, LLM_MODEL, ...)
so the same credentials used by standalone-server work unchanged.

Authentication is either an API token (`YAK_OPS_TOKEN`, when the platform token
feature is available) or a username/password pair (`YAK_OPS_USERNAME` +
`YAK_OPS_PASSWORD`, session-cookie login — the current default).
"""

from __future__ import annotations

import os
from dataclasses import dataclass
from pathlib import Path

_ENV_FILE = Path(__file__).resolve().parent.parent / ".env"


def _load_env_file() -> None:
    """Populate os.environ from .env without overriding existing values."""
    if not _ENV_FILE.exists():
        return
    for raw in _ENV_FILE.read_text(encoding="utf-8").splitlines():
        line = raw.strip()
        if not line or line.startswith("#") or "=" not in line:
            continue
        key, _, value = line.partition("=")
        key, value = key.strip(), value.strip().strip('"').strip("'")
        if key and key not in os.environ:
            os.environ[key] = value


@dataclass(frozen=True)
class Settings:
    llm_base_url: str
    llm_api_key: str
    llm_model: str
    yak_ops_base_url: str
    yak_ops_token: str
    yak_ops_username: str
    yak_ops_password: str
    project_code: int
    # PTC safety: when True, analysis scripts may run on the no-isolation
    # subprocess backend (compute-only, credentials stripped). Default False —
    # without a real sandbox (cocoon/Docker) the tool is simply not registered.
    ptc_allow_unsafe: bool = False

    @classmethod
    def load(cls) -> "Settings":
        _load_env_file()
        missing = [
            k
            for k in ("LLM_BASE_URL", "LLM_API_KEY", "LLM_MODEL", "YAK_OPS_PROJECT_CODE")
            if not os.environ.get(k)
        ]
        if missing:
            raise SystemExit(
                "Missing required settings: " + ", ".join(missing)
                + ". Copy .env.example to .env and fill the values "
                "(YAK_OPS_PROJECT_CODE is the project snowflake ID, visible in the project URL)."
            )
        token = os.environ.get("YAK_OPS_TOKEN", "").strip()
        username = os.environ.get("YAK_OPS_USERNAME", "").strip()
        password = os.environ.get("YAK_OPS_PASSWORD", "")
        if not token and not (username and password):
            raise SystemExit(
                "Missing yak-ops credentials: set YAK_OPS_USERNAME + YAK_OPS_PASSWORD "
                "(session login) or YAK_OPS_TOKEN (API token)."
            )
        project_code = os.environ["YAK_OPS_PROJECT_CODE"].strip()
        return cls(
            llm_base_url=os.environ["LLM_BASE_URL"].rstrip("/"),
            llm_api_key=os.environ["LLM_API_KEY"],
            llm_model=os.environ["LLM_MODEL"],
            yak_ops_base_url=os.environ.get("YAK_OPS_BASE_URL", "http://127.0.0.1:12345/yak-ops").rstrip("/"),
            yak_ops_token=token,
            yak_ops_username=username,
            yak_ops_password=password,
            project_code=int(project_code),
            ptc_allow_unsafe=os.environ.get('YAK_AGENT_PTC_ALLOW_UNSAFE', '').strip().lower() in ('1', 'true', 'yes'),
        )
