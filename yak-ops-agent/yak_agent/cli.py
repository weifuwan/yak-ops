"""Interactive CLI entry point with human-in-the-loop confirmation.

Read tools run automatically (read-only fast path). Write tools trigger a
`RequireUserConfirmEvent`; in the interactive CLI the pending call is shown and
approved per tool call, in non-interactive mode (single-question / smoke test)
writes are denied by default.

Run:  python -m yak_agent.cli                 # interactive CLI
      python -m yak_agent.cli "question"      # one-shot, writes denied
"""

from __future__ import annotations

import argparse
import asyncio
import json
from collections.abc import Callable
from pathlib import Path

from agentscope.agent import Agent
from agentscope.event import (
    ConfirmResult,
    EventType,
    RequireUserConfirmEvent,
    UserConfirmResultEvent,
)
from agentscope.message import UserMsg

from yak_agent.agent.builder import build_agent
from yak_agent.config import Settings
from yak_agent.tools import build_auth
from yak_agent.workspace import SessionWorkspace


def _format_call(name: str, raw_input: str) -> str:
    try:
        parsed = json.loads(raw_input or "{}")
        return f"{name}({json.dumps(parsed, ensure_ascii=False, indent=2)})"
    except json.JSONDecodeError:
        return f"{name}({raw_input})"


def cli_confirm(event: RequireUserConfirmEvent) -> UserConfirmResultEvent:
    """Ask the operator to approve each pending write call."""
    print("\n—— 需要确认的操作 ——")
    results = []
    for tc in event.tool_calls:
        print(_format_call(tc.name, getattr(tc, "input", "")))
        try:
            answer = input("确认执行? [y/N] ").strip().lower()
        except (EOFError, KeyboardInterrupt):
            answer = "n"
        results.append(ConfirmResult(confirmed=answer in ("y", "yes"), tool_call=tc))
    return UserConfirmResultEvent(reply_id=event.reply_id, confirm_results=results)


def deny_confirm(event: RequireUserConfirmEvent) -> UserConfirmResultEvent:
    """Non-interactive policy: never execute writes, tell the model it was denied."""
    return UserConfirmResultEvent(
        reply_id=event.reply_id,
        confirm_results=[ConfirmResult(confirmed=False, tool_call=tc) for tc in event.tool_calls],
    )


async def _ask(
    agent: Agent,
    question: str,
    confirm_handler: Callable[[RequireUserConfirmEvent], UserConfirmResultEvent] = deny_confirm,
) -> str:
    """Run one turn to completion, resolving confirmation pauses via the handler."""
    text_parts: list[str] = []
    pending_msg: object = UserMsg(name="user", content=question)
    while True:
        paused = None
        async for event in agent.reply_stream(pending_msg):
            if event.type == EventType.TEXT_BLOCK_DELTA:
                delta = getattr(event, "delta", "")
                if delta:
                    text_parts.append(delta)
            elif (
                isinstance(event, RequireUserConfirmEvent)
                or event.type == EventType.REQUIRE_USER_CONFIRM
            ):
                paused = event
                break
        if paused is None:
            break
        pending_msg = confirm_handler(paused)
    return "".join(text_parts).strip()


def main() -> None:
    parser = argparse.ArgumentParser(description="yak-ops data-sync agent CLI")
    parser.add_argument("question", nargs="*", help="one-shot question; omit for interactive mode")
    parser.add_argument(
        "--project-code",
        required=True,
        help="workspace/project code the agent should operate on",
    )
    parser.add_argument(
        "--session-id",
        default="",
        help="yak-ops sessionId; falls back to YAK_OPS_TOKEN when omitted",
    )
    args = parser.parse_args()

    settings = Settings.load()
    auth = build_auth(settings, session_id=args.session_id)
    if not auth.token and not auth.session_id:
        raise SystemExit("CLI requires --session-id or YAK_OPS_TOKEN.")
    workspace = SessionWorkspace(
        Path(__file__).resolve().parent.parent / "sessions" / "default" / "workspace"
    )
    agent = build_agent(
        settings,
        workspace=workspace,
        auth=auth,
        project_code=int(args.project_code),
    )

    if args.question:  # one-shot mode: writes are denied by default
        answer = asyncio.run(_ask(agent, " ".join(args.question)))
        print(answer or "（模型未返回文本）")
        return

    print("yak-ops 数据同步助手。输入问题回车提问；写操作会先请求你确认；Ctrl+C 退出。")
    while True:
        try:
            question = input("你> ").strip()
        except (EOFError, KeyboardInterrupt):
            break
        if not question:
            continue
        try:
            print(
                asyncio.run(_ask(agent, question, confirm_handler=cli_confirm))
                or "（模型未返回文本）"
            )
        except Exception as ex:  # keep the loop alive; surface the error honestly
            print(f"出错了：{ex}")


if __name__ == "__main__":
    main()
