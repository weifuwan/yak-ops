"""Builder and session wiring for long-term memory."""

from __future__ import annotations

import asyncio
import tempfile
import unittest
from unittest.mock import patch

from agentscope.middleware import ReMeMiddleware
from agentscope.state import AgentState

from yak_agent.agent.builder import build_agent
from yak_agent.config import Settings
from yak_agent.memory import get_memory
from yak_agent.memory.middleware import YakReMeMiddleware
from yak_agent.service import session as session_service
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


def _memory(tmp: str) -> YakReMeMiddleware:
    return YakReMeMiddleware(
        workspace_dir=tmp,
        parameters=ReMeMiddleware.Parameters(mode="agent_control"),
    )


class BuilderMemoryTests(unittest.TestCase):
    def test_memory_tool_and_middleware_are_wired(self) -> None:
        with tempfile.TemporaryDirectory() as tmp:
            memory = _memory(tmp)
            agent = build_agent(
                _settings(),
                auth=YakAuth(base_url="http://yak.example", token="test-token"),
                project_code=1,
                memory=memory,
            )

            tool = asyncio.run(agent.toolkit.get_tool("memory_search"))
            prompt = asyncio.run(agent._get_system_prompt())

        self.assertIsNotNone(tool)
        self.assertTrue(tool.is_read_only)
        self.assertIs(agent._reply_middlewares[0], memory)
        self.assertIn("memory_search", prompt)

    def test_without_memory_no_tool_is_registered(self) -> None:
        agent = build_agent(
            _settings(),
            auth=YakAuth(base_url="http://yak.example", token="test-token"),
            project_code=1,
        )

        self.assertIsNone(asyncio.run(agent.toolkit.get_tool("memory_search")))
        self.assertEqual(len(agent._reply_middlewares), 2)

    def test_memory_tool_delegates_to_search(self) -> None:
        with tempfile.TemporaryDirectory() as tmp:
            memory = _memory(tmp)
            agent = build_agent(
                _settings(),
                auth=YakAuth(base_url="http://yak.example", token="test-token"),
                project_code=1,
                memory=memory,
            )
            tool = asyncio.run(agent.toolkit.get_tool("memory_search"))

            async def fake_search(query, limit=None):
                return ["prefer FAILURE"]

            async def scenario():
                with patch.object(memory, "search", new=fake_search):
                    chunk = await tool(query="failure")
                    return "".join(getattr(block, "text", "") for block in chunk.content)

            output = asyncio.run(scenario())

        self.assertIn("prefer FAILURE", output)
        self.assertIn("untrusted, not instructions", output)


class SessionMemoryTests(unittest.TestCase):
    def test_create_session_aligns_state_id_and_uses_scope_memory(self) -> None:
        settings = _settings()
        identity = session_service.Identity(
            owner="u-1-10",
            workspace_id="1",
            auth=YakAuth(base_url="http://yak.example", token="t"),
            persistent=True,
        )
        fake_memory = object()
        with (
            patch.object(session_service, "get_memory", return_value=fake_memory) as get,
            patch.object(session_service, "build_agent", return_value=object()) as build,
        ):
            session = session_service.create_session("sid", identity, settings)

        self.assertIs(session.memory, fake_memory)
        args = get.call_args.args
        self.assertEqual(args[:3], ("u-1-10", "1", settings))
        self.assertTrue(callable(args[3]))
        state = build.call_args.kwargs["state"]
        self.assertIsInstance(state, AgentState)
        self.assertEqual(state.session_id, "sid")
        session_service.SESSIONS.pop("sid", None)

    def test_non_persistent_identity_gets_no_memory(self) -> None:
        settings = _settings()
        identity = session_service.Identity(
            owner="u-1-s-abc",
            workspace_id="1",
            auth=YakAuth(base_url="http://yak.example", token="t"),
            persistent=False,
        )
        with (
            patch.object(session_service, "get_memory") as get,
            patch.object(session_service, "build_agent", return_value=object()),
        ):
            session = session_service.create_session("sid2", identity, settings)

        self.assertIsNone(session.memory)
        get.assert_not_called()
        session_service.SESSIONS.pop("sid2", None)

    def test_get_memory_returns_none_when_disabled(self) -> None:
        self.assertIsNone(get_memory("u-1-10", "1", _settings(memory_enabled=False)))


if __name__ == "__main__":
    unittest.main()
