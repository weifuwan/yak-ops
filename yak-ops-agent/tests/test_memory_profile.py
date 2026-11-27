"""Pinned profile storage: merge, render, bootstrap and explicit statements."""

from __future__ import annotations

import json
import tempfile
import unittest
from pathlib import Path

from yak_agent.memory import profile as memory_profile


def _scope(tmp: str) -> Path:
    workspace = Path(tmp) / "u-1" / "w-1"
    workspace.mkdir(parents=True)
    return workspace


def _write(path: Path, data: dict) -> None:
    path.parent.mkdir(parents=True, exist_ok=True)
    path.write_text(json.dumps(data, ensure_ascii=False), encoding="utf-8")


def _read(path: Path) -> dict:
    return json.loads(path.read_text(encoding="utf-8"))


class ProfileStorageTests(unittest.TestCase):
    def test_user_profile_is_the_write_target_and_survives_workspace_switch(self) -> None:
        with tempfile.TemporaryDirectory() as tmp:
            workspace = _scope(tmp)
            memory_profile.remember(workspace, "你叫狗蛋")

            saved = _read(Path(tmp) / "u-1" / "profile.json")
            self.assertEqual(saved["agentName"], "狗蛋")
            self.assertEqual(saved["version"], memory_profile.PROFILE_VERSION)

            other = Path(tmp) / "u-1" / "w-2"
            other.mkdir()
            self.assertEqual(memory_profile.load(other).agent_name, "狗蛋")

    def test_workspace_profile_overrides_user_fields(self) -> None:
        with tempfile.TemporaryDirectory() as tmp:
            workspace = _scope(tmp)
            _write(
                Path(tmp) / "u-1" / "profile.json",
                {"agentName": "狗蛋", "userName": "达摩", "preferences": ["用户偏好 A"]},
            )
            _write(
                workspace / "profile.json",
                {"agentName": "小同", "preferences": ["项目约定 B", "用户偏好 A"]},
            )

            merged = memory_profile.load(workspace)

            self.assertEqual(merged.agent_name, "小同")
            self.assertEqual(merged.user_name, "达摩")
            self.assertEqual(merged.preferences, ["用户偏好 A", "项目约定 B"])

    def test_render_keeps_names_when_preferences_do_not_fit(self) -> None:
        with tempfile.TemporaryDirectory() as tmp:
            workspace = _scope(tmp)
            _write(
                Path(tmp) / "u-1" / "profile.json",
                {
                    "agentName": "狗蛋",
                    "userName": "达摩",
                    "preferences": ["这是一条很长很长的偏好" * 100],
                },
            )

            names_only_block = memory_profile.render(
                memory_profile.Profile(agent_name="狗蛋", user_name="达摩"),
                4000,
            )
            names_only = memory_profile.render(
                memory_profile.load(workspace),
                len(names_only_block.encode("utf-8")) + 20,
            )
            full = memory_profile.render(memory_profile.load(workspace), 4000)

            self.assertIn("助手称呼：狗蛋", names_only)
            self.assertIn("用户称呼：达摩", names_only)
            self.assertNotIn("硬性偏好", names_only)
            self.assertIn("硬性偏好", full)

    def test_bootstrap_uses_existing_personal_digest_cards(self) -> None:
        with tempfile.TemporaryDirectory() as tmp:
            workspace = _scope(tmp)
            card = workspace / "digest" / "personal" / "identity.md"
            card.parent.mkdir(parents=True)
            card.write_text(
                "---\n"
                "bucket: personal\n"
                "description: 'Workspace naming: the assistant is 狗蛋 and the\n"
                "  user is 达摩.'\n"
                "kind: identity\n"
                "---\n\n"
                "# Identity\n",
                encoding="utf-8",
            )

            loaded = memory_profile.load(workspace)
            rendered = memory_profile.render(loaded, 4000)

            self.assertEqual(len(loaded.notes), 1)
            self.assertIn("狗蛋", loaded.notes[0])
            self.assertIn("达摩", loaded.notes[0])
            self.assertIn("历史记忆摘要", rendered)

    def test_bootstrap_stops_once_a_structured_name_exists(self) -> None:
        with tempfile.TemporaryDirectory() as tmp:
            workspace = _scope(tmp)
            card = workspace / "digest" / "personal" / "identity.md"
            card.parent.mkdir(parents=True)
            card.write_text(
                "---\ndescription: 'assistant is 狗蛋'\n---\n",
                encoding="utf-8",
            )
            memory_profile.remember(workspace, "你叫狗蛋")

            loaded = memory_profile.load(workspace)

            self.assertEqual(loaded.notes, [])
            self.assertNotIn("历史记忆摘要", memory_profile.render(loaded, 4000))

    def test_corrupt_profile_is_ignored_and_rewritten(self) -> None:
        with tempfile.TemporaryDirectory() as tmp:
            workspace = _scope(tmp)
            path = Path(tmp) / "u-1" / "profile.json"
            path.parent.mkdir(parents=True, exist_ok=True)
            path.write_text("{not json", encoding="utf-8")

            self.assertEqual(memory_profile.load(workspace).agent_name, "")
            memory_profile.remember(workspace, "你叫狗蛋")

            self.assertEqual(_read(path)["agentName"], "狗蛋")

    def test_block_scalar_description_is_parsed(self) -> None:
        with tempfile.TemporaryDirectory() as tmp:
            workspace = _scope(tmp)
            card = workspace / "digest" / "personal" / "identity.md"
            card.parent.mkdir(parents=True)
            card.write_text(
                "---\ndescription: |\n  the assistant is 狗蛋\n---\n",
                encoding="utf-8",
            )

            self.assertEqual(memory_profile.load(workspace).notes, ["the assistant is 狗蛋"])

    def test_oversized_profile_file_is_ignored(self) -> None:
        with tempfile.TemporaryDirectory() as tmp:
            workspace = _scope(tmp)
            path = Path(tmp) / "u-1" / "profile.json"
            path.parent.mkdir(parents=True, exist_ok=True)
            path.write_text(" " * (70 * 1024), encoding="utf-8")

            self.assertEqual(memory_profile.load(workspace).agent_name, "")

    def test_loaded_preferences_are_capped(self) -> None:
        with tempfile.TemporaryDirectory() as tmp:
            workspace = _scope(tmp)
            _write(
                Path(tmp) / "u-1" / "profile.json",
                {"preferences": [f"preference-{index}" for index in range(15)] + ["x" * 500]},
            )

            loaded = memory_profile.load(workspace)

            self.assertEqual(len(loaded.preferences), 10)
            self.assertTrue(all(len(item) <= 200 for item in loaded.preferences))


class ProfileStatementTests(unittest.TestCase):
    def _remember(self, text: str) -> dict:
        with tempfile.TemporaryDirectory() as tmp:
            workspace = _scope(tmp)
            memory_profile.remember(workspace, text)
            path = Path(tmp) / "u-1" / "profile.json"
            return _read(path) if path.exists() else {}

    def test_explicit_agent_names(self) -> None:
        for text in ("错 你叫狗蛋", "你以后就叫狗蛋", "你的名字是狗蛋", "你名字是狗蛋"):
            with self.subTest(text=text):
                self.assertEqual(self._remember(text)["agentName"], "狗蛋")

    def test_explicit_user_names(self) -> None:
        for text in ("我叫达摩", "我的名字是达摩", "叫我达摩", "我名字是达摩"):
            with self.subTest(text=text):
                self.assertEqual(self._remember(text)["userName"], "达摩")

    def test_pronoun_and_rare_name_are_handled(self) -> None:
        self.assertEqual(self._remember("我叫你"), {})
        self.assertEqual(self._remember("我叫哪吒")["userName"], "哪吒")

    def test_preferences_keep_the_statement(self) -> None:
        saved = self._remember("记住 以后回答必须包含你的名字")
        self.assertEqual(saved["preferences"], ["以后回答必须包含你的名字"])

    def test_name_and_preference_in_one_message(self) -> None:
        saved = self._remember("记住 你叫狗蛋，以后回答要包含名字")
        self.assertEqual(saved["agentName"], "狗蛋")
        self.assertEqual(saved["preferences"], ["以后回答要包含名字"])

    def test_questions_and_verb_phrases_are_not_names(self) -> None:
        for text in ("你叫什么名字", "我叫什么", "你是谁", "你叫狗蛋吗", "你叫一下运维"):
            with self.subTest(text=text):
                self.assertEqual(self._remember(text), {})

    def test_preference_is_redacted_and_deduplicated(self) -> None:
        with tempfile.TemporaryDirectory() as tmp:
            workspace = _scope(tmp)
            memory_profile.remember(workspace, "记住 token=abc123")
            memory_profile.remember(workspace, "记住 token=abc123")

            preferences = _read(Path(tmp) / "u-1" / "profile.json")["preferences"]

            self.assertEqual(len(preferences), 1)
            self.assertIn("<redacted>", preferences[0])

    def test_new_name_supersedes_the_old_one(self) -> None:
        with tempfile.TemporaryDirectory() as tmp:
            workspace = _scope(tmp)
            memory_profile.remember(workspace, "你叫小同")
            memory_profile.remember(workspace, "错 你叫狗蛋")

            self.assertEqual(memory_profile.load(workspace).agent_name, "狗蛋")


if __name__ == "__main__":
    unittest.main()
