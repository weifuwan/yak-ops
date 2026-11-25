"""Server clock tool and current-time skill."""

from __future__ import annotations

import asyncio
import json
import time
import unittest
from datetime import UTC, datetime
from pathlib import Path
from zoneinfo import ZoneInfo

from agentscope.skill import LocalSkillLoader

from yak_agent.agent.prompt import compose_system_prompt
from yak_agent.config import PLATFORM_TIMEZONE, Settings
from yak_agent.tools.builder import build_tools
from yak_agent.tools.time_tools import build_time_tools
from yak_agent.yak_client import YakAuth

_SKILLS_DIR = Path(__file__).resolve().parent.parent / "yak_agent" / "skills"
_WEEKDAYS = ("星期一", "星期二", "星期三", "星期四", "星期五", "星期六", "星期日")


def _settings() -> Settings:
    return Settings(
        llm_base_url="http://llm.example",
        llm_api_key="test-key",
        llm_model="test-model",
        yak_ops_base_url="http://yak.example",
        yak_ops_token="test-token",
    )


class CurrentTimeToolTests(unittest.TestCase):
    @classmethod
    def setUpClass(cls) -> None:
        cls.tools = {
            tool.name: tool
            for tool in build_tools(
                _settings(),
                auth=YakAuth(base_url="http://yak.example", token="test-token"),
                project_code=1,
            )
        }

    def test_tool_is_registered_as_read_only(self) -> None:
        tool = self.tools.get("get_current_time")

        self.assertIsNotNone(tool)
        self.assertTrue(tool.is_read_only)
        self.assertTrue(tool.is_concurrency_safe)

    def test_returns_platform_and_utc_clock(self) -> None:
        chunk = asyncio.run(self.tools["get_current_time"]())
        payload = json.loads(chunk.content[0].text)
        platform = datetime.fromisoformat(payload["iso"])
        utc = datetime.fromisoformat(payload["utc"].replace("Z", "+00:00"))

        self.assertEqual(payload["timezone"], PLATFORM_TIMEZONE)
        self.assertEqual(
            platform.utcoffset(), datetime.now(ZoneInfo(PLATFORM_TIMEZONE)).utcoffset()
        )
        self.assertTrue(payload["utc"].endswith("Z"))
        self.assertLess(abs((utc - datetime.now(UTC)).total_seconds()), 60)
        self.assertLess(abs(payload["epochMillis"] - time.time() * 1000), 60_000)
        self.assertEqual(payload["apiUtcTime"], utc.strftime("%Y-%m-%d %H:%M:%S"))
        self.assertEqual(payload["date"], platform.strftime("%Y-%m-%d"))
        self.assertEqual(payload["time"], platform.strftime("%H:%M:%S"))
        self.assertEqual(payload["weekday"], _WEEKDAYS[platform.weekday()])

    def test_build_time_tools_is_independent_of_workspace(self) -> None:
        tool = build_time_tools()[0]

        self.assertEqual(tool.name, "get_current_time")
        self.assertTrue(tool.is_read_only)


class CurrentTimeSkillTests(unittest.TestCase):
    def test_skill_loads_and_references_the_tool(self) -> None:
        loader = LocalSkillLoader(directory=str(_SKILLS_DIR), scan_subdir=True)
        skills = {skill.name: skill for skill in asyncio.run(loader.list_skills())}

        self.assertIn("current-time", skills)
        skill = skills["current-time"]
        self.assertIn("get_current_time", skill.description)
        self.assertIn("get_current_time", skill.markdown)
        self.assertIn("UTC", skill.markdown)

    def test_system_prompt_requires_the_tool_for_time_questions(self) -> None:
        prompt = compose_system_prompt(["data-sync"])
        self.assertIn("get_current_time", prompt)
        self.assertIn("current-time", prompt)


if __name__ == "__main__":
    unittest.main()
