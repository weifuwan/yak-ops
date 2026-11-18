"""Read-only data-sync tools exposed to the AgentScope agent.

All functions are plain sync functions wrapped by `FunctionTool(is_read_only=True)`:
the permission system's read-only fast path auto-allows them in every mode, so
no interactive approvals are needed.

`build_tools(settings)` closes over the host-side `Settings` so that neither the
token nor the project boundary ever appears in a tool signature — the model can
only pass business parameters. Payloads are truncated before reaching the model.
"""

from __future__ import annotations

import json
from typing import Any, Callable

from agentscope.tool import FunctionTool

from yak_agent.config import Settings
from yak_agent.yak_client import YakApiError, truncate, yak_get

_VALID_STATUS = (
    "SUBMITTED_SUCCESS",
    "RUNNING_EXECUTION",
    "READY_PAUSE",
    "PAUSE",
    "READY_STOP",
    "STOP",
    "FAILURE",
    "SUCCESS",
    "NEED_FAULT_TOLERANCE",
    "KILL",
    "DISPATCH",
)

_MAX_PAGE_SIZE = 50
_MAX_LOG_LINES = 1000


def build_tools(settings: Settings) -> list[FunctionTool]:
    """Create the read-only data-sync tools bound to the given settings."""

    def list_sync_instances(
        status: str | None = None,
        task_code: int | None = None,
        page_no: int = 1,
        page_size: int = 20,
    ) -> str:
        """List data sync execution instances of the project, newest page first.

        Use it to answer: which sync instances failed / are running / succeeded,
        when did they start and end, how long did they run. taskInstanceId in the
        result can be used with query_task_log.

        Args:
            status: Optional filter. One of SUBMITTED_SUCCESS, RUNNING_EXECUTION,
                PAUSE, STOP, FAILURE, SUCCESS, KILL, DISPATCH. Common values:
                FAILURE, RUNNING_EXECUTION, SUCCESS.
            task_code: Optional task code to only list instances of one sync task.
            page_no: Page number starting from 1.
            page_size: Page size, 1-50.
        """
        if status is not None and status not in _VALID_STATUS:
            raise YakApiError(f"unknown status '{status}'. Valid values: {', '.join(_VALID_STATUS)}")
        data = yak_get(
            settings.yak_ops_base_url,
            settings.yak_ops_token,
            f"/projects/{settings.project_code}/data-sync/instances",
            {
                "pageNo": max(1, int(page_no)),
                "pageSize": max(1, min(_MAX_PAGE_SIZE, int(page_size))),
                "taskCode": task_code,
                "status": status,
            },
        )
        return truncate(json.dumps(data, ensure_ascii=False, default=str))

    def get_sync_instance_detail(instance_id: int) -> str:
        """Get one data sync instance's detail (state, start/end time, duration, workflow link).

        Args:
            instance_id: Data sync instance ID, taken from list_sync_instances.
        """
        data = yak_get(
            settings.yak_ops_base_url,
            settings.yak_ops_token,
            f"/projects/{settings.project_code}/data-sync/instances/{int(instance_id)}",
        )
        return truncate(json.dumps(data, ensure_ascii=False, default=str))

    def query_task_log(task_instance_id: int, limit: int = 200) -> str:
        """Read the tail of one task instance's execution log, best for locating failure causes.

        Args:
            task_instance_id: Task instance ID, taken from list_sync_instances (taskInstanceId).
            limit: Number of tail lines to return, 1-1000.
        """
        data = yak_get(
            settings.yak_ops_base_url,
            settings.yak_ops_token,
            "/log/detail",
            {
                "taskInstanceId": int(task_instance_id),
                "skipNum": 0,
                "limit": max(1, min(_MAX_LOG_LINES, int(limit))),
            },
        )
        if isinstance(data, dict) and isinstance(data.get("message"), str):
            data = {**data, "message": truncate(data["message"])}
        return truncate(json.dumps(data, ensure_ascii=False, default=str))

    tools: list[Callable[..., str]] = [list_sync_instances, get_sync_instance_detail, query_task_log]
    return [
        FunctionTool(fn, is_read_only=True, is_concurrency_safe=True)
        for fn in tools
    ]
