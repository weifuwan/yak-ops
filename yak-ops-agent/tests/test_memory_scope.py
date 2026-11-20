"""Memory scope path derivation and traversal guards."""

from __future__ import annotations

import tempfile
import unittest
from pathlib import Path

from yak_agent.memory.scope import (
    scope_dir,
    session_dialog_path,
    session_images_dir,
    validate_component,
)


class MemoryScopeTests(unittest.TestCase):
    def test_scope_layout_is_owner_then_workspace(self) -> None:
        with tempfile.TemporaryDirectory() as tmp:
            root = Path(tmp)
            scope = scope_dir(root, "u-1-1729000000000", "189214122626400")

            self.assertEqual(scope, (root / "u-1-1729000000000" / "w-189214122626400").resolve())

    def test_invalid_components_are_rejected(self) -> None:
        with tempfile.TemporaryDirectory() as tmp:
            root = Path(tmp)
            for owner in ("", ".", "..", "../x", "a/b", r"a\b"):
                with self.subTest(owner=owner), self.assertRaises(ValueError):
                    scope_dir(root, owner, "1")
            for workspace in ("", ".", "..", "../2", "1/2"):
                with self.subTest(workspace=workspace), self.assertRaises(ValueError):
                    scope_dir(root, "u-1", workspace)

    def test_validate_component_accepts_generated_owner_keys(self) -> None:
        self.assertEqual(validate_component("u-1-1729000000000", "owner"), "u-1-1729000000000")
        self.assertEqual(validate_component("u-1-s-abc123", "owner"), "u-1-s-abc123")

    def test_session_transcript_paths(self) -> None:
        scope = Path("/tmp/memory/u-1/w-1")
        self.assertEqual(
            session_dialog_path(scope, "abc123"),
            scope / "session" / "dialog" / "abc123.jsonl",
        )
        self.assertEqual(
            session_images_dir(scope, "abc123"),
            scope / "session" / "images" / "abc123",
        )
        with self.assertRaises(ValueError):
            session_dialog_path(scope, "../escape")


if __name__ == "__main__":
    unittest.main()
