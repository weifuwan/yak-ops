"""PTC security regressions: opt-in, HITL, and no script-visible credentials."""

from __future__ import annotations

import tempfile
import unittest
from pathlib import Path
from unittest.mock import patch

from yak_agent.config import Settings
from yak_agent.ptc import runner as ptc_runner
from yak_agent.ptc.sdk_gen import generate_sdk, write_sdk
from yak_agent.tools.builder import build_tools
from yak_agent.tools.registry import load_allowlist
from yak_agent.workspace import SessionWorkspace
from yak_agent.yak_client import YakAuth


def _settings(**overrides) -> Settings:
    values = {
        "llm_base_url": "http://llm.example",
        "llm_api_key": "test-key",
        "llm_model": "test-model",
        "yak_ops_base_url": "http://yak.example",
        "yak_ops_token": "",
        "ptc_allow_unsafe": True,
    }
    values.update(overrides)
    return Settings(**values)


def _tools(settings: Settings):
    return build_tools(
        settings,
        auth=YakAuth(base_url="http://yak.example", token="test-token"),
        project_code=1,
    )


class PtcRegistrationTests(unittest.TestCase):
    def test_ptc_is_not_registered_by_default(self) -> None:
        names = {tool.name for tool in _tools(_settings())}
        self.assertNotIn("run_analysis_script", names)

    def test_ptc_is_not_read_only_when_enabled(self) -> None:
        tools = _tools(_settings(ptc_enabled=True))
        tool = next(tool for tool in tools if tool.name == "run_analysis_script")
        self.assertFalse(tool.is_read_only)

    def test_ptc_requires_an_explicit_backend(self) -> None:
        with patch.object(ptc_runner, "cocoon_available", return_value=False):
            names = {
                tool.name for tool in _tools(_settings(ptc_enabled=True, ptc_allow_unsafe=False))
            }
        self.assertNotIn("run_analysis_script", names)


class SdkCredentialTests(unittest.TestCase):
    def test_generated_sdk_contains_no_credentials(self) -> None:
        source = generate_sdk(project_code=1, base_url="http://yak.example/yak-ops")
        self.assertNotIn("_SESSION_ID", source)
        self.assertNotIn("_TOKEN =", source)
        self.assertIn("X-Yak-Ptc-Token", source)
        compile(source, "yak_agent_sdk.py", "exec")

    def test_write_sdk_lands_in_the_given_directory_only(self) -> None:
        with tempfile.TemporaryDirectory() as tmp:
            root = Path(tmp)
            workspace = SessionWorkspace(root / "workspace")
            sdk_dir = root / "sdk"
            sdk_dir.mkdir()
            write_sdk(sdk_dir, 1, "http://yak.example/yak-ops")

            self.assertTrue((sdk_dir / "yak_agent_sdk.py").exists())
            self.assertFalse((workspace.root / "yak_agent_sdk.py").exists())

    def test_workspace_refuses_to_read_a_generated_sdk(self) -> None:
        with tempfile.TemporaryDirectory() as tmp:
            workspace = SessionWorkspace(Path(tmp))
            (Path(tmp) / "yak_agent_sdk.py").write_text("_SESSION_ID = 'leak'")
            with self.assertRaises(ValueError):
                workspace.read_file("yak_agent_sdk.py")

    def test_run_tokens_are_scoped_and_revocable(self) -> None:
        auth = YakAuth(base_url="http://yak.example", token="t")
        ptc_runner.register_run_auth("token-1", auth)
        try:
            self.assertIs(ptc_runner._auth_for_token("token-1"), auth)
            self.assertIsNone(ptc_runner._auth_for_token("token-2"))
        finally:
            ptc_runner.unregister_run_auth("token-1")
        self.assertIsNone(ptc_runner._auth_for_token("token-1"))


class PtcAllowlistCoverageTests(unittest.TestCase):
    def test_every_allowlisted_tool_name_is_accounted_for(self) -> None:
        names = {tool.name for tool in _tools(_settings(ptc_enabled=True))}
        missing = load_allowlist() - names
        self.assertEqual(missing, set(), missing)


if __name__ == "__main__":
    unittest.main()
