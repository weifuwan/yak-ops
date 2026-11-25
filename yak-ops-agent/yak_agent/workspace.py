"""Session-scoped persistent workspace (LocalWorkspace思路, minimal shape).

Provides the two workspace capabilities that matter on a Docker-less host:

- a **persistent working directory** per session — PTC scripts run here, so
  intermediate files a script writes survive into follow-up rounds;
- **offloading** — oversized payloads (PTC stdout) are stored as files here,
  and the model reads them back through the `read_workspace_file` tool.

Deliberately NOT provided on this tier: a general Bash tool, arbitrary file
access outside the workspace root, network egress control (the SDK whitelist
and audit trail cover the script surface; production isolation is the
DockerWorkspace backend).
"""

from __future__ import annotations

import base64
import hashlib
import mimetypes
import re
from copy import deepcopy
from pathlib import Path
from typing import Any

from agentscope.message import (
    Base64Source,
    DataBlock,
    HintBlock,
    Msg,
    TextBlock,
    ToolResultBlock,
    URLSource,
)
from pydantic import AnyUrl

_MAX_READ_CHARS = 16_000
_OFFLOAD_DIR = "offload"
_CONTEXT_FILE = f"{_OFFLOAD_DIR}/context.jsonl"
_DATA_DIR = f"{_OFFLOAD_DIR}/data"
_WORKSPACE_SCHEME = re.compile(r"^workspace:///+")


class SessionWorkspace:
    """One persistent directory per session; everything inside is readable."""

    def __init__(self, root: Path) -> None:
        self.root = root
        self.root.mkdir(parents=True, exist_ok=True)

    # -- paths ----------------------------------------------------------------

    def resolve(self, rel_path: str) -> Path:
        """Resolve a relative path, refusing escapes outside the workspace."""
        normalized = _WORKSPACE_SCHEME.sub("", rel_path)
        candidate = (self.root / normalized).resolve()
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
        # Older PTC runs left a generated SDK (with embedded credentials) in the
        # workspace; never surface it to the model even if it still exists.
        if target.name == "yak_agent_sdk.py":
            raise ValueError("generated SDK files are not readable from the workspace")
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
            (
                (p.relative_to(self.root), p.stat().st_size)
                for p in self.root.rglob("*")
                if p.is_file()
            ),
            key=lambda item: str(item[0]),
        )
        if not entries:
            return "(workspace is empty)"
        return "\n".join(f"{rel}  ({size} bytes)" for rel, size in entries)

    # -- AgentScope Offloader protocol ---------------------------------------

    async def offload_context(self, session_id: str, msgs: list[Msg]) -> str:
        """Append compressed messages to `offload/context.jsonl`."""
        del session_id  # one workspace per session; kept for protocol shape
        copied = deepcopy(msgs)
        lines: list[str] = []
        for msg in copied:
            if isinstance(msg.content, list):
                msg.content = [await self._offload_block(block) for block in msg.content]
            lines.append(msg.model_dump_json())
        if not lines:
            return _CONTEXT_FILE
        target = self.resolve(_CONTEXT_FILE)
        target.parent.mkdir(parents=True, exist_ok=True)
        with target.open("a", encoding="utf-8") as handle:
            handle.write("\n".join(lines) + "\n")
        return _CONTEXT_FILE

    async def offload_tool_result(self, session_id: str, tool_result: ToolResultBlock) -> str:
        """Persist a truncated tool result under `offload/`."""
        del session_id
        safe = re.sub(r"[^\w.-]", "-", str(tool_result.id)).strip("-")[:80] or "tool-result"
        rel = f"{_OFFLOAD_DIR}/tool_result-{safe}.txt"
        target = self.resolve(rel)
        target.parent.mkdir(parents=True, exist_ok=True)
        index = 1
        while target.exists():
            rel = f"{_OFFLOAD_DIR}/tool_result-{safe}({index}).txt"
            target = self.resolve(rel)
            index += 1

        parts: list[str] = []
        if isinstance(tool_result.output, str):
            parts.append(tool_result.output)
        else:
            for block in tool_result.output:
                if isinstance(block, TextBlock):
                    parts.append(block.text)
                elif isinstance(block, DataBlock):
                    saved = await self.offload_data_block(block)
                    source = saved.source
                    parts.append(
                        f"<data url='{source.url}' name='{saved.name}' "
                        f"media_type='{source.media_type}'/>"
                    )
        target.write_text("".join(parts), encoding="utf-8")
        return rel

    async def offload_data_block(self, block: DataBlock) -> DataBlock:
        """Persist a base64 data block under `offload/data/`."""
        if not isinstance(block.source, Base64Source):
            return block
        digest = hashlib.sha256(block.source.data.encode()).hexdigest()
        extension = mimetypes.guess_extension(block.source.media_type) or ".bin"
        rel = f"{_DATA_DIR}/{digest}{extension}"
        target = self.resolve(rel)
        target.parent.mkdir(parents=True, exist_ok=True)
        if not target.exists():
            target.write_bytes(base64.b64decode(block.source.data))
        return DataBlock(
            id=block.id,
            name=block.name,
            source=URLSource(
                url=AnyUrl(f"workspace:///{rel}"),
                media_type=block.source.media_type,
            ),
        )

    async def _offload_block(self, block: Any) -> Any:
        if isinstance(block, DataBlock) and isinstance(block.source, Base64Source):
            return await self.offload_data_block(block)
        if isinstance(block, ToolResultBlock) and isinstance(block.output, list):
            block.output = [await self._offload_block(item) for item in block.output]
        elif isinstance(block, HintBlock) and isinstance(block.hint, list):
            block.hint = [await self._offload_block(item) for item in block.hint]
        return block
