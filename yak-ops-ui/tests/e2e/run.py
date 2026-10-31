#!/usr/bin/env python3
"""Playwright UI tests; JDBC and CDC correctness belong to backend E2E."""
import argparse
import asyncio
import json
import os
from pathlib import Path
import re
import subprocess
import sys
import time
import traceback

SCENARIOS = ("table-layout", "solution-selection", "schedule-save-recovery", "multitable-mapping", "multitable-selection", "schema-configuration", "editor-round-trip", "field-mapping", "snapshot-replication", "realtime-stages", "schedule-history", "schedule-errors")
LOGIN = "login-smoke"
ALL = (*SCENARIOS, LOGIN)

async def check(name, directory):
    from playwright.async_api import async_playwright
    from mock_api import mock, state
    from data_sync import (verify_solution_creation, verify_partial_task_save,
                           verify_multitable_mapping, verify_multitable_selection, verify_schema_configuration,
                           verify_editor_round_trip, verify_realtime_stages,
                           verify_field_mapping, verify_snapshot_replication)
    from table_layout import verify_table_layout
    from scheduling import verify_schedule_history, verify_schedule_errors
    from login import verify_login

    scenarios = {
        "table-layout": verify_table_layout,
        "solution-selection": verify_solution_creation,
        "schedule-save-recovery": verify_partial_task_save,
        "multitable-mapping": verify_multitable_mapping,
        "multitable-selection": verify_multitable_selection,
        "schema-configuration": verify_schema_configuration,
        "editor-round-trip": verify_editor_round_trip,
        "field-mapping": verify_field_mapping,
        "snapshot-replication": verify_snapshot_replication,
        "realtime-stages": verify_realtime_stages,
        "schedule-history": verify_schedule_history,
        "schedule-errors": verify_schedule_errors,
        LOGIN: verify_login,
    }
    directory.mkdir(parents=True, exist_ok=True)
    async with async_playwright() as playwright:
        browser = await playwright.chromium.launch(headless=True)
        context = await browser.new_context(viewport={"width": 1440, "height": 900}, reduced_motion="reduce")
        context.set_default_timeout(10000)
        context.set_default_navigation_timeout(20000)
        errors = []
        # Browser traces can record password field values. Do not trace live authentication.
        trace_enabled = name != LOGIN
        if trace_enabled:
            await context.tracing.start(screenshots=True, snapshots=True, sources=False)
        if name != LOGIN:
            await context.add_init_script("localStorage.setItem('yak-ops.current-workspace-id','ws1');")
            await context.route("**/api/**", mock)
        page = await context.new_page()
        page.on("pageerror", lambda error: errors.append(str(error)))
        try:
            await asyncio.wait_for(scenarios[name](page), timeout=150)
            assert not errors, f"Browser page errors: {errors}"
            if name != LOGIN:
                assert not state["unexpected_requests"], f"Unhandled mocked API paths: {state['unexpected_requests']}"
        except BaseException:
            try:
                await page.screenshot(path=str(directory / "failure.png"), full_page=True)
            except Exception:
                pass
            raise
        finally:
            if trace_enabled:
                await context.tracing.stop(path=str(directory / "trace.zip"))
            await context.close()
            await browser.close()

def main():
    parser = argparse.ArgumentParser(description=__doc__)
    parser.add_argument("--suite", choices=("mocked", "login", "full"), default="mocked")
    parser.add_argument("--scenario", choices=ALL)
    parser.add_argument("--base-url", default="http://127.0.0.1:5173")
    parser.add_argument("--version", default="local")
    parser.add_argument("--sha", default=os.environ.get("GITHUB_SHA", "local"))
    args = parser.parse_args()
    os.environ["YAK_UI_BASE_URL"] = args.base_url.rstrip("/")
    version = re.sub(r"[^A-Za-z0-9._-]", "_", args.version)
    sha = re.sub(r"[^A-Za-z0-9._-]", "_", args.sha)
    root = Path(os.environ.get("YAK_PLAYWRIGHT_ARTIFACTS", "/tmp/yak-playwright")) / ("v" + version.removeprefix("v")) / sha[:12]
    if args.scenario:
        os.environ["YAK_UI_ARTIFACT_DIR"] = str(root / args.scenario)
        start = time.monotonic()
        try:
            asyncio.run(check(args.scenario, root / args.scenario))
        except BaseException:
            traceback.print_exc()
            print(f"FAILED {args.scenario}", flush=True)
            return 1
        print(f"PASS {args.scenario} ({time.monotonic() - start:.1f}s)", flush=True)
        return 0
    names = SCENARIOS if args.suite == "mocked" else (LOGIN,) if args.suite == "login" else ALL
    root.mkdir(parents=True, exist_ok=True)
    results = []
    for name in names:
        start = time.monotonic()
        command = [sys.executable, __file__, "--scenario", name, "--base-url", args.base_url,
                   "--version", args.version, "--sha", args.sha]
        status = subprocess.run(command, check=False).returncode
        results.append({"scenario": name, "status": "PASS" if status == 0 else "FAIL",
                        "durationSeconds": round(time.monotonic() - start, 2)})
    report = {"version": args.version, "commitSha": args.sha, "suite": args.suite,
              "results": results, "status": "PASS" if all(x["status"] == "PASS" for x in results) else "FAIL"}
    (root / "playwright-results.json").write_text(json.dumps(report, indent=2) + "\n", encoding="utf-8")
    print(json.dumps(report), flush=True)
    if os.environ.get("GITHUB_STEP_SUMMARY"):
        with open(os.environ["GITHUB_STEP_SUMMARY"], "a", encoding="utf-8") as summary:
            summary.write(f"## Playwright UI Tests v{args.version.removeprefix('v')} ({args.sha[:12]})\n\n")
            for entry in results:
                summary.write(f"- {entry['scenario']}: {entry['status']} ({entry['durationSeconds']}s)\n")
    return 0 if report["status"] == "PASS" else 1

if __name__ == "__main__":
    sys.exit(main())
