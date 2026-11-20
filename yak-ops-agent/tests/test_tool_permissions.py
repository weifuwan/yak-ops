"""Permission invariants: `risk` is the only source of `is_read_only` (backlog 2.2)."""

from __future__ import annotations

import unittest
from unittest.mock import patch

from yak_agent.config import Settings
from yak_agent.tools import registry
from yak_agent.tools.builder import build_tools
from yak_agent.tools.registry import (
    RISK_LEVELS,
    is_read_only,
    load_allowlist,
    load_operations,
    operation_risk,
)
from yak_agent.yak_client import YakApiError, YakAuth


def _settings() -> Settings:
    return Settings(
        llm_base_url="http://llm.example",
        llm_api_key="test-key",
        llm_model="test-model",
        yak_ops_base_url="http://yak.example",
        yak_ops_token="test-token",
    )


class RegistryRiskTests(unittest.TestCase):
    def test_every_registered_operation_has_supported_risk(self) -> None:
        for op in load_operations():
            self.assertIn(op.get("risk"), RISK_LEVELS, op.get("id"))

    def test_load_operations_rejects_unknown_risk(self) -> None:
        with patch.object(
            registry.yaml,
            "safe_load",
            return_value={"operations": [{"id": "write_task", "risk": "read_only"}]},
        ):
            with self.assertRaises(YakApiError):
                load_operations()

    def test_operation_risk_rejects_missing_or_unknown_values(self) -> None:
        for op in ({"id": "x"}, {"id": "x", "risk": "read"}, {"id": "x", "risk": ""}):
            with self.subTest(op=op):
                with self.assertRaises(YakApiError):
                    operation_risk(op)

    def test_readonly_and_write_risks_map_to_boolean(self) -> None:
        self.assertTrue(is_read_only({"id": "x", "risk": "readonly"}))
        self.assertFalse(is_read_only({"id": "x", "risk": "write"}))
        self.assertFalse(is_read_only({"id": "x", "risk": "destructive"}))


class BuiltToolPermissionTests(unittest.TestCase):
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

    def test_every_allowlisted_operation_is_exposed(self) -> None:
        for tool in load_allowlist():
            if tool == "run_analysis_script":
                continue  # registered only when a sandbox backend is available
            self.assertIn(tool, self.tools)

    def test_is_read_only_matches_registry_risk(self) -> None:
        for op in load_operations():
            tool = self.tools.get(op["id"])
            if tool is None:
                continue  # not allowlisted, not exposed
            self.assertEqual(
                tool.is_read_only,
                op["risk"] == "readonly",
                f"{op['id']} risk={op['risk']} is_read_only={tool.is_read_only}",
            )

    def test_no_tool_hardcodes_an_allow_decision(self) -> None:
        # HITL and the read-only fast path rely on the default ASK; an explicit
        # ALLOW would execute writes without confirmation.
        for tool in self.tools.values():
            self.assertIsNone(getattr(tool, "_permission", None), tool.name)


if __name__ == "__main__":
    unittest.main()
