"""Assemble the data-sync copilot from AgentScope components."""

from __future__ import annotations

import logging
from pathlib import Path
from typing import Any

from agentscope.agent import Agent, ContextConfig, InjectionConfig, ReActConfig
from agentscope.credential import OpenAICredential
from agentscope.middleware import TracingMiddleware
from agentscope.model import OpenAIChatModel
from agentscope.skill import LocalSkillLoader
from agentscope.tool import Toolkit

from yak_agent.agent.budget import (
    COMPRESSION_TOOL_TOKENS,
    CONTEXT_TRIGGER_RATIO,
    CacheAwareReplyBudgetControlMiddleware,
    check_context_budget,
    enforce_context_budget,
    estimate_value_tokens,
)
from yak_agent.agent.prompt import compose_system_prompt
from yak_agent.config import PLATFORM_TIMEZONE, Settings
from yak_agent.memory.middleware import MEMORY_PROMPT_TOKENS, build_memory_search_tool
from yak_agent.tools import build_tools
from yak_agent.tools.audit_middleware import ToolAuditMiddleware
from yak_agent.tools.registry import load_operations
from yak_agent.tools.validation import check_tool_inventory, validate_tool_names
from yak_agent.workspace import SessionWorkspace

_SKILLS_DIR = Path(__file__).resolve().parent.parent / "skills"
_COMPRESSION_ENABLED = True
_LOGGER = logging.getLogger(__name__)
_SKILL_TEXT_SUFFIXES = frozenset({".md", ".txt", ".yaml", ".yml", ".json"})


def _skill_prompt_tokens() -> int:
    """Conservative estimate of the skill instructions appended per reply."""
    total = 0
    if _SKILLS_DIR.exists():
        for path in _SKILLS_DIR.rglob("*"):
            if path.is_file() and path.suffix.lower() in _SKILL_TEXT_SUFFIXES:
                total += path.stat().st_size
    return (total + 3) // 4


def _skill_tool_available() -> bool:
    """The Toolkit registers a Skill viewer only when at least one skill loads."""
    return _SKILLS_DIR.exists() and any(_SKILLS_DIR.rglob("SKILL.md"))


def _active_domains(tools: list[Any]) -> list[str]:
    """Tool domains actually registered for this session, in registry order."""
    names = {tool.name for tool in tools}
    domains: list[str] = []
    for op in load_operations():
        domain = op.get("domain")
        if op.get("id") in names and domain and domain not in domains:
            domains.append(domain)
    return domains


def build_agent(
    settings: Settings,
    state: Any | None = None,
    workspace: SessionWorkspace | None = None,
    auth: Any | None = None,
    project_code: int | None = None,
    memory: Any | None = None,
    audit_caller: str = "",
) -> Agent:
    """Assemble the data-sync copilot from open-box AgentScope components."""
    model = OpenAIChatModel(
        credential=OpenAICredential(api_key=settings.llm_api_key, base_url=settings.llm_base_url),
        model=settings.llm_model,
        context_size=settings.llm_context_size,
    )
    tools = build_tools(
        settings,
        auth=auth,
        workspace=workspace,
        project_code=project_code,
    )
    if memory is not None:
        tools.append(build_memory_search_tool(memory))
    validate_tool_names(tools)
    internal_count = (1 if _COMPRESSION_ENABLED else 0) + (1 if _skill_tool_available() else 0)
    check_tool_inventory(tools, internal_count=internal_count, settings=settings)
    profile = memory.profile() if memory is not None else None
    system_prompt = compose_system_prompt(
        _active_domains(tools),
        prompt_dir=settings.prompt_dir,
        include_ptc=any(tool.name == "run_analysis_script" for tool in tools),
        include_workspace=workspace is not None,
        profile=profile,
    )

    report = check_context_budget(
        context_size=settings.llm_context_size,
        trigger_ratio=CONTEXT_TRIGGER_RATIO,
        system_prompt=system_prompt,
        tools=tools,
        memory_budget_tokens=settings.llm_memory_budget_tokens if memory is not None else 0,
        reserved_tool_tokens=COMPRESSION_TOOL_TOKENS if _COMPRESSION_ENABLED else 0,
        reserved_prompt_tokens=estimate_value_tokens(getattr(state, "summary", None))
        + _skill_prompt_tokens()
        + (MEMORY_PROMPT_TOKENS if memory is not None else 0),
    )
    enforce_context_budget(report)
    _LOGGER.info(
        "context budget: threshold=%d used=%d available=%d memory=%d profile_chars=%d",
        report.threshold,
        report.used,
        report.available,
        settings.llm_memory_budget_tokens if memory is not None else 0,
        len(profile or ""),
    )

    middlewares: list[Any] = [
        # Cost/latency guardrail: bill only non-cached input (output weighs
        # 2x). Charging cached context at full price made long sessions stop
        # after the first tool call. Once the budget is exhausted the agent
        # wraps up without further tool calls.
        CacheAwareReplyBudgetControlMiddleware(
            token_budget=settings.reply_token_budget,
            input_token_weight=1.0,
            output_token_weight=2.0,
        ),
        # OpenTelemetry tracing; short-circuits (zero overhead) when no
        # TracerProvider is configured, full span tree when one is.
        TracingMiddleware(),
    ]
    if settings.tool_audit_enabled:
        # Outermost acting middleware: audits every toolkit execution.
        middlewares.insert(
            0,
            ToolAuditMiddleware(caller=audit_caller, workspace_id=project_code),
        )
    if memory is not None:
        # Outermost middleware: supplies the memory tool instructions in the
        # system prompt. Retrieval/write-back timing is owned by run_turn.
        middlewares.insert(0, memory)

    return Agent(
        name="yak_agent",
        system_prompt=system_prompt,
        model=model,
        toolkit=Toolkit(
            tools=tools,
            skills_or_loaders=[LocalSkillLoader(directory=str(_SKILLS_DIR), scan_subdir=True)],
        ),
        state=state,
        offloader=workspace if workspace is not None else None,
        react_config=ReActConfig(
            max_iters=12,
            interruption_message="已中断本次回复，请告诉我接下来要做什么。",
        ),
        injection_config=InjectionConfig(timezone=PLATFORM_TIMEZONE),
        context_config=ContextConfig(
            trigger_ratio=CONTEXT_TRIGGER_RATIO,
            reserve_ratio=0.2,
            context_buffer_ratio=0.2,
            tool_result_limit=4000,
            compression_tool_enabled=_COMPRESSION_ENABLED,
        ),
        middlewares=middlewares,
    )
