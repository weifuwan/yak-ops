"""Session-scoped persistent workspace (LocalWorkspace思路, minimal shape).

Provides the two workspace capabilities that matter on a Docker-less host:

- a **persistent working directory** per session — PTC scripts run here, so
  intermediate files a script writes survive into follow-up rounds;
- **offloading** — oversized payloads (PTC stdout) are stored as files here,
  and the model reads them back through the `read_workspace_file` tool.

Deliberately NOT provided on this tier: a general Bash tool, arbitrary file
access outside the workspace root, network egress control (the SDK whitelist
and audit trail cover the script surface; production isolation is the
DockerWorkspace backend, per FYOIP-16).
"""

from __future__ import annotations

import re
from pathlib import Path

_MAX_READ_CHARS = 16_000


class SessionWorkspace:
    """One persistent directory per session; everything inside is readable."""

    def __init__(self, root: Path) -> None:
        self.root = root
        self.root.mkdir(parents=True, exist_ok=True)

    # -- paths ----------------------------------------------------------------

    def resolve(self, rel_path: str) -> Path:
        """Resolve a relative path, refusing escapes outside the workspace."""
        candidate = (self.root / rel_path).resolve()
        if not candidate.is_relative_to(self.root.resolve()):
            raise ValueError(f"path escapes the workspace: {rel_path}")
        return candidate

    # -- offload --------------------------------------------------------------

    def offload(self, name: str, content: str) -> str:
        """Store content as a file and return its workspace-relative path."""
        safe = re.sub(r"[^\w.-]", "-", name).strip("-") or "offload"
        rel = f"{safe}.txt"
        target = self.resolve(rel)
        seq = 1
        while target.exists():
            rel = f"{safe}-{seq}.txt"
            target = self.resolve(rel)
            seq += 1
        target.write_text(content, encoding="utf-8")
        return rel

    # -- read -----------------------------------------------------------------

    def read_file(self, rel_path: str, start_line: int = 1, end_line: int | None = None) -> str:
        """Read a workspace file (optionally a line window), line numbers prefixed."""
        target = self.resolve(rel_path)
        if not target.is_file():
            raise FileNotFoundError(f"workspace file not found: {rel_path}")
        text = target.read_text(encoding="utf-8", errors="replace")
        lines = text.splitlines()
        start = max(1, int(start_line))
        end = min(len(lines), int(end_line)) if end_line else len(lines)
        if start > len(lines):
            return f"(file has {len(lines)} lines; start_line {start} is past the end)"
        window = lines[start - 1 : end]
        numbered = "\n".join(f"{start + idx}: {line}" for idx, line in enumerate(window))
        return numbered[:_MAX_READ_CHARS]

    def list_files(self) -> str:
        """List workspace files with sizes."""
        entries = sorted(
            ((p.relative_to(self.root), p.stat().st_size) for p in self.root.rglob("*") if p.is_file()),
            key=lambda item: str(item[0]),
        )
        if not entries:
            return "(workspace is empty)"
        return "\n".join(f"{rel}  ({size} bytes)" for rel, size in entries)
