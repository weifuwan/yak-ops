"""SessionWorkspace Offloader protocol (PR A: 1.2/1.5/1.11)."""

from __future__ import annotations

import base64
import json
import tempfile
import unittest
from pathlib import Path

from agentscope.message import (
    Base64Source,
    DataBlock,
    Msg,
    TextBlock,
    ToolResultBlock,
    ToolResultState,
)

from yak_agent.service import persistence
from yak_agent.workspace import SessionWorkspace


def _workspace(tmp: str) -> SessionWorkspace:
    return SessionWorkspace(Path(tmp) / "workspace")


class OffloadContextTests(unittest.TestCase):
    def test_context_is_appended_as_jsonl(self) -> None:
        with tempfile.TemporaryDirectory() as tmp:
            workspace = _workspace(tmp)
            first = [Msg(name="user", role="user", content=[TextBlock(type="text", text="one")])]
            second = [Msg(name="user", role="user", content=[TextBlock(type="text", text="two")])]

            import asyncio

            path_one = asyncio.run(workspace.offload_context("sid", first))
            path_two = asyncio.run(workspace.offload_context("sid", second))

            self.assertEqual(path_one, "offload/context.jsonl")
            self.assertEqual(path_two, path_one)
            lines = (workspace.root / path_one).read_text(encoding="utf-8").splitlines()
            self.assertEqual(len(lines), 2)
            self.assertEqual(json.loads(lines[0])["content"][0]["text"], "one")
            self.assertIn("two", workspace.read_file(path_one))

    def test_inline_data_block_is_offloaded_and_reused(self) -> None:
        with tempfile.TemporaryDirectory() as tmp:
            workspace = _workspace(tmp)
            payload = base64.b64encode(b"hello").decode()
            block = DataBlock(
                type="data",
                name="note.txt",
                source=Base64Source(type="base64", data=payload, media_type="text/plain"),
            )
            msg = Msg(name="user", role="user", content=[block])

            import asyncio

            asyncio.run(workspace.offload_context("sid", [msg]))
            asyncio.run(workspace.offload_context("sid", [msg]))

            files = list((workspace.root / "offload" / "data").iterdir())
            self.assertEqual(len(files), 1)
            self.assertEqual(files[0].read_bytes(), b"hello")
            line = json.loads(
                (workspace.root / "offload" / "context.jsonl")
                .read_text(encoding="utf-8")
                .splitlines()[0]
            )
            source = line["content"][0]["source"]["url"]
            self.assertTrue(source.startswith("workspace:///offload/data/"))


class OffloadToolResultTests(unittest.TestCase):
    def test_text_and_data_blocks_are_written(self) -> None:
        with tempfile.TemporaryDirectory() as tmp:
            workspace = _workspace(tmp)
            result = ToolResultBlock(
                id="call/1",
                name="download_task_log",
                output=[
                    TextBlock(type="text", text="Caused by: boom"),
                    DataBlock(
                        type="data",
                        name="image.png",
                        source=Base64Source(
                            type="base64",
                            data=base64.b64encode(b"png").decode(),
                            media_type="image/png",
                        ),
                    ),
                ],
                state=ToolResultState.SUCCESS,
            )

            import asyncio

            path = asyncio.run(workspace.offload_tool_result("sid", result))
            text = (workspace.root / path).read_text(encoding="utf-8")

            self.assertEqual(path, "offload/tool_result-call-1.txt")
            self.assertIn("Caused by: boom", text)
            self.assertIn("<data url='workspace:///offload/data/", text)
            self.assertIn("media_type='image/png'", text)

    def test_same_tool_result_id_gets_suffix(self) -> None:
        with tempfile.TemporaryDirectory() as tmp:
            workspace = _workspace(tmp)
            result = ToolResultBlock(
                id="call-1",
                name="tool",
                output="first",
                state=ToolResultState.SUCCESS,
            )

            import asyncio

            first = asyncio.run(workspace.offload_tool_result("sid", result))
            second = asyncio.run(workspace.offload_tool_result("sid", result))

            self.assertEqual(first, "offload/tool_result-call-1.txt")
            self.assertEqual(second, "offload/tool_result-call-1(1).txt")


class WorkspaceSchemeTests(unittest.TestCase):
    def test_workspace_scheme_resolves_inside_root(self) -> None:
        with tempfile.TemporaryDirectory() as tmp:
            workspace = _workspace(tmp)
            (workspace.root / "offload").mkdir()
            (workspace.root / "offload" / "ctx.txt").write_text("x", encoding="utf-8")

            self.assertEqual(workspace.read_file("workspace:///offload/ctx.txt"), "1: x")

    def test_escape_still_rejected(self) -> None:
        with tempfile.TemporaryDirectory() as tmp:
            workspace = _workspace(tmp)
            with self.assertRaises(ValueError):
                workspace.resolve("workspace:///../outside.txt")


class OffloadLifecycleTests(unittest.TestCase):
    def test_delete_files_removes_offload_directory(self) -> None:
        with tempfile.TemporaryDirectory() as tmp:
            original = persistence.SESSION_DIR
            persistence.SESSION_DIR = Path(tmp)
            try:
                workspace = SessionWorkspace(Path(tmp) / "sid" / "workspace")
                target = workspace.root / "offload" / "context.jsonl"
                target.parent.mkdir(parents=True)
                target.write_text("{}\n", encoding="utf-8")

                persistence.delete_files("sid")

                self.assertFalse((Path(tmp) / "sid").exists())
            finally:
                persistence.SESSION_DIR = original


if __name__ == "__main__":
    unittest.main()
