"""Skill layout and the data-sync task creation skill."""

from __future__ import annotations

import asyncio
import json
import re
import unittest
from pathlib import Path

from agentscope.skill import LocalSkillLoader

from yak_agent.agent.prompt import compose_system_prompt

_SKILLS_DIR = Path(__file__).resolve().parent.parent / "yak_agent" / "skills"


def _skill_paths() -> dict[str, str]:
    loader = LocalSkillLoader(directory=str(_SKILLS_DIR), scan_subdir=True)
    return {
        skill.name: Path(skill.dir).relative_to(_SKILLS_DIR).as_posix()
        for skill in asyncio.run(loader.list_skills())
    }


class SkillLayoutTests(unittest.TestCase):
    def test_skills_are_grouped_by_domain(self) -> None:
        paths = _skill_paths()

        self.assertEqual(paths["data-sync-create"], "data-sync/create")
        self.assertEqual(paths["data-sync-troubleshooting"], "data-sync/troubleshooting")
        self.assertEqual(paths["current-time"], "platform/current-time")


class CreateTaskSkillTests(unittest.TestCase):
    @classmethod
    def setUpClass(cls) -> None:
        loader = LocalSkillLoader(directory=str(_SKILLS_DIR), scan_subdir=True)
        skills = {skill.name: skill for skill in asyncio.run(loader.list_skills())}
        cls.skill = skills["data-sync-create"]

    def test_description_is_discoverable(self) -> None:
        self.assertIn("新建或修改同步任务", self.skill.description)

    def test_skeleton_is_valid_and_has_the_required_layers(self) -> None:
        blocks = re.findall(r"```json\n(.*?)```", self.skill.markdown, re.DOTALL)
        for block in blocks:
            json.loads(block)
        skeleton = next(block for block in blocks if '"datasource-id": "2"' in block)
        payload = json.loads(skeleton)

        definition = payload["definition"]
        self.assertEqual(definition["source"]["type"], "mysql")
        self.assertEqual(definition["source"]["datasource-id"], "2")
        self.assertEqual(definition["sink"]["type"], "jdbc")
        self.assertEqual(len(definition["route"]), 1)
        self.assertEqual(definition["pipeline"]["execution.runtime-mode"], "STREAMING")

    def test_skill_covers_modes_routes_and_stop_rule(self) -> None:
        for expected in (
            "scan.startup.mode",
            "latest-offset",
            "snapshot",
            "route: every source table",
            "replace-symbol",
            "止损规则",
            "禁止猜",
        ):
            with self.subTest(expected=expected):
                self.assertIn(expected, self.skill.markdown)

    def test_database_scope_pattern_is_json_escaped(self) -> None:
        # Flink CDC's database pattern is "db.\.*"; JSON text needs "\\."
        self.assertIn(r'"tables": "ecommerce_demo.\\.*"', self.skill.markdown)
        self.assertIn(r'"source-table": "ecommerce_demo.\\.*"', self.skill.markdown)

    def test_system_prompt_points_to_the_create_skill(self) -> None:
        self.assertIn("data-sync-create", compose_system_prompt(["data-sync"]))


if __name__ == "__main__":
    unittest.main()
