"""Build the AgentScope tool surface from the declarative operation registry.

`operations.yaml` is the single source of truth: every record becomes one
AgentScope FunctionTool with a JSON Schema derived from its params. The executor
owns the cross-cutting rules — project injection, query/body/path routing,
clamping, JSON parsing, envelope checks, truncation — so new operations are
data, not code.

Risk levels map to the permission system:
- readonly  -> is_read_only=True  -> auto-allowed (read-only fast path);
- write / destructive -> is_read_only=False -> ASK -> HITL confirmation pause.
"""

from __future__ import annotations

import asyncio
import logging
from typing import Any

from agentscope.message import TextBlock, ToolResultState
from agentscope.tool import FunctionTool, ToolChunk

from yak_agent.config import Settings
from yak_agent.tools.registry import is_read_only, load_allowlist, load_operations
from yak_agent.tools.runtime import execute
from yak_agent.tools.schema import build_schema
from yak_agent.tools.time_tools import build_time_tools
from yak_agent.tools.validation import validate_registry
from yak_agent.tools.workspace_tools import build_workspace_tools
from yak_agent.yak_client import YakApiError, YakAuth

_LOGGER = logging.getLogger(__name__)


def build_auth(settings: Settings, session_id: str = "") -> YakAuth:
    """Build the host-side auth for CLI use (token or a supplied session)."""
    return YakAuth(
        base_url=settings.yak_ops_base_url,
        token=settings.yak_ops_token,
        session_id=session_id,
    )


def build_tools(
    settings: Settings,
    auth: YakAuth | None = None,
    workspace: Any | None = None,
    project_code: int | None = None,
) -> list[FunctionTool]:
    """Materialize the registry as AgentScope tools bound to one workspace.

    `project_code` is the workspace the caller is currently in; every REST
    call is scoped to it. `workspace` (a SessionWorkspace) enables the
    persistent PTC working directory and the read/list workspace tools.
    """
    if not project_code:
        raise YakApiError("project_code is required; refusing to build tools without a workspace")
    validate_registry()
    auth = auth or build_auth(settings)

    tools: list[FunctionTool] = []
    allowlisted = load_allowlist()
    for op in load_operations():
        if op["id"] not in allowlisted:
            continue  # allowlist enforcement: not reviewed, not exposed
        readonly = is_read_only(op)

        async def async_runner(_op: dict[str, Any] = op, **kwargs: Any) -> str:
            # yak_client is synchronous (it is also used inside analysis
            # scripts); run it off the API event loop so one slow API call
            # cannot freeze every session.
            return await asyncio.to_thread(execute, _op, auth, kwargs, project_code)

        tools.append(
            FunctionTool(
                async_runner,
                name=op["id"],
                description=f"[{op.get('domain', '')}] {op['summary']}\n\n{op.get('description', '').strip()}",
                input_schema=build_schema(op),
                is_read_only=readonly,
                is_concurrency_safe=readonly,
            )
        )
    tools.extend(build_time_tools())
    if workspace is not None:
        tools.extend(build_workspace_tools(workspace))

    # PTC tool: opt-in only (`YAK_AGENT_PTC_ENABLED`). Code execution is never
    # auto-approved: if enabled, the tool is registered as a non-read-only
    # action, so every run pauses for HITL confirmation.
    try:
        from yak_agent.ptc import runner as _ptc

        ptc_ready = settings.ptc_enabled and (_ptc.cocoon_available() or settings.ptc_allow_unsafe)

        async def run_analysis_script(code: str, timeout_seconds: int = 60) -> ToolChunk:
            """Write and run a Python analysis script in the sandboxed executor (cocoon: host mounts read-only, process limits, per-run proxy token). The user is asked to confirm each run. Use for log root-cause analysis (download the FULL log via download_task_log, grep the Caused by chain), cross-instance aggregation, or anything too large to fit in context. The SDK functions (list_sync_tasks, get_sync_task, list_sync_instances, get_sync_instance_detail, query_task_log, download_task_log, preview_sync_task, get_sync_overview) return parsed dicts; writes are disabled inside scripts. Print conclusions — only stdout returns to you, stdout is truncated at 16 KB (the full output is offloaded to the workspace when that happens). Files you write in the current directory persist across turns.

            Args:
                code: Python 3 script body. `import yak_agent_sdk as sdk` then call its functions; print the conclusion.
                timeout_seconds: Wall-clock limit, 5-300.
            """
            workdir = workspace.root if workspace is not None else None
            result = await asyncio.to_thread(
                _ptc.run_analysis,
                code,
                timeout_seconds,
                workdir=workdir,
                workspace=workspace,
                allow_unsafe=settings.ptc_allow_unsafe,
                auth=auth,
                project_code=project_code,
            )
            return ToolChunk(
                content=[TextBlock(text=_ptc.format_result(result))],
                state=(
                    ToolResultState.SUCCESS
                    if result.get("exitCode") == 0
                    else ToolResultState.ERROR
                ),
                metadata={
                    key: result[key]
                    for key in ("backend", "exitCode", "durationMs")
                    if key in result
                },
            )

        if ptc_ready:
            tools.append(
                FunctionTool(
                    run_analysis_script,
                    is_read_only=False,
                    is_concurrency_safe=False,
                )
            )
    except Exception as ex:  # noqa: BLE001 - PTC is an enhancement; absence keeps form 1
        _LOGGER.warning("PTC tool registration skipped: %s", ex)

    return tools
