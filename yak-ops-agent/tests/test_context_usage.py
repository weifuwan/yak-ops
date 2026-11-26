"""Context capacity payload: raw usage, component breakdown, cache hit rate."""

from __future__ import annotations

import asyncio
import json
import tempfile
import unittest
from pathlib import Path
from types import SimpleNamespace
from unittest.mock import AsyncMock, patch

from agentscope.event import ModelCallEndEvent, ReplyStartEvent
from agentscope.message import AssistantMsg, UserMsg

from yak_agent.agent.budget import (
    CONTEXT_TRIGGER_RATIO,
    RAW_USAGE_KEY,
    CacheAwareReplyBudgetControlMiddleware,
    cache_hit_rate,
    context_payload,
    describe_context,
)
from yak_agent.agent.builder import build_agent
from yak_agent.api.sse import forward_event
from yak_agent.config import Settings
from yak_agent.service import persistence
from yak_agent.service.session import Session
from yak_agent.yak_client import YakAuth


def _settings() -> Settings:
    return Settings(
        llm_base_url="http://llm.example",
        llm_api_key="test-key",
        llm_model="test-model",
        yak_ops_base_url="http://yak.example",
        yak_ops_token="",
    )


class RawUsageRecordingTests(unittest.TestCase):
    def test_middleware_keeps_raw_usage_for_the_panel(self) -> None:
        middleware = CacheAwareReplyBudgetControlMiddleware(
            token_budget=25000,
            input_token_weight=1.0,
            output_token_weight=2.0,
        )
        agent = SimpleNamespace(state=SimpleNamespace(context=[], middle_context={}))
        events = [
            ReplyStartEvent(session_id="s", reply_id="r", name="agent"),
            ModelCallEndEvent(
                reply_id="r",
                input_tokens=43000,
                output_tokens=1000,
                cache_input_tokens=38000,
                cache_creation_input_tokens=500,
            ),
        ]

        async def scenario():
            seen = []

            async def next_handler(**kwargs):
                for event in events:
                    yield event

            async for event in middleware.on_reply(agent, {}, next_handler):
                seen.append(event)
            return seen

        seen = asyncio.run(scenario())

        self.assertEqual(
            agent.state.middle_context[RAW_USAGE_KEY],
            {
                "inputTokens": 43000,
                "outputTokens": 1000,
                "cacheInputTokens": 38000,
                "cacheCreationInputTokens": 500,
            },
        )
        # Downstream billing semantics are unchanged.
        call_end = next(event for event in seen if isinstance(event, ModelCallEndEvent))
        self.assertEqual(call_end.input_tokens, 5000)
        self.assertEqual(call_end.cache_input_tokens, 0)


class CacheHitRateTests(unittest.TestCase):
    def test_hit_rate_is_cache_reads_over_input(self) -> None:
        self.assertEqual(
            cache_hit_rate({"inputTokens": 1000, "cacheInputTokens": 985}),
            0.985,
        )

    def test_hit_rate_is_clamped_and_zero_safe(self) -> None:
        self.assertEqual(cache_hit_rate({"inputTokens": 100, "cacheInputTokens": 200}), 1.0)
        self.assertEqual(cache_hit_rate({}), 0.0)
        self.assertEqual(cache_hit_rate(None), 0.0)


class ContextPayloadTests(unittest.TestCase):
    def test_payload_carries_window_threshold_and_ratio(self) -> None:
        payload = context_payload(
            used_tokens=356000,
            limit_tokens=1000000,
            average_cache_hit_rate=0.985,
            compressed=True,
            breakdown=[{"key": "messages", "label": "消息", "tokens": 356000, "ratio": 1.0}],
        )

        self.assertEqual(payload["usedTokens"], 356000)
        self.assertEqual(payload["limitTokens"], 1000000)
        self.assertEqual(payload["triggerTokens"], int(1000000 * CONTEXT_TRIGGER_RATIO))
        self.assertEqual(payload["ratio"], 0.356)
        self.assertEqual(payload["averageCacheHitRate"], 0.985)
        self.assertTrue(payload["compressed"])
        self.assertEqual(payload["breakdown"][0]["key"], "messages")

    def test_payload_without_a_window_stays_zero(self) -> None:
        payload = context_payload(used_tokens=100, limit_tokens=0)
        self.assertEqual(payload["ratio"], 0.0)
        self.assertEqual(payload["triggerTokens"], 0)


class DescribeContextTests(unittest.TestCase):
    def test_breakdown_sums_to_the_real_prompt_size(self) -> None:
        agent = build_agent(
            _settings(),
            auth=YakAuth(base_url="http://yak.example", token="t"),
            project_code=1,
        )
        agent.state.context = [
            UserMsg(name="user", content="分析订单表的同步失败原因 " * 20),
            AssistantMsg(name="agent", content="我先看最近的失败实例 " * 20),
        ]
        agent.state.summary = "之前已经确认数据源连通性正常 " * 10

        payload = asyncio.run(
            describe_context(
                agent,
                used_tokens=20000,
                limit_tokens=128000,
                average_cache_hit_rate=0.9,
            )
        )

        self.assertEqual(payload["usedTokens"], 20000)
        self.assertEqual(payload["limitTokens"], 128000)
        self.assertEqual(payload["triggerTokens"], int(128000 * CONTEXT_TRIGGER_RATIO))
        self.assertTrue(payload["compressed"])
        keys = [item["key"] for item in payload["breakdown"]]
        self.assertEqual(keys, ["messages", "tools", "skills", "systemPrompt", "other"])
        self.assertEqual(sum(item["tokens"] for item in payload["breakdown"]), 20000)
        self.assertAlmostEqual(
            sum(item["ratio"] for item in payload["breakdown"]),
            1.0,
            places=3,
        )


class UsageEventTests(unittest.IsolatedAsyncioTestCase):
    async def test_usage_event_carries_raw_context_size(self) -> None:
        state = SimpleNamespace(
            middle_context={
                RAW_USAGE_KEY: {
                    "inputTokens": 43000,
                    "outputTokens": 1000,
                    "cacheInputTokens": 38000,
                    "cacheCreationInputTokens": 500,
                }
            }
        )
        session = Session(
            sid="sid",
            agent=SimpleNamespace(state=state),
            workspace=object(),
            context_limit_tokens=128000,
        )

        with patch("yak_agent.api.sse.describe_context", new=AsyncMock(return_value=None)):
            await forward_event(
                session,
                ModelCallEndEvent(
                    reply_id="r",
                    input_tokens=5000,
                    output_tokens=1000,
                    cache_input_tokens=0,
                ),
            )

        event = session.event_log.events[-1]
        self.assertEqual(event["type"], "usage")
        self.assertEqual(event["contextTokens"], 43000)
        self.assertEqual(session.context_tokens, 43000)
        self.assertEqual(session.raw_usage["cacheInputTokens"], 38000)
        self.assertEqual(cache_hit_rate(session.raw_usage), round(38000 / 43000, 4))


class ContextPersistenceTests(unittest.TestCase):
    def test_meta_round_trips_context_fields(self) -> None:
        previous = persistence.SESSION_DIR
        with tempfile.TemporaryDirectory() as tmp:
            persistence.SESSION_DIR = Path(tmp)
            try:
                session = Session(
                    sid="sid",
                    agent=SimpleNamespace(state=None),
                    workspace=object(),
                    owner="u-1",
                    workspace_id="1",
                )
                session.context_tokens = 9000
                session.raw_usage = {"inputTokens": 10000, "cacheInputTokens": 9000}
                session.context_snapshot = context_payload(
                    used_tokens=9000,
                    limit_tokens=10000,
                    average_cache_hit_rate=0.9,
                )

                self.assertTrue(persistence.persist(session))
                meta = json.loads(
                    persistence.meta_path("sid").read_text(encoding="utf-8"),
                )

                self.assertEqual(meta["contextTokens"], 9000)
                self.assertEqual(meta["rawUsage"]["cacheInputTokens"], 9000)
                self.assertEqual(meta["context"]["usedTokens"], 9000)
            finally:
                persistence.SESSION_DIR = previous


if __name__ == "__main__":
    unittest.main()
