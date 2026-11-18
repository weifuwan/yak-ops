"""Write-operation drill: full task lifecycle through the real model + HITL.

Creates a drill task from an existing template, publishes it, takes it offline
and deletes it — every write goes through a real RequireUserConfirmEvent which
this script answers with "allow" (printed for transparency). Verifies at the
end that the drill task is gone.

Run:  python smoke/write_drill.py
Costs real model tokens and touches the real backend; the drill task is
deleted at the end, leaving only audit/log records behind.
"""

from __future__ import annotations

import asyncio
import sys
import time
from pathlib import Path

sys.path.insert(0, str(Path(__file__).resolve().parent.parent))

from yak_agent.app import build_agent  # noqa: E402
from yak_agent.config import Settings  # noqa: E402
from yak_agent.yak_client import YakAuth, yak_get  # noqa: E402

DRILL_NAME = f"agent-drill-{time.strftime('%H%M%S')}"


async def main_async() -> int:
    settings = Settings.load()
    agent = build_agent(settings)
    auth = YakAuth(
        base_url=settings.yak_ops_base_url,
        token=settings.yak_ops_token,
        username=settings.yak_ops_username,
        password=settings.yak_ops_password,
    )

    confirms: list[dict] = []
    problems: list[str] = []

    def auto_confirm(event) -> object:
        from agentscope.event import ConfirmResult, UserConfirmResultEvent

        for tc in event.tool_calls:
            entry = {"tool": tc.name, "input": str(getattr(tc, "input", ""))[:200]}
            confirms.append(entry)
            print(f"  [confirm→allow] {tc.name} {entry['input']}")
        return UserConfirmResultEvent(
            reply_id=event.reply_id,
            confirm_results=[
                ConfirmResult(confirmed=True, tool_call=tc) for tc in event.tool_calls
            ],
        )

    turns = [
        (
            f"请照着项目里现有的任意一个数据同步任务，创建一个演练任务，名字叫 {DRILL_NAME}，"
            f"备注写「agent 写操作演练，可删除」。要求：先预览校验，确认参数无误后再创建。"
        ),
        f"把刚创建的演练任务 {DRILL_NAME} 发布上线。",
        f"把演练任务 {DRILL_NAME} 下线。",
        f"删除演练任务 {DRILL_NAME}。",
    ]

    for index, question in enumerate(turns, 1):
        from yak_agent.app import _ask

        print(f"\n=== turn {index}: {question[:40]}... ===")
        try:
            answer = await _ask(agent, question, confirm_handler=auto_confirm)
        except Exception as ex:  # noqa: BLE001
            problems.append(f"turn {index} raised {type(ex).__name__}: {ex}")
            break
        print(f"  [answer] {answer[:220].replace(chr(10), ' ')}")

    # Final check: the drill task must be gone (direct API, not via the model).
    data = yak_get(
        auth,
        f"/projects/{settings.project_code}/data-sync/tasks",
        {"keyword": DRILL_NAME, "pageNo": 1, "pageSize": 10},
    )
    total = data.get("total") if isinstance(data, dict) else None
    print(f"\nfinal check: tasks matching '{DRILL_NAME}' = {total}")

    writes = [
        c
        for c in confirms
        if c["tool"]
        in (
            "create_sync_task",
            "update_sync_task",
            "publish_sync_task",
            "unpublish_sync_task",
            "delete_sync_task",
        )
    ]
    ok = (
        len(writes) >= 4
        and {c["tool"] for c in writes}
        >= {"create_sync_task", "publish_sync_task", "unpublish_sync_task", "delete_sync_task"}
        and total == 0
        and not problems
    )
    print(f"confirms total: {len(confirms)} | write confirms: {len(writes)}")
    print(f"problems: {problems or 'none'}")
    print(f"\n{'DRILL PASSED' if ok else 'DRILL FAILED'}")
    return 0 if ok else 1


if __name__ == "__main__":
    sys.exit(asyncio.run(main_async()))
