"""Assemble the data-sync copilot from AgentScope components."""

from __future__ import annotations

from pathlib import Path
from typing import Any

from agentscope.agent import Agent, ContextConfig, InjectionConfig, ReActConfig
from agentscope.credential import OpenAICredential
from agentscope.middleware import ReplyBudgetControlMiddleware, TracingMiddleware
from agentscope.model import OpenAIChatModel
from agentscope.skill import LocalSkillLoader
from agentscope.tool import Toolkit

from yak_agent.agent.prompt import SYSTEM_PROMPT
from yak_agent.config import Settings
from yak_agent.tools import build_tools
from yak_agent.workspace import SessionWorkspace

_SKILLS_DIR = Path(__file__).resolve().parent.parent / "skills"


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
    return Agent(
        name="yak_agent",
        system_prompt=SYSTEM_PROMPT,
        model=model,
        toolkit=Toolkit(
            tools=build_tools(
                settings,
                auth=auth,
                workspace=workspace,
                project_code=project_code,
            ),
            skills_or_loaders=[LocalSkillLoader(directory=str(_SKILLS_DIR), scan_subdir=True)],
        ),
        state=state,
        react_config=ReActConfig(max_iters=12),
        injection_config=InjectionConfig(timezone="Asia/Shanghai"),
        context_config=ContextConfig(
            trigger_ratio=0.7,
            reserve_ratio=0.2,
            tool_result_limit=4000,
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
