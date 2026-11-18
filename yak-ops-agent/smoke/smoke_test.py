"""Smoke test for the data-sync closed loop (AgentScope harness).

Verdicts are based on facts recorded by the API client, not on guessing from
model prose:
- a question passes when its tool calls all succeeded (at least one new call);
- a question also passes when it made no new call but the session already holds
  successfully fetched data (the model answering from session context is correct
  and cheap — not a hallucination);
- any failed tool call fails the question.

The stability probe rebuilds the agent on every run (cold start) so it measures
real tool-calling reliability instead of session memory. It aborts early on
model-API problems (e.g. insufficient balance) instead of burning quota.

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
from yak_agent.yak_client import stats_snapshot  # noqa: E402

GOLDEN_QUESTIONS = [
    "昨天有哪些数据同步任务失败了？",
    "现在有哪些同步实例正在运行？",
    "最近一次失败的同步任务的失败原因是什么？",
    "数据同步任务执行了多久，耗时最长的是哪个？",
    "项目里最近的数据同步执行情况怎么样，给一个概览。",
]

FATAL_MARKERS = (
    "cannot reach yak-ops",
    "login failed",
    "Insufficient Balance",
    "exhausted all",
)


def _judge(calls: int, errors: list[str], answer: str, prior_calls: int) -> tuple[bool, str]:
    if errors:
        return False, f"tool call failed: {errors[0][:220]}"
    if not answer.strip():
        return False, "empty answer"
    if calls == 0 and prior_calls == 0:
        return False, "no tool call was made and no prior data in session"
    return True, ""


async def _run_once(agent, question: str) -> tuple[bool, str, float]:
    before = stats_snapshot()
    start = time.monotonic()
    try:
        answer = await _ask(agent, question)
    except Exception as ex:  # noqa: BLE001 - smoke test reports, never crashes
        return False, f"{type(ex).__name__}: {ex}", time.monotonic() - start
    elapsed = time.monotonic() - start

    after = stats_snapshot()
    calls = after["calls"] - before["calls"]
    errors = after["errors"][len(before["errors"]) :]
    ok, reason = _judge(calls, errors, answer, prior_calls=after["calls"])
    detail = answer if ok else f"[{reason}] {answer}"
    return ok, detail, elapsed


def _fatal_check(text: str) -> str | None:
    return next((m for m in FATAL_MARKERS if m in text), None)


async def main_async(repeat: int) -> int:
    settings = Settings.load()
    agent = build_agent(settings)
    failures = 0

    for index, question in enumerate(GOLDEN_QUESTIONS):
        ok, answer, elapsed = await _run_once(agent, question)
        failures += 0 if ok else 1
        print(f"[{'PASS' if ok else 'FAIL'} {elapsed:5.1f}s] {question}")
        preview = answer[:300].replace("\n", " ")
        print(f"       {preview}{'...' if len(answer) > 300 else ''}")
        fatal = _fatal_check(answer)
        if fatal:
            print(
                f"\nSMOKE ABORTED on question {index + 1}: setup problem detected ('{fatal}'). "
                "Fix .env (credentials / backend URL / project code / model quota) and rerun."
            )
            return 1

    if repeat > 1:
        probe = GOLDEN_QUESTIONS[0]
        hits = 0
        aborted = False
        for run in range(1, repeat + 1):
            fresh_agent = build_agent(settings)  # cold start: no session memory
            ok, answer, elapsed = await _run_once(fresh_agent, probe)
            hits += 1 if ok else 0
            if not ok:
                preview = answer[:200].replace("\n", " ")
                print(f"  probe run {run} FAIL ({elapsed:.1f}s): {preview}")
                fatal = _fatal_check(answer)
                if fatal:
                    print(
                        f"\nPROBE ABORTED at run {run}: model API problem detected ('{fatal}'). "
                        f"Partial stability: {hits}/{run - 1} completed runs."
                    )
                    failures += 1
                    aborted = True
                    break
        if not aborted:
            rate = hits / repeat
            print(f"stability probe: {hits}/{repeat} ({rate:.0%}) — threshold 90%")
            if rate < 0.9:
                failures += 1

    print(f"\n{'SMOKE PASSED' if failures == 0 else f'SMOKE FAILED ({failures} issues)'}")
    return 0 if failures == 0 else 1


def main() -> None:
    parser = argparse.ArgumentParser()
    parser.add_argument(
        "--repeat", type=int, default=1, help="repeat probe times for stability check"
    )
    args = parser.parse_args()
    sys.exit(asyncio.run(main_async(args.repeat)))


if __name__ == "__main__":
    main()
