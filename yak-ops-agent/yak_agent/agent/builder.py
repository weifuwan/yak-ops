"""Assemble the data-sync copilot from AgentScope components."""

from __future__ import annotations

import logging
from pathlib import Path
from typing import Any

from agentscope.agent import Agent, ContextConfig, InjectionConfig, ReActConfig
from agentscope.credential import OpenAICredential
from agentscope.middleware import ReplyBudgetControlMiddleware, TracingMiddleware
from agentscope.model import OpenAIChatModel
from agentscope.skill import LocalSkillLoader
from agentscope.tool import Toolkit

from yak_agent.agent.budget import (
    COMPRESSION_TOOL_TOKENS,
    check_context_budget,
    enforce_context_budget,
)
from yak_agent.agent.prompt import OFFLOAD_INSTRUCTIONS, SYSTEM_PROMPT
from yak_agent.config import Settings
from yak_agent.tools import build_tools
from yak_agent.workspace import SessionWorkspace

_SKILLS_DIR = Path(__file__).resolve().parent.parent / "skills"
_TRIGGER_RATIO = 0.7
_COMPRESSION_ENABLED = True
_LOGGER = logging.getLogger(__name__)


def build_agent(
    settings: Settings,
    state: Any | None = None,
    workspace: SessionWorkspace | None = None,
    auth: Any | None = None,
    project_code: int | None = None,
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
    system_prompt = SYSTEM_PROMPT
    if workspace is not None:
        system_prompt = f"{SYSTEM_PROMPT}\n\n{OFFLOAD_INSTRUCTIONS}"

    report = check_context_budget(
        context_size=settings.llm_context_size,
        trigger_ratio=_TRIGGER_RATIO,
        system_prompt=system_prompt,
        tools=tools,
        memory_budget_tokens=settings.llm_memory_budget_tokens,
        reserved_tool_tokens=COMPRESSION_TOOL_TOKENS if _COMPRESSION_ENABLED else 0,
    )
    enforce_context_budget(report)
    _LOGGER.info(
        "context budget: threshold=%d used=%d available=%d memory=%d",
        report.threshold,
        report.used,
        report.available,
        settings.llm_memory_budget_tokens,
    )

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
        injection_config=InjectionConfig(timezone="Asia/Shanghai"),
        context_config=ContextConfig(
            trigger_ratio=_TRIGGER_RATIO,
            reserve_ratio=0.2,
            context_buffer_ratio=0.2,
            tool_result_limit=4000,
            compression_tool_enabled=_COMPRESSION_ENABLED,
        ),
        middlewares=[
            # Cost/latency guardrail: output tokens weigh 2x; once the budget
            # is exhausted the agent wraps up without further tool calls.
            ReplyBudgetControlMiddleware(
                token_budget=settings.reply_token_budget,
                input_token_weight=1.0,
                output_token_weight=2.0,
            ),
            # OpenTelemetry tracing; short-circuits (zero overhead) when no
            # TracerProvider is configured, full span tree when one is.
            TracingMiddleware(),
        ],
    )
