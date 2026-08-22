#!/usr/bin/env python3
"""Automated guardrails for the Realtime Sync domain.

The script intentionally uses only Python's standard library so it can run:
- locally without Maven/Node dependencies;
- in GitHub Actions before any project dependency resolution.

It enforces stable architectural facts established by Realtime Sync DOMAIN.md.
"""

from __future__ import annotations

import argparse
import json
import re
import sys
from pathlib import Path

ROOT = Path(__file__).resolve().parents[1]
MODULE = ROOT / "yak-ops-business/yak-ops-business-sync/yak-ops-business-sync-realtime"
JAVA_ROOT = MODULE / "src/main/java/io/yak/ops/business/sync/realtime"
DOMAIN_DIR = JAVA_ROOT / "domain"

CORE_DOMAIN_FILES = (
    "RealtimeJobState.java",
    "SyncDefinition.java",
    "RuntimeEnvironmentRef.java",
    "DefinitionDigest.java",
    "SyncDefinitionDigestCalculator.java",
    "DefinitionVersion.java",
    "SyncExecution.java",
    "SyncExecutionStateMachine.java",
)

FORBIDDEN_CORE_IMPORT_PREFIXES = (
    "org.springframework.",
    "com.fasterxml.jackson.",
    "com.baomidou.",
    "org.mybatis.",
    "jakarta.persistence.",
    "io.yak.ops.business.sync.realtime.controller.",
    "io.yak.ops.business.sync.realtime.service.",
    "io.yak.ops.business.sync.realtime.repository.",
    "io.yak.ops.business.sync.realtime.dao.",
    "io.yak.ops.business.sync.realtime.engine.",
)

FORBIDDEN_CORE_IDENTIFIERS = (
    "pipelineYaml",
    "flinkHome",
    "flinkCdcHome",
    "flinkRestUrl",
    "sshHost",
    "sshUser",
    "identityFile",
    "jdbcUrl",
    "password",
    "sceneType",
    "syncType",
)

FORBIDDEN_DOMAIN_FILENAME_PATTERNS = (
    re.compile(r"^(Wizard|Yaml|Flink|Mysql|Postgres|Kafka).*(Spec|Definition|Task)\.java$", re.I),
    re.compile(r".*(SceneType|SyncType).*\.java$", re.I),
)


class Guardrails:
    def __init__(self) -> None:
        self.errors: list[str] = []
        self.checks = 0

    def check(self, condition: bool, message: str) -> None:
        self.checks += 1
        if not condition:
            self.errors.append(message)

    def require_file(self, path: Path) -> str:
        self.check(path.exists(), f"Missing required file: {path.relative_to(ROOT)}")
        if not path.exists():
            return ""
        return path.read_text(encoding="utf-8")

    def report(self) -> int:
        if self.errors:
            print("Realtime Sync Domain Guardrails: FAILED")
            for index, error in enumerate(self.errors, start=1):
                print(f"{index}. {error}")
            print(f"\n{len(self.errors)} failure(s), {self.checks} checks executed.")
            return 1
        print(f"Realtime Sync Domain Guardrails: OK ({self.checks} checks)")
        return 0


def java_code_without_comments_and_strings(text: str) -> str:
    # Imports are checked separately. Identifier checks only need comments and strings removed.
    text = re.sub(r"/\*.*?\*/", " ", text, flags=re.S)
    text = re.sub(r"//[^\n]*", " ", text)
    text = re.sub(r'"(?:\\.|[^"\\])*"', '""', text)
    text = re.sub(r"'(?:\\.|[^'\\])*'", "''", text)
    return text


def check_core_domain_purity(g: Guardrails) -> None:
    for filename in CORE_DOMAIN_FILES:
        path = DOMAIN_DIR / filename
        text = g.require_file(path)
        if not text:
            continue

        imports = re.findall(r"(?m)^\s*import\s+(?:static\s+)?([^;]+);", text)
        for imported in imports:
            g.check(
                imported.startswith("java.")
                or imported.startswith("io.yak.ops.business.sync.realtime.domain."),
                f"{filename}: Core Domain import is not JDK/domain-local: {imported}",
            )
            g.check(
                not imported.startswith(FORBIDDEN_CORE_IMPORT_PREFIXES),
                f"{filename}: forbidden framework/adapter import: {imported}",
            )

        code = java_code_without_comments_and_strings(text)
        for identifier in FORBIDDEN_CORE_IDENTIFIERS:
            g.check(
                re.search(rf"\b{re.escape(identifier)}\b", code) is None,
                f"{filename}: forbidden Core Domain identifier: {identifier}",
            )


def check_domain_naming(g: Guardrails) -> None:
    if not DOMAIN_DIR.exists():
        g.check(False, f"Missing domain directory: {DOMAIN_DIR.relative_to(ROOT)}")
        return

    for path in DOMAIN_DIR.glob("*.java"):
        for pattern in FORBIDDEN_DOMAIN_FILENAME_PATTERNS:
            g.check(
                pattern.match(path.name) is None,
                f"Domain anti-pattern filename requires domain review: {path.name}",
            )

        code = java_code_without_comments_and_strings(path.read_text(encoding="utf-8"))
        for identifier in ("sceneType", "syncType"):
            g.check(
                re.search(rf"\b{identifier}\b", code) is None,
                f"{path.name}: scene/sync type discriminator is forbidden without domain review: {identifier}",
            )


def check_execution_is_runtime_truth(g: Guardrails) -> None:
    dao_path = JAVA_ROOT / "dao/impl/RealtimeJobDaoImpl.java"
    query_path = MODULE / "src/main/resources/mapper/realtime/RealtimeJobQueryMapper.xml"
    store_path = JAVA_ROOT / "repository/RealtimeJobStore.java"

    dao = g.require_file(dao_path)
    query = g.require_file(query_path)
    store = g.require_file(store_path)

    for token in (
        "RealtimeJobDefinitionPO::getDesiredState",
        "RealtimeJobDefinitionPO::getObservedState",
        "RealtimeJobDefinitionPO::getLastError",
    ):
        g.check(token not in dao, f"Task runtime dual-write reintroduced in DAO: {token}")

    for token in ("d.desired_state", "d.observed_state", "d.last_error"):
        g.check(token not in query, f"Task runtime fallback reintroduced in query model: {token}")

    for method in ("desiredJobs", "hasOtherDesiredRunning", "markStarting"):
        g.check(
            re.search(rf"\b{method}\s*\(", store) is None,
            f"Legacy Task runtime side-path reintroduced in RealtimeJobStore: {method}",
        )

    g.check(
        "p.definition_version_id" in query and "d.published_definition_version_id" in query,
        "publishedUpdateAvailable must compare immutable DefinitionVersion IDs",
    )
    g.check(
        not re.search(
            r"p\.definition_version\s*(?:!=|<>|&lt;&gt;)\s*d\.published_version", query
        ),
        "Legacy DraftRevision comparison must not be used as version identity",
    )


def check_execution_commands(g: Guardrails) -> None:
    service_path = JAVA_ROOT / "service/RealtimeJobService.java"
    controller_path = JAVA_ROOT / "controller/v1/RealtimeJobController.java"
    frontend_api_path = ROOT / "yak-ops-ui/src/pages/realtime-sync/api.ts"

    service = g.require_file(service_path)
    controller = g.require_file(controller_path)
    frontend = g.require_file(frontend_api_path)

    g.check(
        re.search(r"\brestartExecution\s*\(", service) is not None,
        "RealtimeJobService must expose RestartExecution semantics",
    )
    g.check(
        re.search(r"\bapplyPublishedVersion\s*\(", service) is not None,
        "RealtimeJobService must expose ApplyPublishedVersion semantics",
    )
    g.check(
        re.search(r"\bpublic\s+[^\n{;]+\brestart\s*\(", service) is None,
        "Generic Application restart() must not be reintroduced",
    )
    g.check(
        "service.restart(" not in controller,
        "Controller compatibility endpoint must never delegate to generic restart()",
    )
    if '@PostMapping("/{id}/restart")' in controller:
        g.check(
            "service.restartExecution(id, key)" in controller,
            "Legacy HTTP /restart endpoint must delegate to restartExecution()",
        )

    g.check(
        re.search(r"\|\s*'restart'\s*;", frontend) is None
        and re.search(r"\|\s*'restart'\s*\n", frontend) is None,
        "Frontend RealtimeAction must not reintroduce generic 'restart'",
    )
    g.check(
        "'restart-execution'" in frontend and "'apply-published-version'" in frontend,
        "Frontend must keep restart-execution and apply-published-version as separate actions",
    )

    g.check(
        "requirePublishedDefinition(id)" in service,
        "Start path must resolve an immutable PublishedDefinitionRef",
    )
    g.check(
        "prepare(id, true)" not in service,
        "Start must not fall back to preparing the mutable current Draft",
    )


def check_digest_semantics(g: Guardrails) -> None:
    store = g.require_file(JAVA_ROOT / "repository/RealtimeJobStore.java")
    view = g.require_file(DOMAIN_DIR / "RealtimeJobView.java")

    for symbol in ("sourceConfigDigest()", "artifactDigest()", "draftRevision()"):
        g.check(
            symbol in store or symbol in view,
            f"Expected explicit digest/revision semantic alias is missing: {symbol}",
        )


def check_required_domain_docs(g: Guardrails) -> None:
    domain_contract = MODULE / "DOMAIN.md"
    stage6 = ROOT / "docs/realtime-sync/domain/06-stage6-migration-completion.md"
    stage7 = ROOT / "docs/realtime-sync/domain/07-automated-domain-guardrails.md"

    contract = g.require_file(domain_contract)
    g.require_file(stage6)
    g.require_file(stage7)

    for phrase in (
        "RealtimeSyncTask",
        "DefinitionVersion",
        "SyncExecution",
        "Domain Impact Analysis",
        "Domain Compliance Report",
    ):
        g.check(phrase in contract, f"DOMAIN.md lost mandatory contract phrase: {phrase}")


def check_pr_body(g: Guardrails, event_path: Path | None) -> None:
    if event_path is None:
        return
    if not event_path.exists():
        g.check(False, f"GitHub event file does not exist: {event_path}")
        return

    event = json.loads(event_path.read_text(encoding="utf-8"))
    pull_request = event.get("pull_request")
    if not pull_request:
        return

    body = pull_request.get("body") or ""
    for required in ("Domain Impact Analysis", "Domain Compliance Report", "Domain Gap"):
        g.check(
            required.lower() in body.lower(),
            f"Realtime-sync PR body must contain '{required}'",
        )


def parse_args() -> argparse.Namespace:
    parser = argparse.ArgumentParser()
    parser.add_argument(
        "--event",
        type=Path,
        help="Optional GitHub event JSON. Pull requests are checked for the domain review contract.",
    )
    return parser.parse_args()


def main() -> int:
    args = parse_args()
    g = Guardrails()
    check_core_domain_purity(g)
    check_domain_naming(g)
    check_execution_is_runtime_truth(g)
    check_execution_commands(g)
    check_digest_semantics(g)
    check_required_domain_docs(g)
    check_pr_body(g, args.event)
    return g.report()


if __name__ == "__main__":
    sys.exit(main())
