"""Manual data-sync task execution tool."""

from __future__ import annotations

import asyncio
import unittest
from unittest.mock import patch

from yak_agent.config import Settings
from yak_agent.tools import runtime
from yak_agent.tools.builder import build_tools
from yak_agent.yak_client import YakAuth


def _settings() -> Settings:
    return Settings(
        llm_base_url="http://llm.example",
        llm_api_key="test-key",
        llm_model="test-model",
        yak_ops_base_url="http://yak.example",
        yak_ops_token="test-token",
    )


class RunSyncTaskTests(unittest.TestCase):
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

    def _call(self, **kwargs):
        with patch.object(runtime, "yak_write", return_value=[123]) as write:
            asyncio.run(self.tools["run_sync_task"](**kwargs))
        write.assert_called_once()
        return write.call_args.args

    def test_registered_as_write_with_confirmation(self) -> None:
        tool = self.tools["run_sync_task"]

        self.assertFalse(tool.is_read_only)
        self.assertFalse(tool.is_concurrency_safe)

    def test_requires_workflow_code_and_hides_fixed_params(self) -> None:
        schema = self.tools["run_sync_task"].input_schema

        self.assertEqual(schema["required"], ["workflow_code"])
        self.assertNotIn("exec_type", schema["properties"])
        self.assertNotIn("failure_strategy", schema["properties"])
        self.assertEqual(schema["properties"]["warning_type"]["default"], "NONE")

    def test_maps_workflow_code_with_run_defaults(self) -> None:
        _auth, method, path, body, query = self._call(workflow_code=123)

        self.assertEqual(method, "POST")
        self.assertEqual(path, "/projects/1/executors/start-workflow-instance")
        self.assertEqual(body, {})
        self.assertEqual(
            query,
            {
                "workflowDefinitionCode": 123,
                "warningType": "NONE",
                "execType": "START_PROCESS",
                "taskDependType": "TASK_POST",
                "failureStrategy": "END",
                "workflowInstancePriority": "MEDIUM",
                "workerGroup": "default",
                "tenantCode": "default",
                "environmentCode": -1,
                "dryRun": 0,
            },
        )

    def test_maps_warning_settings(self) -> None:
        _auth, _method, _path, _body, query = self._call(
            workflow_code=123,
            warning_type="FAILURE",
            warning_group_id=9,
        )

        self.assertEqual(query["warningType"], "FAILURE")
        self.assertEqual(query["warningGroupId"], 9)


if __name__ == "__main__":
    unittest.main()
