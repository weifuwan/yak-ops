"""Agent on_acting audit middleware."""

from __future__ import annotations

import asyncio
import json
import unittest
from types import SimpleNamespace
from unittest.mock import patch

from agentscope.agent import Agent
from agentscope.credential import OpenAICredential
from agentscope.message import TextBlock, ToolCallBlock, ToolResultState
from agentscope.model import OpenAIChatModel
from agentscope.state import AgentState
from agentscope.tool import FunctionTool, ToolChunk, Toolkit, ToolResponse

from yak_agent.tools.audit_middleware import ToolAuditMiddleware


def _agent() -> SimpleNamespace:
    return SimpleNamespace(state=AgentState(session_id="state-sid"))


def _middleware() -> ToolAuditMiddleware:
    return ToolAuditMiddleware(caller="u-1-10", workspace_id=1)


async def _collect(middleware, tool_call, handler):
    items = []
    async for item in middleware.on_acting(_agent(), {"tool_call": tool_call}, handler):
        items.append(item)
    return items


def _tool_call(input_value: str) -> ToolCallBlock:
    return ToolCallBlock(id="call-1", name="create_sync_task", input=input_value)


class ToolAuditMiddlewareTests(unittest.TestCase):
    def test_success_records_sanitized_parameters_and_result_metadata(self) -> None:
        payload = json.dumps(
            {
                "config_json": '{"url":"jdbc:mysql://u:p@h/db"}',
                "password": "secret",
                "name": "task",
                "code": "print(1)",
            }
        )
        tool_call = _tool_call(payload)

        async def handler(**kwargs):
            yield ToolChunk(content=[TextBlock(text="ok")])
            yield ToolResponse(
                state=ToolResultState.SUCCESS,
                metadata={"backend": "cocoon", "exitCode": 0, "durationMs": 12},
            )

        with patch("yak_agent.tools.audit_middleware.audit.record_tool_call") as record:
            items = asyncio.run(_collect(_middleware(), tool_call, handler))

        self.assertEqual(len(items), 2)
        record.assert_called_once()
        name, params, ok, duration, error = record.call_args.args
        self.assertEqual(name, "create_sync_task")
        self.assertTrue(ok)
        self.assertIsNone(error)
        self.assertGreaterEqual(duration, 0)
        self.assertEqual(params["toolCallId"], "call-1")
        self.assertEqual(params["config_json"], "<json omitted>")
        self.assertEqual(params["password"], "<redacted>")
        self.assertEqual(params["name"], "task")
        self.assertEqual(params["code"], {"chars": len("print(1)")})
        self.assertEqual(params["session"], "state-sid")
        self.assertEqual(params["caller"], "u-1-10")
        self.assertEqual(params["workspace"], 1)
        self.assertEqual(params["backend"], "cocoon")
        self.assertEqual(params["exitCode"], 0)
        self.assertEqual(params["durationMs"], 12)

    def test_error_state_records_failure(self) -> None:
        async def handler(**kwargs):
            yield ToolChunk(content=[TextBlock(text="failed")], state=ToolResultState.ERROR)
            yield ToolResponse(state=ToolResultState.ERROR)

        with patch("yak_agent.tools.audit_middleware.audit.record_tool_call") as record:
            asyncio.run(_collect(_middleware(), _tool_call("{}"), handler))

        _, _params, ok, _duration, error = record.call_args.args
        self.assertFalse(ok)
        self.assertIn("error", error)

    def test_exception_is_recorded_and_reraised(self) -> None:
        async def handler(**kwargs):
            raise RuntimeError("tool exploded")
            yield  # pragma: no cover

        with patch("yak_agent.tools.audit_middleware.audit.record_tool_call") as record:
            with self.assertRaises(RuntimeError):
                asyncio.run(_collect(_middleware(), _tool_call("{}"), handler))

        _, _params, ok, _duration, error = record.call_args.args
        self.assertFalse(ok)
        self.assertIn("RuntimeError: tool exploded", error)

    def test_missing_final_response_is_a_failure(self) -> None:
        async def handler(**kwargs):
            yield ToolChunk(content=[TextBlock(text="partial")])

        with patch("yak_agent.tools.audit_middleware.audit.record_tool_call") as record:
            asyncio.run(_collect(_middleware(), _tool_call("{}"), handler))

        _, _params, ok, _duration, error = record.call_args.args
        self.assertFalse(ok)
        self.assertIn("no final response", error)

    def test_invalid_json_input_records_only_length(self) -> None:
        async def handler(**kwargs):
            yield ToolResponse(state=ToolResultState.SUCCESS)

        with patch("yak_agent.tools.audit_middleware.audit.record_tool_call") as record:
            asyncio.run(_collect(_middleware(), _tool_call("{not-json"), handler))

        _, params, ok, _duration, _error = record.call_args.args
        self.assertTrue(ok)
        self.assertEqual(params["toolCallId"], "call-1")
        self.assertEqual(params["inputChars"], len("{not-json"))

    def test_real_agent_acting_invokes_the_audit_middleware(self) -> None:
        def echo() -> str:
            return "ok"

        agent = Agent(
            name="test-agent",
            system_prompt="test",
            model=OpenAIChatModel(
                credential=OpenAICredential(api_key="test", base_url="http://llm.example"),
                model="test-model",
            ),
            toolkit=Toolkit(
                tools=[FunctionTool(echo, is_read_only=True, is_concurrency_safe=True)]
            ),
            state=AgentState(session_id="state-sid"),
            middlewares=[ToolAuditMiddleware(caller="u-1-10", workspace_id=1)],
        )

        async def scenario() -> None:
            async for _event in agent._execute_tool_call(
                ToolCallBlock(id="call-1", name="echo", input="{}")
            ):
                pass

        with patch("yak_agent.tools.audit_middleware.audit.record_tool_call") as record:
            asyncio.run(scenario())

        record.assert_called_once()
        name, params, ok, _duration, error = record.call_args.args
        self.assertEqual(name, "echo")
        self.assertTrue(ok)
        self.assertIsNone(error)
        self.assertEqual(params["toolCallId"], "call-1")
        self.assertEqual(params["session"], "state-sid")
        self.assertEqual(params["caller"], "u-1-10")


if __name__ == "__main__":
    unittest.main()
