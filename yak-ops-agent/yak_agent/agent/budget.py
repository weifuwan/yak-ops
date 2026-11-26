"""Budget guardrails: context headroom and per-reply token accounting."""

from __future__ import annotations

import json
from collections.abc import AsyncGenerator, Callable
from dataclasses import dataclass
from typing import Any

from agentscope.event import ModelCallEndEvent
from agentscope.message import SystemMsg, UserMsg
from agentscope.middleware import ReplyBudgetControlMiddleware

# AgentScope's base `count_tokens` estimates input size as bytes / 4; keep the
# startup gate consistent with the number the framework will use later.
_BYTES_PER_TOKEN = 4

# The compression threshold as a ratio of the model window. Shared by the
# builder and the context payload so the UI and the agent agree.
CONTEXT_TRIGGER_RATIO = 0.7

# The cache-aware middleware parks the *raw* usage of the last model call here.
# Downstream events stay billing-adjusted (non-cached input), so this key is the
# only place the full prompt size and cache reads remain visible.
RAW_USAGE_KEY = "yak_context_usage"

# The compression tool is registered by the Agent after construction, so its
# schema cannot be measured here; this conservative allowance covers its small
# policy + description.
COMPRESSION_TOOL_TOKENS = 200

_CONTEXT_LABELS = {
    "messages": "消息",
    "tools": "系统工具",
    "skills": "技能",
    "systemPrompt": "系统提示词",
    "other": "其他",
}


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


def cache_hit_rate(raw_usage: dict[str, Any] | None) -> float:
    """Share of prompt tokens served from the provider cache (0..1)."""
    raw = raw_usage or {}
    input_tokens = max(0, int(raw.get("inputTokens") or 0))
    cache_tokens = max(0, int(raw.get("cacheInputTokens") or 0))
    if input_tokens <= 0:
        return 0.0
    return round(min(1.0, cache_tokens / input_tokens), 4)


def context_payload(
    *,
    used_tokens: int,
    limit_tokens: int,
    average_cache_hit_rate: float = 0.0,
    compressed: bool = False,
    breakdown: list[dict[str, Any]] | None = None,
) -> dict[str, Any]:
    """Build the frontend-facing context capacity payload."""
    used = max(0, int(used_tokens))
    limit = max(0, int(limit_tokens))
    return {
        "usedTokens": used,
        "limitTokens": limit,
        "triggerTokens": int(limit * CONTEXT_TRIGGER_RATIO),
        "ratio": round(min(1.0, used / limit), 4) if limit else 0.0,
        "averageCacheHitRate": round(max(0.0, min(1.0, float(average_cache_hit_rate))), 4),
        "compressed": bool(compressed),
        "breakdown": list(breakdown or []),
    }


async def _count(model: Any, messages: list[Any], tools: list[dict] | None = None) -> int:
    return max(0, int(await model.count_tokens(messages=messages, tools=tools) or 0))


async def describe_context(
    agent: Any,
    *,
    used_tokens: int,
    limit_tokens: int,
    average_cache_hit_rate: float = 0.0,
    cache: dict[str, int] | None = None,
) -> dict[str, Any]:
    """Describe where the model-visible context is spent.

    The fixed inputs (tool schemas, skills, system prompt, summary) are counted
    with the model's own counter; the conversation is the remainder of the real
    prompt size. That keeps the breakdown exact and avoids re-scanning a large
    context on every model call.
    """
    state = getattr(agent, "state", None)
    toolkit = getattr(agent, "toolkit", None)
    model = getattr(agent, "model", None)
    if state is None or toolkit is None or model is None:
        raise ValueError("agent does not expose state/toolkit/model")

    groups = getattr(getattr(state, "tool_context", None), "activated_groups", None)
    cached = cache or {}
    if {"tools", "skills", "systemPrompt"} <= cached.keys():
        tool_tokens = cached["tools"]
        skill_tokens = cached["skills"]
        system_tokens = cached["systemPrompt"]
    else:
        tool_schemas = await toolkit.get_tool_schemas(groups)
        skill_instructions = await toolkit.get_skill_instructions(groups)
        system_prompt = str(getattr(agent, "_system_prompt", "") or "")
        tool_tokens = await _count(model, [], tool_schemas)
        skill_tokens = (
            await _count(model, [SystemMsg(name="system", content=skill_instructions)])
            if skill_instructions
            else 0
        )
        system_tokens = await _count(model, [SystemMsg(name="system", content=system_prompt)])
        if cache is not None:
            cache.update(tools=tool_tokens, skills=skill_tokens, systemPrompt=system_tokens)

    summary = getattr(state, "summary", "")
    other_tokens = await _count(model, [UserMsg(name="user", content=summary)]) if summary else 0
    fixed = tool_tokens + skill_tokens + system_tokens + other_tokens

    used = max(0, int(used_tokens))
    if used <= 0:
        # No model call yet: estimate the conversation too, so a freshly
        # created session still reports its base prompt.
        message_tokens = await _count(model, list(getattr(state, "context", []) or []))
        used = fixed + message_tokens
        alloc = {
            "messages": message_tokens,
            "tools": tool_tokens,
            "skills": skill_tokens,
            "systemPrompt": system_tokens,
            "other": other_tokens,
        }
    elif fixed < used:
        alloc = {
            "messages": used - fixed,
            "tools": tool_tokens,
            "skills": skill_tokens,
            "systemPrompt": system_tokens,
            "other": other_tokens,
        }
    else:
        # Tiny prompt where the fixed inputs alone exceed the reported size:
        # scale them down so the breakdown still sums to the real total.
        scale = used / fixed if fixed else 0
        alloc = {
            "messages": 0,
            "tools": int(round(tool_tokens * scale)),
            "skills": int(round(skill_tokens * scale)),
            "systemPrompt": int(round(system_tokens * scale)),
            "other": int(round(other_tokens * scale)),
        }
        alloc["other"] = max(0, alloc["other"] + used - sum(alloc.values()))
        if sum(alloc.values()) != used:
            alloc["tools"] = max(0, alloc["tools"] + used - sum(alloc.values()))

    breakdown = [
        {
            "key": key,
            "label": _CONTEXT_LABELS[key],
            "tokens": alloc[key],
            "ratio": round(alloc[key] / used, 4) if used else 0.0,
        }
        for key in _CONTEXT_LABELS
    ]
    return context_payload(
        used_tokens=used,
        limit_tokens=limit_tokens,
        average_cache_hit_rate=average_cache_hit_rate,
        compressed=bool(summary),
        breakdown=breakdown,
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
                    # Keep the raw values for the context panel before the
                    # billing-adjusted copy is forwarded to the rest of the
                    # chain (input_tokens includes cache reads by definition).
                    middle_context = getattr(getattr(agent, "state", None), "middle_context", None)
                    if isinstance(middle_context, dict):
                        middle_context[RAW_USAGE_KEY] = {
                            "inputTokens": max(0, int(event.input_tokens or 0)),
                            "outputTokens": max(0, int(event.output_tokens or 0)),
                            "cacheInputTokens": max(0, int(event.cache_input_tokens or 0)),
                            "cacheCreationInputTokens": max(
                                0, int(event.cache_creation_input_tokens or 0)
                            ),
                        }
                    event = event.model_copy(
                        update={
                            "input_tokens": max(0, event.input_tokens - event.cache_input_tokens),
                            "cache_input_tokens": 0,
                        }
                    )
                yield event

        async for event in super().on_reply(agent, input_kwargs, billable_events):
            yield event
