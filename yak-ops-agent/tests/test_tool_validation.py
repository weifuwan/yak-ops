"""Tool registry validation and inventory guard."""

from __future__ import annotations

import asyncio
import unittest
from http import HTTPStatus
from types import SimpleNamespace
from unittest.mock import patch

from agentscope.message import ToolResultState
from agentscope.tool import ToolChunk

from yak_agent.api.errors import _STATUS_BY_ERROR
from yak_agent.config import Settings
from yak_agent.ptc import runner as ptc_runner
from yak_agent.service.errors import ToolConfigurationExceeded
from yak_agent.tools import validation
from yak_agent.tools.builder import build_tools
from yak_agent.tools.validation import (
    RESERVED_TOOL_NAMES,
    ToolConfigurationError,
    check_tool_inventory,
    validate_registry,
    validate_tool_names,
)
from yak_agent.yak_client import YakApiError, YakAuth


def _tool(name: str) -> SimpleNamespace:
    return SimpleNamespace(name=name, description="", input_schema={})


class ToolNameValidationTests(unittest.TestCase):
    def test_unique_names_pass(self) -> None:
        validate_tool_names([_tool("list_sync_tasks"), _tool("memory_search")])

    def test_duplicate_names_fail(self) -> None:
        with self.assertRaises(ToolConfigurationError) as ctx:
            validate_tool_names([_tool("a"), _tool("a")])
        self.assertIn("duplicate tool names", str(ctx.exception))

    def test_reserved_names_fail(self) -> None:
        for name in RESERVED_TOOL_NAMES:
            with self.subTest(name=name), self.assertRaises(ToolConfigurationError):
                validate_tool_names([_tool(name)])

    def test_missing_name_fails(self) -> None:
        with self.assertRaises(ToolConfigurationError):
            validate_tool_names([_tool("")])


class RegistryValidationTests(unittest.TestCase):
    def test_actual_registry_is_valid(self) -> None:
        validate_registry()

    def test_duplicate_operation_id_fails(self) -> None:
        with (
            patch.object(validation, "load_operations", return_value=[{"id": "a"}] * 2),
            patch.object(validation, "load_allowlist", return_value={"a"}),
        ):
            with self.assertRaises(ToolConfigurationError) as ctx:
                validate_registry()
        self.assertIn("duplicate operation ids", str(ctx.exception))

    def test_unknown_allowlist_entry_fails(self) -> None:
        with (
            patch.object(validation, "load_operations", return_value=[{"id": "a"}]),
            patch.object(validation, "load_allowlist", return_value={"a", "unknown"}),
        ):
            with self.assertRaises(ToolConfigurationError) as ctx:
                validate_registry()
        self.assertIn("unknown", str(ctx.exception))

    def test_allowlist_only_ptc_name_is_allowed(self) -> None:
        with (
            patch.object(validation, "load_operations", return_value=[{"id": "a"}]),
            patch.object(validation, "load_allowlist", return_value={"a", "run_analysis_script"}),
        ):
            validate_registry()

    def test_reserved_operation_name_fails(self) -> None:
        with (
            patch.object(validation, "load_operations", return_value=[{"id": "Skill"}]),
            patch.object(validation, "load_allowlist", return_value={"Skill"}),
        ):
            with self.assertRaises(ToolConfigurationError):
                validate_registry()

    def test_invalid_risk_wraps_as_configuration_error(self) -> None:
        with patch.object(
            validation,
            "load_operations",
            side_effect=YakApiError("unsupported risk"),
        ):
            with self.assertRaises(ToolConfigurationError) as ctx:
                validate_registry()
        self.assertIn("unsupported risk", str(ctx.exception))


class InventoryTests(unittest.TestCase):
    def setUp(self) -> None:
        validation.reset_inventory_warnings()

    def _settings(self, **overrides):
        values = {
            "tool_count_warn": 20,
            "tool_count_max": 0,
            "tool_schema_warn_tokens": 100_000,
        }
        values.update(overrides)
        return SimpleNamespace(**values)

    def test_counts_explicit_and_internal_tools(self) -> None:
        with patch.object(validation, "tool_schema_tokens", return_value=100):
            inventory = check_tool_inventory(
                [_tool("a"), _tool("b")],
                internal_count=2,
                settings=self._settings(),
            )

        self.assertEqual(inventory.count, 4)
        self.assertEqual(inventory.schema_tokens, 100 + 2 * validation._INTERNAL_TOOL_TOKENS)
        self.assertEqual(inventory.names, ("a", "b"))

    def test_hard_cap_fails(self) -> None:
        with patch.object(validation, "tool_schema_tokens", return_value=100):
            with self.assertRaises(ToolConfigurationError):
                check_tool_inventory(
                    [_tool("a"), _tool("b")],
                    internal_count=1,
                    settings=self._settings(tool_count_max=2),
                )

    def test_warning_is_emitted_once_per_inventory(self) -> None:
        with (
            patch.object(validation, "tool_schema_tokens", return_value=100),
            patch.object(validation._LOGGER, "warning") as warning,
        ):
            settings = self._settings(tool_count_warn=1)
            check_tool_inventory([_tool("a"), _tool("b")], internal_count=0, settings=settings)
            check_tool_inventory([_tool("a"), _tool("b")], internal_count=0, settings=settings)

        self.assertEqual(warning.call_count, 1)

    def test_tool_configuration_error_maps_to_422(self) -> None:
        self.assertEqual(
            _STATUS_BY_ERROR[ToolConfigurationExceeded],
            HTTPStatus.UNPROCESSABLE_ENTITY,
        )


class PtcToolResultTests(unittest.TestCase):
    @staticmethod
    def _settings() -> Settings:
        return Settings(
            llm_base_url="http://llm.example",
            llm_api_key="test-key",
            llm_model="test-model",
            yak_ops_base_url="http://yak.example",
            yak_ops_token="",
            ptc_allow_unsafe=True,
        )

    def _run(self, exit_code: int) -> ToolChunk:
        result = {
            "stdout": "done",
            "stderr": "" if exit_code == 0 else "boom",
            "exitCode": exit_code,
            "durationMs": 7,
            "backend": "cocoon",
        }
        with patch.object(ptc_runner, "run_analysis", return_value=result):
            tools = build_tools(
                self._settings(),
                auth=YakAuth(base_url="http://yak.example", token="test-token"),
                project_code=1,
            )
            tool = next(tool for tool in tools if tool.name == "run_analysis_script")
            return asyncio.run(tool(code="print('x')"))

    def test_success_returns_success_chunk_and_metadata(self) -> None:
        chunk = self._run(0)

        self.assertEqual(chunk.state, ToolResultState.SUCCESS)
        self.assertIn("done", chunk.content[0].text)
        self.assertEqual(chunk.metadata["backend"], "cocoon")
        self.assertEqual(chunk.metadata["exitCode"], 0)

    def test_nonzero_exit_returns_error_chunk(self) -> None:
        chunk = self._run(-1)

        self.assertEqual(chunk.state, ToolResultState.ERROR)
        self.assertEqual(chunk.metadata["exitCode"], -1)


if __name__ == "__main__":
    unittest.main()
