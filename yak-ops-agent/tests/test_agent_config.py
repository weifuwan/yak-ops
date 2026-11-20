"""Agent assembly wiring: context budget, compression ratios and timezone."""

from __future__ import annotations

import asyncio
import json
import os
import tempfile
import unittest
from pathlib import Path
from unittest.mock import patch

from yak_agent import config
from yak_agent.agent.builder import build_agent
from yak_agent.config import Settings
from yak_agent.workspace import SessionWorkspace
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


class AgentConfigTests(unittest.TestCase):
    @classmethod
    def setUpClass(cls) -> None:
        cls.agent = build_agent(
            _settings(),
            auth=YakAuth(base_url="http://yak.example", token="test-token"),
            project_code=1,
        )

    def test_model_context_size_comes_from_settings(self) -> None:
        self.assertEqual(self.agent.model.context_size, 64000)

    def test_compression_triggers_before_the_window_fills(self) -> None:
        self.assertEqual(self.agent.context_config.trigger_ratio, 0.7)
        self.assertEqual(self.agent.context_config.reserve_ratio, 0.2)

    def test_injected_time_uses_platform_timezone(self) -> None:
        self.assertEqual(self.agent.injection_config.timezone, "Asia/Shanghai")

    def test_runtime_state_hint_carries_platform_timezone(self) -> None:
        async def first_hint() -> str:
            agent = build_agent(
                _settings(),
                auth=YakAuth(base_url="http://yak.example", token="test-token"),
                project_code=1,
            )
            async for event in agent._inject_runtime_state():
                return event.hint
            raise AssertionError("runtime state was not injected")

        hint = asyncio.run(first_hint())

        self.assertIn("<timezone>Asia/Shanghai</timezone>", hint)
        self.assertIn("<current-time>", hint)

    def test_tool_results_keep_enough_context_for_logs(self) -> None:
        self.assertEqual(self.agent.context_config.tool_result_limit, 4000)

    def test_interruption_message_is_localized(self) -> None:
        self.assertIn("已中断", self.agent.react_config.interruption_message)

    def test_compression_tool_is_enabled(self) -> None:
        self.assertTrue(self.agent.context_config.compression_tool_enabled)
        self.assertEqual(self.agent.context_config.context_buffer_ratio, 0.2)

    def test_compression_tool_is_registered_for_model_input(self) -> None:
        model_input = asyncio.run(self.agent._prepare_model_input())

        self.assertIn("CompressContext", json.dumps(model_input["tools"], ensure_ascii=False))


class WorkspaceWiringTests(unittest.TestCase):
    def test_workspace_is_used_as_offloader_and_documented(self) -> None:
        with tempfile.TemporaryDirectory() as tmp:
            workspace = SessionWorkspace(Path(tmp) / "workspace")
            agent = build_agent(
                _settings(),
                workspace=workspace,
                auth=YakAuth(base_url="http://yak.example", token="test-token"),
                project_code=1,
            )
            prompt = asyncio.run(agent._get_system_prompt())

        self.assertIs(agent.offloader, workspace)
        self.assertIn("offload/context.jsonl", prompt)
        self.assertIn("read_workspace_file", prompt)

    def test_no_workspace_keeps_offloader_and_prompt_clean(self) -> None:
        agent = build_agent(
            _settings(),
            auth=YakAuth(base_url="http://yak.example", token="test-token"),
            project_code=1,
        )
        prompt = asyncio.run(agent._get_system_prompt())

        self.assertIsNone(agent.offloader)
        self.assertNotIn("offload/context.jsonl", prompt)


class SettingsLoadTests(unittest.TestCase):
    _BASE_ENV = {
        "LLM_BASE_URL": "http://llm.example",
        "LLM_API_KEY": "test-key",
        "LLM_MODEL": "test-model",
    }

    def _load(self, **overrides: str) -> Settings:
        with (
            patch.dict(os.environ, {**self._BASE_ENV, **overrides}, clear=True),
            patch.object(config, "_load_env_file", return_value={}),
        ):
            return Settings.load()

    def test_context_and_confirmation_defaults(self) -> None:
        settings = self._load()

        self.assertEqual(settings.llm_context_size, 128000)
        self.assertEqual(settings.confirm_timeout_seconds, 600)
        self.assertEqual(settings.llm_memory_budget_tokens, 1500)
        self.assertTrue(settings.memory_enabled)
        self.assertEqual(settings.memory_top_k, 5)
        self.assertEqual(settings.memory_dir.name, "memory")

    def test_context_and_confirmation_env_overrides(self) -> None:
        settings = self._load(
            LLM_CONTEXT_SIZE="64000",
            YAK_AGENT_CONFIRM_TIMEOUT_SECONDS="120",
            YAK_AGENT_MEMORY_BUDGET_TOKENS="3000",
        )

        self.assertEqual(settings.llm_context_size, 64000)
        self.assertEqual(settings.confirm_timeout_seconds, 120)
        self.assertEqual(settings.llm_memory_budget_tokens, 3000)


if __name__ == "__main__":
    unittest.main()
