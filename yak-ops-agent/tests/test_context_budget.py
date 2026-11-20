"""Context headroom gate before long-term memory (PR A: 1.7)."""

from __future__ import annotations

import tempfile
import unittest
from pathlib import Path
from unittest.mock import patch

from yak_agent.agent.budget import (
    COMPRESSION_TOOL_TOKENS,
    ContextBudgetError,
    check_context_budget,
    enforce_context_budget,
    estimate_tokens,
)
from yak_agent.agent.builder import build_agent
from yak_agent.config import Settings
from yak_agent.service import persistence
from yak_agent.service import session as session_service
from yak_agent.service.errors import ContextBudgetExceeded


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


class EstimateTests(unittest.TestCase):
    def test_ascii_and_cjk_use_byte_estimation(self) -> None:
        self.assertEqual(estimate_tokens(""), 0)
        self.assertEqual(estimate_tokens("abcd"), 1)
        self.assertEqual(estimate_tokens("你好"), 2)  # 6 UTF-8 bytes / 4 -> ceil


class BudgetReportTests(unittest.TestCase):
    def test_room_left_passes(self) -> None:
        report = check_context_budget(
            context_size=10000,
            trigger_ratio=0.7,
            system_prompt="x" * 1000,
            tools=[],
            memory_budget_tokens=100,
        )

        self.assertTrue(report.ok)
        self.assertEqual(report.threshold, 7000)
        self.assertGreater(report.available, 0)

    def test_base_prompt_over_threshold_fails(self) -> None:
        report = check_context_budget(
            context_size=1000,
            trigger_ratio=0.7,
            system_prompt="x" * 4000,
            tools=[],
        )

        self.assertFalse(report.ok)
        self.assertLessEqual(report.available, 0)

    def test_memory_and_reserved_tool_tokens_count(self) -> None:
        without = check_context_budget(
            context_size=4000,
            trigger_ratio=0.7,
            system_prompt="x" * 100,
            tools=[],
        )
        with_memory = check_context_budget(
            context_size=4000,
            trigger_ratio=0.7,
            system_prompt="x" * 100,
            tools=[],
            memory_budget_tokens=2000,
            reserved_tool_tokens=COMPRESSION_TOOL_TOKENS,
        )

        self.assertLess(with_memory.available, without.available)
        self.assertEqual(with_memory.used - without.used, 2000 + COMPRESSION_TOOL_TOKENS)

    def test_enforce_raises_with_actionable_message(self) -> None:
        report = check_context_budget(
            context_size=1000,
            trigger_ratio=0.7,
            system_prompt="x" * 4000,
            tools=[],
        )

        with self.assertRaises(ContextBudgetError) as ctx:
            enforce_context_budget(report)
        self.assertIn("LLM_CONTEXT_SIZE", str(ctx.exception))
        self.assertIn("上下文窗口不足", str(ctx.exception))


class BuilderBudgetTests(unittest.TestCase):
    def test_over_budget_memory_fails_session_creation(self) -> None:
        settings = _settings(llm_context_size=2000, llm_memory_budget_tokens=5000)

        with self.assertRaises(ContextBudgetError):
            build_agent(settings, project_code=1)

    def test_memory_budget_is_optional(self) -> None:
        agent = build_agent(_settings(), project_code=1)

        self.assertEqual(agent.model.context_size, 64000)


class SessionConversionTests(unittest.TestCase):
    def test_budget_error_becomes_domain_error(self) -> None:
        with tempfile.TemporaryDirectory() as tmp:
            original = persistence.SESSION_DIR
            persistence.SESSION_DIR = Path(tmp)
            identity = session_service.Identity(
                owner="u-1",
                workspace_id="1",
                auth=None,
                persistent=True,
            )
            try:
                with patch.object(
                    session_service,
                    "build_agent",
                    side_effect=ContextBudgetError("too small"),
                ):
                    with self.assertRaises(ContextBudgetExceeded):
                        session_service.create_session("sid", identity, _settings())
            finally:
                persistence.SESSION_DIR = original

    def test_api_maps_budget_error_to_422(self) -> None:
        from http import HTTPStatus

        from yak_agent.api import errors

        self.assertEqual(
            errors._STATUS_BY_ERROR[ContextBudgetExceeded],
            HTTPStatus.UNPROCESSABLE_ENTITY,
        )


if __name__ == "__main__":
    unittest.main()
