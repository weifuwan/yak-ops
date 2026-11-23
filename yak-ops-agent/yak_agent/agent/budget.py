"""Budget guardrails: context headroom and per-reply token accounting."""

from __future__ import annotations

import json
from collections.abc import AsyncGenerator, Callable
from dataclasses import dataclass
from typing import Any

from agentscope.event import ModelCallEndEvent
from agentscope.middleware import ReplyBudgetControlMiddleware

# AgentScope's base `count_tokens` estimates input size as bytes / 4; keep the
# startup gate consistent with the number the framework will use later.
_BYTES_PER_TOKEN = 4

# The compression tool is registered by the Agent after construction, so its
# schema cannot be measured here; this conservative allowance covers its small
# policy + description.
COMPRESSION_TOOL_TOKENS = 200


class ContextBudgetError(RuntimeError):
    """Raised when the base prompt and tools already exceed the window."""


@dataclass(frozen=True)
class BudgetReport:
    threshold: int
    used: int
    available: int

    @property
    def ok(self) -> bool:
        return self.available > 0


def estimate_tokens(text: str) -> int:
    """Estimate tokens the same way as AgentScope's default counter."""
    return (len(text.encode("utf-8")) + _BYTES_PER_TOKEN - 1) // _BYTES_PER_TOKEN


def estimate_value_tokens(value: Any) -> int:
    """Estimate a prompt fragment that may be a string or block list."""
    if not value:
        return 0
    if isinstance(value, str):
        return estimate_tokens(value)
    return estimate_tokens(json.dumps(value, ensure_ascii=False, default=str))


def _tool_tokens(tool: Any) -> int:
    schema = {
        "name": getattr(tool, "name", ""),
        "description": getattr(tool, "description", ""),
        "input_schema": getattr(tool, "input_schema", {}),
    }
    return estimate_tokens(json.dumps(schema, ensure_ascii=False, default=str))


def tool_schema_tokens(tools: list[Any]) -> int:
    """Estimate the model-visible schema size of a tool inventory."""
    return sum(_tool_tokens(tool) for tool in tools)


def check_context_budget(
    *,
    context_size: int,
    trigger_ratio: float,
    system_prompt: str,
    tools: list[Any],
    memory_budget_tokens: int = 0,
    reserved_tool_tokens: int = 0,
    reserved_prompt_tokens: int = 0,
) -> BudgetReport:
    """Return the headroom left for conversation context after fixed inputs."""
    threshold = int(context_size * trigger_ratio)
    used = estimate_tokens(system_prompt)
    used += sum(_tool_tokens(tool) for tool in tools)
    used += max(0, memory_budget_tokens)
    used += max(0, reserved_tool_tokens)
    used += max(0, reserved_prompt_tokens)
    return BudgetReport(threshold=threshold, used=used, available=threshold - used)


def enforce_context_budget(report: BudgetReport) -> None:
    if report.ok:
        return
    raise ContextBudgetError(
        "模型上下文窗口不足："
        f"压缩触发阈值 {report.threshold} tokens，"
        f"基础提示+工具+摘要+记忆预计 {report.used} tokens。"
        "请调大 LLM_CONTEXT_SIZE，或减少工具/记忆预算。"
    )


class CacheAwareReplyBudgetControlMiddleware(ReplyBudgetControlMiddleware):
    """Reply budget that bills only non-cached input tokens.

    AgentScope's default middleware charges the full prompt on every model
    call, including prompt-cache hits. Long sessions re-send the same context
    each iteration, so the first tool call alone can exhaust the budget even
    when almost all input is cached. That turns the cost guardrail into a
    one-tool-per-reply cap and blocks any read-then-write sequence.

    This subclass keeps the same per-reply budget and wrap-up behavior, but
    accumulates:

        (input_tokens - cache_input_tokens) + output_weight * output_tokens

    Cache creation tokens are still counted as new input. Providers that do
    not report cache usage fall back to the default full-input accounting.
    """

    async def on_reply(
        self,
        agent: Any,
        input_kwargs: dict[str, Any],
        next_handler: Callable[..., AsyncGenerator[Any, None]],
    ) -> AsyncGenerator[Any, None]:
        async def billable_events(**kwargs: Any) -> AsyncGenerator[Any, None]:
            async for event in next_handler(**kwargs):
                if isinstance(event, ModelCallEndEvent):
                    event = event.model_copy(
                        update={
                            "input_tokens": max(0, event.input_tokens - event.cache_input_tokens),
                            "cache_input_tokens": 0,
                        }
                    )
                yield event

        async for event in super().on_reply(agent, input_kwargs, billable_events):
            yield event
