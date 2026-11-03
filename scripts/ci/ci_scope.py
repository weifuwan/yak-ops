#!/usr/bin/env python3
"""Derive format and unit-test selectors from actual pull-request or push ranges."""
from __future__ import annotations
import argparse
import os
import subprocess
from pathlib import Path
import impact

ROOT = Path(__file__).resolve().parents[2]

def changed_paths() -> list[str] | None:
    event = os.environ.get("GITHUB_EVENT_NAME", "")
    if event == "workflow_dispatch":
        return None
    if event == "pull_request":
        base, head = os.environ.get("CI_PR_BASE_SHA"), os.environ.get("CI_PR_HEAD_SHA")
        if not base or not head:
            raise ValueError("Missing PR base/head SHA")
        refs = [base + "..." + head]
    elif event == "push":
        before, head = os.environ.get("CI_PUSH_BEFORE_SHA", ""), os.environ.get("CI_CURRENT_SHA", "")
        if not before or set(before) == {"0"} or not head:
            return None
        refs = [before, head]
    else:
        return None
    changed = subprocess.check_output(["git", "diff", "--name-only", "--no-renames", "-z", *refs], cwd=ROOT)
    return [part.decode("utf-8") for part in changed.split(b"\0") if part]

def selection(paths: list[str] | None, *, mode: str, event: str, branch: str) -> dict[str, str]:
    result = impact.analyze(paths or [], full=paths is None)
    if mode == "tests" and event == "push" and branch == "main":
        result["full"] = True
    selected = impact.reactor_modules() if result["full"] else result["modules"]
    # Explicit module paths prevent an aggregator selector from including CDC children.
    unit_modules = ",".join(sorted(module for module in selected if impact.is_ci_unit_module(module)))
    format_full = paths is None or any(
        path in {"pom.xml", ".editorconfig"} or path.startswith((".mvn/", "tools/maven/"))
        for path in paths
    )
    backend_format = impact.ALL_BACKEND if format_full else ",".join(sorted(result["format_modules"]))
    return {"frontend": str(result["frontend_format"]).lower(),
            "backend_modules": backend_format, "unit_modules": unit_modules}

def main() -> None:
    parser = argparse.ArgumentParser()
    parser.add_argument("mode", choices=("format", "tests"))
    args = parser.parse_args()
    fields = selection(changed_paths(), mode=args.mode,
                       event=os.environ.get("GITHUB_EVENT_NAME", ""),
                       branch=os.environ.get("GITHUB_REF_NAME", ""))
    for key, value in fields.items():
        print(key + "=" + value)
    if os.environ.get("GITHUB_OUTPUT"):
        with open(os.environ["GITHUB_OUTPUT"], "a", encoding="utf-8") as stream:
            for key, value in fields.items():
                stream.write(key + "=" + value + "\n")

if __name__ == "__main__":
    main()
