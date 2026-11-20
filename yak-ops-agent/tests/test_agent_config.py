"""Agent assembly wiring: context budget, compression ratios and timezone."""

from __future__ import annotations

import unittest

from yak_agent.agent.builder import build_agent
from yak_agent.config import Settings
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

    def test_tool_results_keep_enough_context_for_logs(self) -> None:
        self.assertEqual(self.agent.context_config.tool_result_limit, 4000)


if __name__ == "__main__":
    unittest.main()
