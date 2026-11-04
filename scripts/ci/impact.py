#!/usr/bin/env python3
"""Select format modules, unit modules and registered database suites from changed files."""
from __future__ import annotations
from functools import lru_cache
from pathlib import Path
import xml.etree.ElementTree as ET

ROOT = Path(__file__).resolve().parents[2]
SUITES = ("business",)
ALL_BACKEND = "!yak-ops-bom,!yak-ops-ui,!yak-ops-dist"
NAMESPACE = {"m": "http://maven.apache.org/POM/4.0.0"}
CDC_ROOT = "yak-ops-cdc/"
CDC = CDC_ROOT + "yak-ops-cdc-connect/yak-ops-cdc-source-connectors/yak-ops-connector-mysql-cdc/"
BUSINESS = "yak-ops-business/"
CI_UNIT_EXCLUDED = frozenset({"yak-ops-bom", "yak-ops-ui", "yak-ops-dist"})

def is_ci_unit_module(module: str) -> bool:
    """Only native backend modules belong in automatic unit-test Maven reactors."""
    return module not in CI_UNIT_EXCLUDED and module != "yak-ops-cdc" and not module.startswith(CDC_ROOT)

@lru_cache(maxsize=1)
def reactor_modules() -> tuple[str, ...]:
    modules = []
    def visit(folder: Path) -> None:
        pom = ET.parse(folder / "pom.xml").getroot()
        children = [node.text for node in pom.findall("m:modules/m:module", NAMESPACE)]
        if children:
            for child in children:
                visit(folder / child)
        elif folder != ROOT:
            modules.append(folder.relative_to(ROOT).as_posix())
    visit(ROOT)
    return tuple(modules)

def module_for(path: str) -> list[str]:
    modules = reactor_modules()
    matches = [module for module in modules if path.startswith(module + "/")]
    if matches:
        return [max(matches, key=len)]
    if path.endswith("/pom.xml"):
        prefix = path.removesuffix("/pom.xml") + "/"
        return [module for module in modules if module.startswith(prefix)]
    return []

@lru_cache(maxsize=1)
def module_dependents() -> dict[str, set[str]]:
    artifacts = {}
    projects = {}
    for module in reactor_modules():
        pom = ET.parse(ROOT / module / "pom.xml").getroot()
        projects[module] = pom
        artifact = pom.findtext("m:artifactId", namespaces=NAMESPACE)
        if artifact:
            artifacts[artifact] = module
    result = {module: set() for module in projects}
    for module, pom in projects.items():
        for dependency in pom.findall("m:dependencies/m:dependency", NAMESPACE):
            if dependency.findtext("m:groupId", namespaces=NAMESPACE) != "io.yak.ops":
                continue
            supplier = artifacts.get(dependency.findtext("m:artifactId", namespaces=NAMESPACE))
            if supplier:
                result[supplier].add(module)
    return result

def affected_modules(owners: list[str]) -> set[str]:
    dependents = module_dependents()
    selected = set(owners)
    pending = list(owners)
    while pending:
        for module in dependents.get(pending.pop(), set()):
            if module not in selected:
                selected.add(module)
                pending.append(module)
    return selected - {"yak-ops-ui", "yak-ops-dist", "yak-ops-bom"}

def test_suites(path: str) -> set[str]:
    if "/src/test/" in path:
        if not path.endswith("IT.java"):
            return set()
        if path.startswith(BUSINESS):
            return {"business"}
        return set()
    if path.startswith((
        BUSINESS, "yak-ops-dao/", "yak-ops-platform/", "yak-ops-task-execution/",
        "yak-ops-plugins/yak-ops-plugin-task/",
        "yak-ops-plugins/yak-ops-plugin-datasource/", "yak-ops-boot/",
    )):
        return {"business"}
    return set()

def analyze(paths: list[str], *, full: bool = False) -> dict:
    result = {"frontend_format": False, "format_modules": set(),
              "modules": set(), "suites": set(), "full": full}
    def require_full() -> None:
        result["full"] = True
        result["suites"].update(SUITES)
    if full:
        require_full()
        result["frontend_format"] = True
    for path in paths:
        if path.startswith("yak-ops-ui/"):
            result["frontend_format"] = True
            continue
        if path.startswith("docs/") or path.endswith(".md") or path in {
            "README.md", "LICENSE", "NOTICE", ".gitignore", ".asf.yaml",
        }:
            continue
        if path in {"pom.xml", "mvnw", "mvnw.cmd", ".editorconfig"} or path.startswith(
            ("yak-ops-bom/", ".mvn/", "tools/maven/")
        ):
            require_full()
            continue
        if path in {".github/workflows/backend-tests.yml", "scripts/tests/backend_tests.py"} or path.startswith(
            ("scripts/tests/", "scripts/ci/")
        ):
            require_full()
            continue
        if path.startswith(".github/workflows/"):
            continue
        if path.startswith(("scripts/release/", "docker/")) or path in {
            "Dockerfile", "compose.yaml", "compose.without-mysql.yaml", "release.env",
        }:
            continue
        if path.startswith("yak-ops-configuration/"):
            result["modules"].update(affected_modules(["yak-ops-common"]))
            continue
        if path == "scripts/verify_logging_rules.py":
            result["modules"].add("yak-ops-boot")
            continue
        owners = [module for module in module_for(path) if module not in {
            "yak-ops-ui", "yak-ops-dist", "yak-ops-bom",
        }]
        if owners:
            result["modules"].update(affected_modules(owners))
            if path.endswith(".java"):
                result["format_modules"].update(owners)
            result["suites"].update(test_suites(path))
            continue
        require_full()
    return result

def select_suites(paths: list[str]) -> list[str]:
    selected = analyze(paths)["suites"]
    return [suite for suite in SUITES if suite in selected]
