#!/usr/bin/env python3
"""Select and run native Maven database tests; require positive JUnit evidence."""
from __future__ import annotations

import argparse
import json
import os
from pathlib import Path
import shutil
import subprocess
import sys
import time
import xml.etree.ElementTree as ET

sys.path.insert(0, str(Path(__file__).resolve().parents[1] / 'ci'))
import impact

ROOT = Path(__file__).resolve().parents[2]
JDBC = "yak-flow/yak-flow-connector-jdbc"
CDC = "yak-flow/yak-flow-connector-cdc-mysql"
BUSINESS = "yak-ops-business/yak-ops-business-task"
E2E = "yak-ops-e2e"
# Minimum method counts prevent an empty subclass, lost inherited tests or partial run from passing.
SUITES = {
    "jdbc-mysql": (JDBC, "jdbc & mysql", {"MySqlSourceIT": 6, "MySqlSinkIT": 3, "MySqlKeylessRetractIT": 8, "MySqlXaSinkIT": 3}),
    "jdbc-postgres": (JDBC, "jdbc & postgres", {"PostgresSourceIT": 5, "PostgresSinkIT": 3, "PostgresKeylessRetractIT": 3}),
    "jdbc-oracle": (JDBC, "jdbc & oracle", {"OracleSourceIT": 5, "OracleSinkIT": 3, "OracleKeylessRetractIT": 4}),
    "pipeline": (JDBC, "pipeline", {name: 2 for name in ("MySqlToMySqlIT", "MySqlToPostgresIT", "PostgresToOracleIT", "OracleToMySqlIT")}),
    "business": (BUSINESS, "business", {"TaskCursorIT": 6, "ScheduleAdmissionIT": 13, "DefinitionPageQueryIT": 3}),
    "cdc": (CDC, "cdc", {"MySqlSnapshotBackfillIT": 6, "MySqlDebeziumRecoveryIT": 1, "MySqlUnsignedChunkIT": 1, "MySqlStartupModesIT": 2}),
    "backend-e2e": (E2E, "backend-e2e", {"TaskExecutionIT": 5, "BatchSyncIT": 2, "RealtimeSyncIT": 4, "SnapshotBackfillSinkIT": 1, "SingleTableRealtimeSchemaIT": 2, "OfflineIncrementalIT": 3, "SchemaMigrationIT": 2, "ScheduleIT": 1}),
    "recovery-e2e": (E2E, "recovery-e2e", {"ProcessRecoveryIT": 2}),
}


def select(paths: list[str]) -> list[str]:
    """Use the same change impact classification as the PR quality jobs."""
    return impact.select_suites(paths)


def selected_suites(request: str) -> list[str]:
    if request == "full":
        return list(SUITES)
    if request in SUITES:
        return [request]
    if request != "auto":
        raise ValueError("Unknown suite: " + request)
    if os.environ.get("CI_EVENT_NAME") != "pull_request":
        return list(SUITES)
    base, head = os.environ.get("CI_PR_BASE_SHA"), os.environ.get("CI_PR_HEAD_SHA")
    if not base or not head:
        raise ValueError("PR selection requires base and head SHAs")
    diff = subprocess.check_output(["git", "diff", "--name-only", "--no-renames", "-z", base + "..." + head], cwd=ROOT)
    return select([p.decode("utf-8") for p in diff.split(b"\0") if p])


def check_registration(root: Path = ROOT) -> None:
    """Every concrete database IT in the owned modules needs a suite entry."""
    expected = {name for _, _, tests in SUITES.values() for name in tests}
    actual = {p.stem for module in ({CDC} | {spec[0] for spec in SUITES.values()}) for p in (root / module / "src/test").rglob("*IT.java")}
    if actual != expected:
        raise ValueError(f"IT registration mismatch: unregistered={sorted(actual-expected)}, missing={sorted(expected-actual)}")


def check_reports(names: list[str], root: Path = ROOT) -> dict[str, int]:
    reports: dict[str, list[ET.Element]] = {}
    for module in {SUITES[name][0] for name in names}:
        for path in (root / module / "target/failsafe-reports").glob("TEST-*.xml"):
            xml = ET.parse(path).getroot()
            for suite in [xml] if xml.tag == "testsuite" else xml.findall("testsuite"):
                for case in suite.findall("testcase"):
                    classname = case.get("classname", suite.get("name", "")).split(".")[-1]
                    reports.setdefault(classname, []).append(case)
                if any(int(suite.get(key, "0")) > 0 for key in ("failures", "errors", "skipped")):
                    raise ValueError("Failed or skipped selected integration test: " + str(path))
    counts: dict[str, int] = {}
    for name in names:
        for classname, minimum in SUITES[name][2].items():
            cases = reports.get(classname, [])
            if len(cases) < minimum:
                raise ValueError(f"Missing or incomplete {classname}: {len(cases)} test cases; expected at least {minimum}")
            if any(case.find(tag) is not None for case in cases for tag in ("skipped", "failure", "error")):
                raise ValueError("Failed or skipped " + classname)
            counts[classname] = len(cases)
    return counts


def require_results(names: list[str], scope_result: str, tests_result: str) -> str:
    if scope_result != "success":
        raise ValueError("Backend test selection did not succeed")
    if not names:
        if tests_result not in {"skipped", "success"}:
            raise ValueError("Unexpected result for unselected tests: " + tests_result)
        return "NOT SELECTED"
    if tests_result != "success":
        raise ValueError("Selected backend tests did not succeed: " + tests_result)
    return "PASS (selected suites only)"


def run(name: str) -> None:
    names = list(SUITES) if name == "full" else [name]
    if any(n not in SUITES for n in names):
        raise ValueError("Unknown backend test suite: " + name)
    check_registration()
    # Do not reuse XML from a previous local invocation as current evidence.
    for module in {spec[0] for spec in SUITES.values()}:
        for output in ("failsafe-reports", "test-artifacts"):
            shutil.rmtree(ROOT / module / "target" / output, ignore_errors=True)
    evidence = ROOT / "target/test-artifacts"
    shutil.rmtree(evidence, ignore_errors=True)
    evidence.mkdir(parents=True)
    sha = subprocess.check_output(["git", "rev-parse", "HEAD"], cwd=ROOT, text=True).strip()
    module = E2E if name == "full" else SUITES[name][0]
    groups = " | ".join("(" + SUITES[n][1] + ")" for n in names)
    command = ["bash", "mvnw", "-B", "-ntp", "-pl", module, "-am", "-Pintegration-tests",
               "-Dspotless.check.skip=true", "-Dcheckstyle.skip=true",
               "-Dbackend.test.groups=" + groups, "verify"]
    record = {"sha": sha, "suites": names, "command": command, "status": "NOT RUN"}
    started = time.monotonic()
    try:
        subprocess.run(["docker", "info"], check=True, stdout=subprocess.DEVNULL, stderr=subprocess.PIPE, timeout=30)
        with (evidence / "maven.log").open("w") as log:
            process = subprocess.Popen(command, cwd=ROOT, stdout=subprocess.PIPE, stderr=subprocess.STDOUT, text=True, bufsize=1)
            try:
                assert process.stdout is not None
                for line in process.stdout:
                    print(line, end="", flush=True)
                    log.write(line)
                code = process.wait()
                if code:
                    raise RuntimeError("Maven failed with exit code " + str(code))
            finally:
                if process.poll() is None:
                    process.terminate()
                    try:
                        process.wait(timeout=15)
                    except subprocess.TimeoutExpired:
                        process.kill()
        record["testCounts"] = check_reports(names)
        record["status"] = "PASS"
    except BaseException:
        record["status"] = "FAIL"
        raise
    finally:
        record["elapsedSeconds"] = round(time.monotonic() - started, 3)
        (evidence / "result.json").write_text(json.dumps(record, indent=2) + "\n")
        # Test-only secrets must not leak through driver, process or container diagnostics.
        for folder in [evidence] + [ROOT / module / "target/test-artifacts" for module in {s[0] for s in SUITES.values()}]:
            for path in folder.rglob("*"):
                if path.is_file() and path.suffix in {".log", ".json", ".txt"}:
                    text = path.read_text(errors="replace")
                    for secret in ("yak-test-only-password", "backend-e2e-only-password", "backend-test-isolated-master-key"):
                        text = text.replace(secret, "[REDACTED]")
                    path.write_text(text)


def main() -> None:
    parser = argparse.ArgumentParser()
    parser.add_argument("action", choices=("select", "run", "check", "registration"))
    parser.add_argument("suite", nargs="?", default="full")
    args = parser.parse_args()
    if args.action == "registration":
        check_registration()
    elif args.action == "run":
        run(args.suite)
    elif args.action == "check":
        names = json.loads(os.environ["SELECTED_SUITES"])
        print(require_results(names, os.environ["SCOPE_RESULT"], os.environ["TESTS_RESULT"]))
    else:
        names = selected_suites(args.suite)
        print("Selected suites:", json.dumps(names))
        output = os.environ.get("GITHUB_OUTPUT")
        if output:
            with open(output, "a") as stream:
                stream.write("suites=" + json.dumps(names) + "\n")
                stream.write("matrix=" + json.dumps({"suite": names}) + "\n")
        if os.environ.get("GITHUB_STEP_SUMMARY"):
            with open(os.environ["GITHUB_STEP_SUMMARY"], "a") as stream:
                stream.write("Backend tests: " + (", ".join(names) if names else "NOT SELECTED") + "\n")


if __name__ == "__main__":
    main()
