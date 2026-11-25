"""File-based system prompt bundle: layout, composition and validation."""

from __future__ import annotations

import tempfile
import unittest
from pathlib import Path

from yak_agent.agent.prompt import (
    DEFAULT_PROMPT_DIR,
    compose_system_prompt,
    validate_prompt_bundle,
)
from yak_agent.tools.registry import load_operations


def _registry_domains() -> list[str]:
    return sorted({op.get("domain") for op in load_operations() if op.get("domain")})


class PromptBundleTests(unittest.TestCase):
    def test_registry_domains_have_fragments(self) -> None:
        validate_prompt_bundle(DEFAULT_PROMPT_DIR, _registry_domains())
        for domain in _registry_domains():
            with self.subTest(domain=domain):
                self.assertTrue((DEFAULT_PROMPT_DIR / "domains" / f"{domain}.md").is_file())

    def test_core_is_platform_level(self) -> None:
        core = (DEFAULT_PROMPT_DIR / "AGENT.md").read_text(encoding="utf-8")

        self.assertIn("平台智能助手", core)
        self.assertIn("能力边界", core)
        self.assertNotIn("数据同步助手", core)

    def test_composition_follows_active_domains(self) -> None:
        data_sync_only = compose_system_prompt(["data-sync"])

        self.assertIn("data-sync-create", data_sync_only)
        self.assertIn("data-sync-troubleshooting", data_sync_only)
        self.assertNotIn("数据源目录领域", data_sync_only)

        with_datasource = compose_system_prompt(["data-sync", "datasource"])
        self.assertIn("数据源目录领域", with_datasource)

    def test_optional_sections_are_toggled(self) -> None:
        base = compose_system_prompt(["data-sync"])
        self.assertNotIn("run_analysis_script", base)
        self.assertNotIn("read_workspace_file", base)

        full = compose_system_prompt(
            ["data-sync"],
            include_ptc=True,
            include_workspace=True,
        )
        self.assertIn("run_analysis_script", full)
        self.assertIn("read_workspace_file", full)

    def test_missing_core_prompt_fails_closed(self) -> None:
        with tempfile.TemporaryDirectory() as tmp:
            with self.assertRaises(FileNotFoundError):
                compose_system_prompt([], prompt_dir=Path(tmp))

    def test_missing_domain_fragment_fails_closed(self) -> None:
        with self.assertRaises(FileNotFoundError):
            compose_system_prompt(["unknown-domain"])


if __name__ == "__main__":
    unittest.main()
