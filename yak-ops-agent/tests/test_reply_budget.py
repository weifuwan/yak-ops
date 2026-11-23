"""Cache-aware reply budget accounting."""

from __future__ import annotations

import asyncio
import unittest
from types import SimpleNamespace

from agentscope.event import ModelCallEndEvent, ReplyStartEvent

from yak_agent.agent.budget import CacheAwareReplyBudgetControlMiddleware
from yak_agent.agent.builder import build_agent
from yak_agent.config import Settings
from yak_agent.yak_client import YakAuth


def _settings() -> Settings:
    return Settings(
        llm_base_url="http://llm.example",
        llm_api_key="test-key",
        llm_model="test-model",
        yak_ops_base_url="http://yak.example",
        yak_ops_token="test-token",
    )


class CacheAwareReplyBudgetTests(unittest.TestCase):
    def _run(self, events: list[object]):
        middleware = CacheAwareReplyBudgetControlMiddleware(
            token_budget=25000,
            input_token_weight=1.0,
            output_token_weight=2.0,
        )
        agent = SimpleNamespace(
            state=SimpleNamespace(context=[], middle_context={}),
        )

        async def next_handler(**kwargs):
            for event in events:
                yield event

        async def scenario():
            async for _ in middleware.on_reply(agent, {}, next_handler):
                pass
            return await middleware.get_middleware_key()

        key = asyncio.run(scenario())
        return agent, key

    def test_cached_input_is_not_billed(self) -> None:
        agent, key = self._run(
            [
                ReplyStartEvent(session_id="s", reply_id="r", name="agent"),
                ModelCallEndEvent(
                    reply_id="r",
                    input_tokens=43000,
                    output_tokens=1000,
                    cache_input_tokens=38000,
                ),
            ]
        )

        self.assertEqual(agent.state.middle_context[key]["r"], 7000)

    def test_uncached_input_is_billed(self) -> None:
        agent, key = self._run(
            [
                ReplyStartEvent(session_id="s", reply_id="r", name="agent"),
                ModelCallEndEvent(
                    reply_id="r",
                    input_tokens=10000,
                    output_tokens=1000,
                    cache_input_tokens=0,
                ),
            ]
        )

        self.assertEqual(agent.state.middle_context[key]["r"], 12000)

    def test_cache_larger_than_input_does_not_go_negative(self) -> None:
        agent, key = self._run(
            [
                ReplyStartEvent(session_id="s", reply_id="r", name="agent"),
                ModelCallEndEvent(
                    reply_id="r",
                    input_tokens=5000,
                    output_tokens=1000,
                    cache_input_tokens=6000,
                ),
            ]
        )

        self.assertEqual(agent.state.middle_context[key]["r"], 2000)

    def test_new_reply_resets_the_budget(self) -> None:
        agent, key = self._run(
            [
                ReplyStartEvent(session_id="s", reply_id="first", name="agent"),
                ModelCallEndEvent(
                    reply_id="first",
                    input_tokens=43000,
                    output_tokens=1000,
                    cache_input_tokens=38000,
                ),
                ReplyStartEvent(session_id="s", reply_id="second", name="agent"),
                ModelCallEndEvent(
                    reply_id="second",
                    input_tokens=10000,
                    output_tokens=500,
                    cache_input_tokens=0,
                ),
            ]
        )

        self.assertEqual(agent.state.middle_context[key], {"second": 11000})

    def test_long_session_cached_context_stays_under_budget(self) -> None:
        events = [ReplyStartEvent(session_id="s", reply_id="r", name="agent")]
        for _ in range(2):
            events.append(
                ModelCallEndEvent(
                    reply_id="r",
                    input_tokens=43278,
                    output_tokens=525,
                    cache_input_tokens=38528,
                )
            )

        agent, key = self._run(events)

        self.assertEqual(agent.state.middle_context[key]["r"], 11600)
        self.assertLess(agent.state.middle_context[key]["r"], 25000)

    def test_budget_still_forces_wrap_up_when_billable_cost_exceeds_budget(self) -> None:
        middleware = CacheAwareReplyBudgetControlMiddleware(token_budget=25000)
        agent = SimpleNamespace(
            name="agent",
            state=SimpleNamespace(
                context=[],
                middle_context={},
                reply_id="r",
                reply_context=SimpleNamespace(
                    structured_schema=None,
                    structured_output=None,
                ),
            ),
        )

        async def reply_events(**kwargs):
            yield ReplyStartEvent(session_id="s", reply_id="r", name="agent")
            yield ModelCallEndEvent(
                reply_id="r",
                input_tokens=60000,
                output_tokens=1000,
                cache_input_tokens=30000,
            )

        async def scenario():
            async for _ in middleware.on_reply(agent, {}, reply_events):
                pass
            captured = {}

            async def capture_reasoning(**kwargs):
                captured.update(kwargs)
                yield kwargs

            async for _ in middleware.on_reasoning(agent, {}, capture_reasoning):
                pass
            return captured

        captured = asyncio.run(scenario())

        self.assertEqual(captured["tool_choice"].mode, "none")
        self.assertEqual(agent.state.context[-1].content[0].type, "hint")

    def test_builder_uses_cache_aware_middleware(self) -> None:
        agent = build_agent(
            _settings(),
            auth=YakAuth(base_url="http://yak.example", token="test-token"),
            project_code=1,
        )

        self.assertTrue(
            any(
                isinstance(middleware, CacheAwareReplyBudgetControlMiddleware)
                for middleware in agent._reply_middlewares
            )
        )
        self.assertTrue(
            any(
                isinstance(middleware, CacheAwareReplyBudgetControlMiddleware)
                for middleware in agent._reasoning_middlewares
            )
        )


if __name__ == "__main__":
    unittest.main()
