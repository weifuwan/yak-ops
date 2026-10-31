#!/usr/bin/env python3
"""One source of truth for PR quality, Maven modules and database-test impact."""
from __future__ import annotations

import ast
from functools import lru_cache
import os
from pathlib import Path
import subprocess
import sys
import xml.etree.ElementTree as ET

ROOT = Path(__file__).resolve().parents[2]
SUITES = ("jdbc-mysql", "jdbc-postgres", "jdbc-oracle", "pipeline", "business", "cdc", "backend-e2e", "recovery-e2e")
JDBC = "yak-flow/yak-flow-connector-jdbc/"
CDC = "yak-flow/yak-flow-connector-cdc-mysql/"
CDC_BASE = "yak-flow/yak-flow-connector-cdc-base/"
RUNTIME = "yak-flow/yak-flow-runtime/"
ENGINE = "yak-ops-engine/"
E2E = "yak-ops-e2e/"
BUSINESS = "yak-ops-business/"
NAMESPACE = {"m": "http://maven.apache.org/POM/4.0.0"}


@lru_cache(maxsize=1)
def reactor_modules() -> tuple[str, ...]:
    """Read the actual nested reactor; new modules require no hard-coded path list."""
    result = []

    def visit(folder: Path) -> None:
        pom = ET.parse(folder / "pom.xml").getroot()
        children = [element.text for element in pom.findall("m:modules/m:module", NAMESPACE)]
        if children:
            for child in children:
                visit(folder / child)
        elif folder != ROOT:
            result.append(folder.relative_to(ROOT).as_posix())

    visit(ROOT)
    return tuple(result)


def module_for(path: str) -> list[str]:
    modules = reactor_modules()
    owners = [m for m in modules if path.startswith(m + "/")]
    if owners:
        return [max(owners, key=len)]
    # An aggregator POM or module configuration owns its children.
    if path.endswith("/pom.xml"):
        folder = path.removesuffix("/pom.xml")
        return [m for m in modules if m.startswith(folder + "/")]
    return []


@lru_cache(maxsize=1)
def module_dependents() -> dict[str, set[str]]:
    """Map reactor dependencies to consumers so changed contracts compile downstream."""
    projects = {}
    artifacts = {}
    for module in reactor_modules():
        project = ET.parse(ROOT / module / "pom.xml").getroot()
        projects[module] = project
        artifact = project.findtext("m:artifactId", namespaces=NAMESPACE)
        if artifact:
            artifacts[artifact] = module
    consumers = {module: set() for module in projects}
    for module, project in projects.items():
        for dependency in project.findall("m:dependencies/m:dependency", NAMESPACE):
            if dependency.findtext("m:groupId", namespaces=NAMESPACE) != "io.yak.ops":
                continue
            producer = artifacts.get(dependency.findtext("m:artifactId", namespaces=NAMESPACE))
            if producer:
                consumers[producer].add(module)
    return consumers


def affected_modules(owners: list[str]) -> set[str]:
    """Only changed modules and actual transitively dependent modules need unit checks."""
    consumers = module_dependents()
    selected = set(owners)
    pending = list(owners)
    while pending:
        for module in consumers.get(pending.pop(), set()):
            if module not in selected:
                selected.add(module)
                pending.append(module)
    return selected - {"yak-ops-ui", "yak-ops-dist"}


def test_suites(path: str) -> set[str]:
    """Select registered integration suites, including connector-local CDC contracts."""
    if "/src/test/" in path:
        if not path.endswith("IT.java"):
            return set()
        filename = path.rsplit("/", 1)[-1]
        if path.startswith(JDBC):
            if "To" in filename or "/integration/" in path:
                return {"pipeline"}
            vendor = next((v for v in ("mysql", "postgres", "oracle") if v in filename.lower()), None)
            return {"jdbc-" + vendor} if vendor else {"pipeline"}
        if path.startswith(CDC):
            return {"cdc"}
        if path.startswith(E2E):
            return {"recovery-e2e"} if "Recovery" in filename else {"backend-e2e"}
        if path.startswith(BUSINESS):
            return {"business"}
        return set()

    if path.startswith(JDBC):
        vendor = next((v for v in ("mysql", "postgres", "oracle") if v in path.lower().removeprefix(JDBC)), None)
        if vendor:
            suites = {"jdbc-" + vendor, "pipeline", "backend-e2e"}
            return suites | ({"recovery-e2e"} if vendor == "mysql" else set())
        return {"jdbc-mysql", "jdbc-postgres", "jdbc-oracle", "pipeline", "backend-e2e", "recovery-e2e"}
    if path.startswith(CDC_BASE) or path.startswith(CDC):
        return {"cdc", "backend-e2e", "recovery-e2e"}
    if path.startswith(RUNTIME):
        return {"pipeline", "recovery-e2e", "backend-e2e"}
    if path.startswith(ENGINE):
        return {"pipeline", "cdc", "backend-e2e", "recovery-e2e"}
    if path.startswith("yak-ops-plugins/yak-ops-plugin-task/yak-ops-plugin-task-data-sync/"):
        return {"backend-e2e", "recovery-e2e"}
    if path.startswith("yak-flow/yak-flow-connector-base/"):
        return {"pipeline", "backend-e2e", "recovery-e2e"}
    if path.startswith("yak-ops-core/"):
        return {"pipeline", "backend-e2e", "recovery-e2e"}
    if path.startswith("yak-ops-business/yak-ops-business-task/"):
        return {"business", "backend-e2e"}
    if path.startswith("yak-ops-business/yak-ops-business-schedule/"):
        return {"business", "backend-e2e"}
    if path.startswith("yak-ops-business/"):
        return {"business"}
    if path.startswith("yak-ops-task-execution/"):
        return {"backend-e2e", "recovery-e2e"}
    if path.startswith(E2E):
        return {"backend-e2e", "recovery-e2e"}
    if path.startswith(("yak-ops-boot/", "yak-ops-plugins/")):
        return {"backend-e2e"}
    if path.startswith("yak-ops-dao/"):
        return {"business", "backend-e2e"}
    if path.startswith("yak-ops-platform/"):
        return {"business", "backend-e2e"}
    if path.startswith(("yak-ops-common/", "yak-ops-configuration/", "yak-ops-spi/", "yak-ops-bom/")):
        # Compile/unit-check shared contracts, but do not run unrelated databases.
        return set()
    return set()


def compare_suite_registry(before: str, after: str) -> set[str] | None:
    """Return only modified suite entries when runner logic is unchanged.

    None means an unrecognized/unsafe change and requires the full test matrix.
    """
    try:
        previous, current = ast.parse(before), ast.parse(after)

        def entries(module: ast.Module) -> dict[str, str]:
            assignments = [
                node for node in module.body
                if isinstance(node, ast.Assign)
                and any(isinstance(target, ast.Name) and target.id == "SUITES" for target in node.targets)
            ]
            if len(assignments) != 1 or not isinstance(assignments[0].value, ast.Dict):
                raise ValueError("Expected one literal suite registry")
            registry = assignments[0]
            suites = {}
            for key, value in zip(registry.value.keys, registry.value.values):
                if not isinstance(key, ast.Constant) or not isinstance(key.value, str) or key.value in suites:
                    raise ValueError("Non-literal or duplicate suite name")
                if key.value not in SUITES:
                    raise ValueError("Unknown suite")
                suites[key.value] = ast.dump(value, include_attributes=False)
            module.body.remove(registry)
            return suites

        old_suites, new_suites = entries(previous), entries(current)
        if ast.dump(previous, include_attributes=False) != ast.dump(current, include_attributes=False):
            return None
        return {
            suite for suite in old_suites.keys() | new_suites.keys()
            if old_suites.get(suite) != new_suites.get(suite)
        }
    except (SyntaxError, ValueError, TypeError):
        return None


def suite_registration_changes() -> set[str] | None:
    """Inspect the PR endpoints, not checkout's synthetic merge commit."""
    base = os.environ.get("CI_PR_BASE_SHA")
    head = os.environ.get("CI_PR_HEAD_SHA")
    if not base or not head or os.environ.get("CI_EVENT_NAME") != "pull_request":
        return None
    path = "scripts/tests/backend_tests.py"
    try:
        before = subprocess.check_output(["git", "show", f"{base}:{path}"], cwd=ROOT, text=True)
        after = subprocess.check_output(["git", "show", f"{head}:{path}"], cwd=ROOT, text=True)
    except (OSError, subprocess.CalledProcessError, UnicodeError):
        return None
    return compare_suite_registry(before, after)


def analyze(paths: list[str], *, full: bool = False) -> dict:
    result = dict(backend=False, frontend=False, distribution=False, release=False,
                  modules=set(), suites=set(), suite_reasons={}, full=False,
                  preflight=False, preflight_modules=set())

    def add_suites(suites: set[str] | tuple[str, ...], reason: str) -> None:
        result["suites"].update(suites)
        for suite in suites:
            result["suite_reasons"].setdefault(suite, set()).add(reason)

    def all_checks(reason: str) -> None:
        result.update(backend=True, frontend=True, distribution=True, release=True, full=True,
                      preflight=True)
        result["preflight_modules"].clear()
        add_suites(SUITES, reason)

    if full:
        all_checks("Release Gate: full verification")
    for path in paths:
        if path.startswith("docs/") or path.endswith(".md") or path in {".gitignore", ".asf.yaml", "LICENSE"}:
            continue
        if path.startswith("yak-ops-ui/"):
            result["frontend"] = True
            if path in {"yak-ops-ui/pom.xml", "yak-ops-ui/package.json", "yak-ops-ui/package-lock.json"}:
                result["distribution"] = True
            continue
        if path.startswith((".github/workflows/playwright-", ".github/ISSUE_TEMPLATE/")):
            continue
        if path.startswith("scripts/ci/") or path.startswith("scripts/tests/test_") or path == "scripts/tests/__init__.py":
            # Scope unit tests are always run in the cheap select job.
            continue
        if path == "scripts/tests/backend_tests.py":
            changed = suite_registration_changes()
            if changed is None:
                add_suites(SUITES, f"{path}: runner logic changed or diff unavailable")
            else:
                add_suites(changed, f"{path}: suite registration only")
            continue
        if path.startswith("scripts/tests/") or path == ".github/workflows/backend-tests.yml":
            add_suites(SUITES, f"{path}: test execution infrastructure")
            continue
        if path in {".github/workflows/quality-check.yml"}:
            all_checks(f"{path}: workflow definition")
            continue
        if path.startswith(".github/workflows/") or path.startswith("scripts/release/") or path == "release.env":
            result["release"] = True
            continue
        if path in {"Dockerfile", "compose.yaml", "compose.without-mysql.yaml", "NOTICE", ".env.example"} or path.startswith(("docker/", "yak-ops-dist/")):
            result["distribution"] = True
            continue
        if path == "pom.xml" or path in {"mvnw", "mvnw.cmd"} or path.startswith((".mvn/", "tools/maven/")):
            all_checks(f"{path}: build infrastructure")
            continue
        if path.startswith("yak-ops-dao/") and "/db/migration/" in path:
            result["release"] = True
        owners = [m for m in module_for(path) if m not in {"yak-ops-ui", "yak-ops-dist"}]
        if owners:
            result["backend"] = True
            result["modules"].update(affected_modules(owners))
            # Spotless and Checkstyle cover production Java; check direct owners only.
            if "/src/main/java/" in path or path.endswith("/pom.xml"):
                # BOM has a standalone parent and does not inherit these plugins.
                preflight_owners = set(owners) - {"yak-ops-bom"}
                if preflight_owners:
                    result["preflight"] = True
                    if not result["full"]:
                        result["preflight_modules"].update(preflight_owners)
            add_suites(test_suites(path), path)
            continue
        # An unknown path might change a build or generated contract: fail closed.
        all_checks(f"{path}: unknown impact")
    return result



def summary(paths: list[str], *, full: bool = False) -> str:
    """Human-readable selection evidence in the Detect Changes job."""
    result = analyze(paths, full=full)
    lines = ["### Selected CI checks", "", "| Check | Run |", "| --- | --- |"]
    for key, label in (("backend", "Backend Quality"), ("frontend", "Frontend Quality"),
                       ("distribution", "Distribution Quality"), ("release", "Release Checks")):
        lines.append(f"| {label} | {'Yes' if result[key] else 'No'} |")
    preflight_modules = ("!yak-ops-bom,!yak-ops-ui,!yak-ops-dist" if result["full"]
                         else ",".join(sorted(result["preflight_modules"])))
    lines.extend(["", "### Fast Preflight", "",
                  f"Run: {'Yes' if result['preflight'] else 'No'}",
                  f"Modules: {preflight_modules if result['preflight'] else '-'}",
                  "", "### Selected database suites", "", "| Suite | Reason |", "| --- | --- |"])
    if not result["suites"]:
        lines.append("| None | No relevant database changes |")
    for suite in SUITES:
        if suite in result["suites"]:
            reasons = ", ".join(sorted(result["suite_reasons"].get(suite, {"Full verification"})))
            lines.append(f"| {suite} | {reasons.replace('|', '/').replace(chr(10), ' ')} |")
    return "\n".join(lines) + "\n"


def select_suites(paths: list[str]) -> list[str]:
    result = analyze(paths)
    return [name for name in SUITES if name in result["suites"]]


def outputs(paths: list[str], *, full: bool = False) -> str:
    impact = analyze(paths, full=full)
    selected_modules = "!yak-ops-bom,!yak-ops-ui,!yak-ops-dist" if impact["full"] else ",".join(sorted(impact["modules"]))
    fields = {
        "backend": str(impact["backend"]).lower(),
        "frontend": str(impact["frontend"]).lower(),
        "distribution": str(impact["distribution"]).lower(),
        "release": str(impact["release"]).lower(),
        "tests": str(bool(impact["suites"])).lower(),
        "modules": selected_modules,
        "suites": ",".join(name for name in SUITES if name in impact["suites"]),
        "preflight": str(impact["preflight"]).lower(),
        "preflight_modules": (
            "!yak-ops-bom,!yak-ops-ui,!yak-ops-dist" if impact["full"]
            else ",".join(sorted(impact["preflight_modules"]))
        ),
    }
    return "\n".join(f"{key}={value}" for key, value in fields.items()) + "\n"


if __name__ == "__main__":
    # Paths are collected using git -z, so spaces and non-ASCII file names are safe.
    print(outputs(sys.argv[1:]), end="")
