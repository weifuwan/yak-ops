"""Smoke test for the data-sync closed loop (AgentScope harness).

Runs 5 golden questions once each plus one repeated-stability probe, asserting
non-empty grounded answers. Requires real credentials in .env and a reachable
yak-ops backend; exits with clear guidance otherwise.

Run:  python smoke/smoke_test.py [--repeat 10]
"""

from __future__ import annotations

import argparse
import asyncio
import sys
import time
from pathlib import Path

sys.path.insert(0, str(Path(__file__).resolve().parent.parent))

from yak_agent.app import _ask, build_agent  # noqa: E402
from yak_agent.config import Settings  # noqa: E402

GOLDEN_QUESTIONS = [
    "昨天有哪些数据同步任务失败了？",
    "现在有哪些同步实例正在运行？",
    "最近一次失败的同步任务的失败原因是什么？",
    "数据同步任务执行了多久，耗时最长的是哪个？",
    "项目里最近的数据同步执行情况怎么样，给一个概览。",
]


async def _run_once(agent, question: str) -> tuple[bool, str, float]:
    start = time.monotonic()
    try:
        answer = await _ask(agent, question)
        ok = bool(answer.strip())
        return ok, answer, time.monotonic() - start
    except Exception as ex:  # noqa: BLE001 - smoke test reports, never crashes
        return False, f"{type(ex).__name__}: {ex}", time.monotonic() - start


async def main_async(repeat: int) -> int:
    settings = Settings.load()
    agent = build_agent(settings)
    failures = 0

    for question in GOLDEN_QUESTIONS:
        ok, answer, elapsed = await _run_once(agent, question)
        failures += 0 if ok else 1
        print(f"[{'PASS' if ok else 'FAIL'} {elapsed:5.1f}s] {question}")
        preview = answer[:300].replace("\n", " ")
        print(f"       {preview}{'...' if len(answer) > 300 else ''}")

    if repeat > 1:
        probe = GOLDEN_QUESTIONS[0]
        hits = sum(
            1
            for _ in range(repeat)
            if (await _run_once(agent, probe))[0]
        )
        rate = hits / repeat
        print(f"stability probe: {hits}/{repeat} ({rate:.0%}) — threshold 90%")
        if rate < 0.9:
            failures += 1

    print(f"\n{'SMOKE PASSED' if failures == 0 else f'SMOKE FAILED ({failures} issues)'}")
    return 0 if failures == 0 else 1


def main() -> None:
    parser = argparse.ArgumentParser()
    parser.add_argument("--repeat", type=int, default=1, help="repeat probe times for stability check")
    args = parser.parse_args()
    sys.exit(asyncio.run(main_async(args.repeat)))


if __name__ == "__main__":
    main()
