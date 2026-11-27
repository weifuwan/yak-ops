"""Pinned profile wiring: system prompt, rebuild refresh and the zero budget."""

from __future__ import annotations

import asyncio
import tempfile
import unittest
from pathlib import Path

from agentscope.middleware import ReMeMiddleware

from yak_agent.agent.builder import build_agent
from yak_agent.config import Settings
from yak_agent.memory import profile as memory_profile
from yak_agent.memory.middleware import YakReMeMiddleware
from yak_agent.yak_client import YakAuth


def _settings(**overrides) -> Settings:
    values = {
        "llm_base_url": "http://llm.example",
        "llm_api_key": "test-key",
        "llm_model": "test-model",
        "yak_ops_base_url": "http://yak.example",
        "yak_ops_token": "",
        "llm_context_size": 64000,
    }
    values.update(overrides)
    return Settings(**values)


def _scope(tmp: str) -> Path:
    workspace = Path(tmp) / "u-1" / "w-1"
    workspace.mkdir(parents=True)
    return workspace


def _memory(workspace: Path, budget_tokens: int = 300) -> YakReMeMiddleware:
    return YakReMeMiddleware(
        workspace_dir=str(workspace),
        parameters=ReMeMiddleware.Parameters(mode="agent_control"),
        profile_budget_tokens=budget_tokens,
    )


def _prompt(memory: YakReMeMiddleware | None) -> str:
    agent = build_agent(
        _settings(),
        auth=YakAuth(base_url="http://yak.example", token="test-token"),
        project_code=1,
        memory=memory,
    )
    return asyncio.run(agent._get_system_prompt())


class IdentityMemoryTests(unittest.TestCase):
    def test_profile_identity_is_in_the_system_prompt(self) -> None:
        with tempfile.TemporaryDirectory() as tmp:
            workspace = _scope(tmp)
            memory_profile.remember(workspace, "你叫狗蛋")
            memory_profile.remember(workspace, "我叫达摩")
            memory_profile.remember(workspace, "记住 以后回答必须包含你的名字")
            memory = _memory(workspace)

            prompt = _prompt(memory)

        self.assertIn("## 工作区记忆", prompt)
        self.assertIn("助手称呼：狗蛋", prompt)
        self.assertIn("用户称呼：达摩", prompt)
        self.assertIn("硬性偏好：以后回答必须包含你的名字", prompt)
        self.assertIn("优先于默认角色描述", prompt)
        self.assertIn("不要声称自己没有名字", prompt)

    def test_memory_search_tool_is_still_registered(self) -> None:
        with tempfile.TemporaryDirectory() as tmp:
            workspace = _scope(tmp)
            memory = _memory(workspace)
            agent = build_agent(
                _settings(),
                auth=YakAuth(base_url="http://yak.example", token="test-token"),
                project_code=1,
                memory=memory,
            )
            tool = asyncio.run(agent.toolkit.get_tool("memory_search"))

        self.assertIsNotNone(tool)
        self.assertTrue(tool.is_read_only)

    def test_without_memory_no_profile_block_is_added(self) -> None:
        prompt = _prompt(None)

        self.assertNotIn("## 工作区记忆", prompt)

    def test_rebuild_picks_up_a_new_name(self) -> None:
        with tempfile.TemporaryDirectory() as tmp:
            workspace = _scope(tmp)
            memory = _memory(workspace)
            before = _prompt(memory)

            memory_profile.remember(workspace, "错 你叫狗蛋")
            after = _prompt(memory)

        self.assertNotIn("助手称呼：狗蛋", before)
        self.assertIn("助手称呼：狗蛋", after)

    def test_zero_budget_disables_reading_and_writing(self) -> None:
        with tempfile.TemporaryDirectory() as tmp:
            workspace = _scope(tmp)
            memory = _memory(workspace, budget_tokens=0)

            memory.remember("你叫狗蛋")

            self.assertIsNone(memory.profile())
            self.assertFalse((Path(tmp) / "u-1" / "profile.json").exists())


if __name__ == "__main__":
    unittest.main()
