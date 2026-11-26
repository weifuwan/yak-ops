"""Load and compose the system prompt from markdown files.

AgentScope takes a plain ``system_prompt`` string, so this bundle layout is
ours: ``AGENT.md`` carries the platform identity and global rules,
``domains/<domain>.md`` adds the rules for each tool domain that is actually
registered, and ``sections/*.md`` are optional capabilities toggled by the
tool inventory (PTC, workspace). Adding a domain is a data change, not code.
"""

from __future__ import annotations

from collections.abc import Iterable
from pathlib import Path

DEFAULT_PROMPT_DIR = Path(__file__).resolve().parent.parent / "prompts"
_CORE_FILE = "AGENT.md"
_DOMAIN_DIR = "domains"
_SECTION_DIR = "sections"


def _read(path: Path) -> str:
    if not path.is_file():
        raise FileNotFoundError(f"agent prompt file missing: {path}")
    return path.read_text(encoding="utf-8").strip()


def load_core_prompt(prompt_dir: Path = DEFAULT_PROMPT_DIR) -> str:
    return _read(prompt_dir / _CORE_FILE)


def load_domain_prompt(prompt_dir: Path, domain: str) -> str:
    return _read(prompt_dir / _DOMAIN_DIR / f"{domain}.md")


def load_section(prompt_dir: Path, name: str) -> str:
    return _read(prompt_dir / _SECTION_DIR / f"{name}.md")


def compose_system_prompt(
    domains: Iterable[str],
    *,
    prompt_dir: Path = DEFAULT_PROMPT_DIR,
    include_ptc: bool = False,
    include_workspace: bool = False,
) -> str:
    """Compose core + active domain fragments + optional sections."""
    parts = [load_core_prompt(prompt_dir)]
    seen: set[str] = set()
    for domain in domains:
        if domain in seen:
            continue
        seen.add(domain)
        parts.append(load_domain_prompt(prompt_dir, domain))
    if include_ptc:
        parts.append(load_section(prompt_dir, "ptc"))
    if include_workspace:
        parts.append(load_section(prompt_dir, "workspace"))
    return "\n\n".join(parts)


def validate_prompt_bundle(prompt_dir: Path, domains: Iterable[str]) -> None:
    """Fail fast at startup when the configured bundle is incomplete."""
    load_core_prompt(prompt_dir)
    for domain in domains:
        load_domain_prompt(prompt_dir, domain)
    for section in ("ptc", "workspace"):
        load_section(prompt_dir, section)
