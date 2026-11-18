#!/usr/bin/env python3
"""Cross-check the agent allowlist and tool registry against the OpenAPI contract.

Three-way consistency, no service needed:
  1. every allowlist operationId exists in yak-ops-3.1.json;
  2. every allowlist entry has exactly one matching tool in the agent's
     operations registry (and no orphan tools);
  3. each tool's method + normalized path matches its contract operation, the
     risk level agrees, and every query param name exists in the contract.

Usage:
    python scripts/check_agent_operations.py [--registry PATH] [--allowlist PATH] [--contract PATH]

Requires PyYAML. Exit code 0 = consistent, 1 = problems found.
"""

from __future__ import annotations

import argparse
import json
import re
import sys
from pathlib import Path

try:
    import yaml
except ImportError:  # pragma: no cover
    sys.exit("PyYAML is required: pip install pyyaml")

MODULE_ROOT = Path(__file__).resolve().parent.parent
DEFAULT_CONTRACT = MODULE_ROOT / "src/main/resources/openapi/yak-ops-3.1.json"
DEFAULT_ALLOWLIST = MODULE_ROOT / "src/main/resources/openapi/agent-allowlist.yaml"
DEFAULT_REGISTRY = MODULE_ROOT.parent / "yak-ops-agent/yak_agent/operations.yaml"

_VAR = re.compile(r"\{[^}]+\}")


def normalize_path(path: str) -> str:
    """Replace every template variable with {} so naming differences don't matter."""
    return _VAR.sub("{}", path)


def load_contract(path: Path) -> tuple[dict[str, dict], dict[tuple[str, str], dict]]:
    doc = json.loads(path.read_text(encoding="utf-8"))
    by_id: dict[str, dict] = {}
    by_route: dict[tuple[str, str], dict] = {}
    for route, methods in (doc.get("paths") or {}).items():
        for method, op in methods.items():
            if not isinstance(op, dict) or "responses" not in op:
                continue
            entry = {
                "operationId": op.get("operationId"),
                "method": method.upper(),
                "path": route,
                "params": op.get("parameters") or [],
            }
            if entry["operationId"]:
                by_id[entry["operationId"]] = entry
            by_route[(entry["method"], normalize_path(route))] = entry
    return by_id, by_route


def main() -> int:
    parser = argparse.ArgumentParser()
    parser.add_argument("--contract", type=Path, default=DEFAULT_CONTRACT)
    parser.add_argument("--allowlist", type=Path, default=DEFAULT_ALLOWLIST)
    parser.add_argument("--registry", type=Path, default=DEFAULT_REGISTRY)
    args = parser.parse_args()

    by_id, by_route = load_contract(args.contract)
    allow = yaml.safe_load(args.allowlist.read_text(encoding="utf-8")) or {}
    registry = yaml.safe_load(args.registry.read_text(encoding="utf-8")) or {}
    entries = allow.get("entries") or []
    tools = {op["id"]: op for op in (registry.get("operations") or [])}

    # drift check: the agent's runtime copy must match the reviewed original —
    # two copies of one fact would silently diverge.
    agent_copy = args.allowlist.parents[3] / "yak-ops-agent" / "yak_agent" / "agent-allowlist.yaml"
    if agent_copy.exists():
        if args.allowlist.read_text(encoding="utf-8") != agent_copy.read_text(encoding="utf-8"):
            print("1 problem(s):")
            print("  - allowlist drift: yak-ops-agent/yak_agent/agent-allowlist.yaml differs from the reviewed original; copy the original over the copy")
            return 1

    problems: list[str] = []
    expected_tools: set[str] = set()

    for entry in entries:
        op_id = entry.get("operationId")
        tool = entry.get("tool") or re.sub(r"(?<!^)(?=[A-Z])", "_", op_id or "").lower()
        expected_tools.add(tool)

        if entry.get("kind") == "script":
            # Non-REST tool (e.g. sandboxed script execution): no contract
            # operation and no registry entry to cross-check — it is materialized
            # by the executor, not the registry. Still counts as a reviewed tool.
            continue

        contract_op = by_id.get(op_id)
        if contract_op is None:
            problems.append(f"allowlist: operationId '{op_id}' not found in contract")
            continue

        op = tools.get(tool)
        if op is None:
            problems.append(f"allowlist: tool '{tool}' (operationId {op_id}) missing from operations registry")
            continue

        route = (op.get("method", "").upper(), normalize_path(op.get("path", "")))
        contract_route = (contract_op["method"], normalize_path(contract_op["path"]))
        if route != contract_route:
            problems.append(
                f"'{tool}': registry route {route[0]} {route[1]} != contract "
                f"{contract_route[0]} {contract_route[1]} (operationId {op_id})"
            )

        if op.get("risk") != entry.get("risk"):
            problems.append(f"'{tool}': risk {op.get('risk')} != allowlist {entry.get('risk')}")

        contract_query = {
            p.get("name") for p in contract_op["params"] if p.get("in") == "query"
        }
        for param in op.get("params") or []:
            if param.get("location") != "query":
                continue
            wire = param.get("as", param["name"])
            if wire not in contract_query:
                problems.append(f"'{tool}': query param '{wire}' not in contract operation {op_id}")

    orphan = set(tools) - expected_tools
    if orphan:
        problems.append(f"registry tools not covered by allowlist: {sorted(orphan)}")

    print(f"allowlist entries: {len(entries)} | registry tools: {len(tools)} | contract operations: {len(by_id)}")
    if problems:
        print(f"\n{len(problems)} problem(s):")
        for item in problems:
            print(f"  - {item}")
        return 1
    print("OK: allowlist, registry and contract are consistent")
    return 0


if __name__ == "__main__":
    sys.exit(main())
