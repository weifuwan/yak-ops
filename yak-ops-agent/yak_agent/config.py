"""Settings loaded from environment variables and an optional .env file.

Zero-dependency on purpose: a tiny .env parser instead of python-dotenv.
Variable names mirror the yak-ops PoC convention (AGENT_BASE_URL, AGENT_MODEL, ...)
so the same credentials used by standalone-server work unchanged.
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
    project_code: int | None

    @classmethod
    def load(cls) -> "Settings":
        _load_env_file()
        missing = [k for k in ("LLM_BASE_URL", "LLM_API_KEY", "LLM_MODEL", "YAK_OPS_TOKEN") if not os.environ.get(k)]
        if missing:
            raise SystemExit(
                "Missing required settings: " + ", ".join(missing)
                + ". Copy .env.example to .env and fill the values."
            )
        project_code = os.environ.get("YAK_OPS_PROJECT_CODE", "").strip()
        return cls(
            llm_base_url=os.environ["LLM_BASE_URL"].rstrip("/"),
            llm_api_key=os.environ["LLM_API_KEY"],
            llm_model=os.environ["LLM_MODEL"],
            yak_ops_base_url=os.environ.get("YAK_OPS_BASE_URL", "http://127.0.0.1:12345/yak-ops").rstrip("/"),
            yak_ops_token=os.environ["YAK_OPS_TOKEN"],
            project_code=int(project_code) if project_code else None,
        )
