"""Tools for reading files from the per-session workspace."""

from __future__ import annotations

from typing import Any

from agentscope.tool import FunctionTool

from yak_agent.yak_client import truncate


def build_workspace_tools(workspace: Any) -> list[FunctionTool]:
    def read_workspace_file(path: str, start_line: int = 1, end_line: int | None = None) -> str:
        """Read a file from the session workspace (scripts, offloaded outputs, intermediate data). Line numbers are prefixed. Use to retrieve the full version of an offloaded output.

        Args:
            path: Path relative to the workspace root, e.g. `script-stdout.txt`.
            start_line: First line to read, 1-based.
            end_line: Last line to read (inclusive); omit to read to the end.
        """
        return truncate(workspace.read_file(path, start_line, end_line))

    def list_workspace_files() -> str:
        """List the files stored in the session workspace with sizes."""
        return workspace.list_files()

    return [
        FunctionTool(read_workspace_file, is_read_only=True, is_concurrency_safe=True),
        FunctionTool(list_workspace_files, is_read_only=True, is_concurrency_safe=True),
    ]
