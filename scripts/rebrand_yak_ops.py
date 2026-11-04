#!/usr/bin/env python3
"""Rebrand Yak Ops-owned identifiers without altering upstream legal/provenance records.

Run --audit for inventory, --apply to rename names and references, --verify for residuals.
"""
from __future__ import annotations

import argparse
import collections
from pathlib import Path
import re
import subprocess
import sys
import xml.etree.ElementTree as ET

ROOT = Path(__file__).resolve().parent.parent
TERM = re.compile(r"dolphinscheduler|dolphin_scheduler", re.IGNORECASE)
TEXT_EXTS = {
    ".java", ".xml", ".json", ".properties", ".yaml", ".yml", ".sql",
    ".md", ".txt", ".sh", ".py", ".proto", ".gradle", ".scala", ".kt",
    ".js", ".jsx", ".ts", ".tsx", ".css", ".html", ".conf", ".ini", ".svg",
    ".toml", ".graphql", ".tpl",
}
PROVENANCE = (
    "docs/scheduler/",
    "scripts/migrate_ds343.py",
)
UPSTREAM_URL = re.compile(
    r"https?://(?:github\.com/apache/dolphinscheduler|"
    r"dolphinscheduler\.apache\.org)[^\s<>\"'\x60)]*",
    re.IGNORECASE,
)
PROVENANCE_NAME = re.compile(r"Apache DolphinScheduler(?:\s+3\.4\.3)?")
JAVA_TOKENS = re.compile(
    r'("(?:\\.|[^"\\])*"|'
    r"'(?:\\.|[^'\\])*'|"
    r"//[^\r\n]*|/\*[\s\S]*?\*/)",
    re.MULTILINE,
)

def tracked_paths() -> list[Path]:
    out = subprocess.check_output(["git", "ls-files", "-z"], cwd=ROOT)
    return [Path(x.decode("utf-8")) for x in out.split(b"\0") if x]

def excluded(name: str) -> bool:
    path = Path(name)
    if any(name.startswith(prefix) for prefix in PROVENANCE):
        return True
    if path.name in {"LICENSE", "NOTICE", "LICENSE.txt", "NOTICE.txt"}:
        return True
    if path.name.startswith("UPSTREAM"):
        return True
    if path.suffix not in TEXT_EXTS:
        return True
    return False

def protect_upstream(text: str) -> tuple[str, dict[str, str]]:
    references: dict[str, str] = {}
    def mask(match: re.Match) -> str:
        placeholder = f"\uE000{len(references):04d}\uE001"
        references[placeholder] = match.group(0)
        return placeholder
    text = UPSTREAM_URL.sub(mask, text)
    text = PROVENANCE_NAME.sub(mask, text)
    return text, references

def restore(text: str, references: dict[str, str]) -> str:
    for placeholder, value in references.items():
        text = text.replace(placeholder, value)
    return text

def rename_token(text: str, *, java_code: bool) -> str:
    # Already migrated packages must remain valid JVM package names.
    text = text.replace("org.apache.dolphinscheduler", "io.yak.ops")
    text = text.replace("org/apache/dolphinscheduler", "io/yak/ops")
    text = text.replace("DOLPHIN_SCHEDULER", "YAK_OPS")
    text = text.replace("dolphin_scheduler", "yak_ops")
    text = text.replace("DOLPHINSCHEDULER", "YAK_OPS")
    text = text.replace("DolphinScheduler", "YakOps")
    text = text.replace("Dolphinscheduler", "YakOps")
    text = text.replace("dolphinScheduler", "yakOps")
    if java_code:
        return text.replace("dolphinscheduler", "yakOps")
    return text.replace("dolphinscheduler", "yak-ops")

def rename_content(path: Path, content: str) -> str:
    content, references = protect_upstream(content)
    if path.suffix == ".java":
        segments = JAVA_TOKENS.split(content)
        content = "".join(
            rename_token(part, java_code=index % 2 == 0)
            for index, part in enumerate(segments)
        )
    else:
        content = rename_token(content, java_code=False)
    content = restore(content, references)
    # The imported project's developer mailing list is not Yak Ops contact info.
    if str(path) == "pom.xml":
        content, count = re.subn(
            r"\s*<mailingLists>[\s\S]*?</mailingLists>",
            "", content,
            count=1,
        )
        if count != 1 and "dolphinscheduler.apache.org" in content:
            raise RuntimeError("Stale upstream mailing list remains in root POM")
    return content

def rename_path(path: Path) -> Path:
    name = str(path)
    # Avoid renaming archive/provenance files.
    name = rename_token(name, java_code=False)
    # A class/file name uses UpperCamelCase; lower-case resource names use hyphens.
    return Path(name)

def list_matches(paths: list[Path]) -> tuple[dict[str, int], list[tuple[str, int]]]:
    totals: dict[str, int] = collections.Counter()
    examples: list[tuple[str, int]] = []
    for path in paths:
        name = str(path)
        if excluded(name) or not (ROOT / path).is_file():
            continue
        try:
            content = (ROOT / path).read_text(encoding="utf-8")
        except (UnicodeDecodeError, OSError):
            continue
        masked, _ = protect_upstream(content)
        count = len(TERM.findall(masked))
        if TERM.search(name):
            count += 1
        if count:
            totals[name.split("/")[0]] += count
            examples.append((name, count))
    return totals, examples

def validate_xml() -> None:
    checked = 0
    for path in ROOT.rglob("pom.xml"):
        if any(x in {"node_modules", "target", ".git"} for x in path.parts):
            continue
        ET.parse(path)
        checked += 1
    print(f"Validated {checked} Maven POM XML documents")

def apply(paths: list[Path]) -> None:
    edits = collections.Counter()
    renames = []
    for relative in paths:
        old = ROOT / relative
        if excluded(str(relative)) or not old.is_file():
            continue
        try:
            original = old.read_text(encoding="utf-8")
        except UnicodeDecodeError:
            continue
        text = rename_content(relative, original)
        new_relative = rename_path(relative)
        new = ROOT / new_relative
        if new != old:
            if new.exists():
                raise RuntimeError(f"Rename collision: {relative} -> {new_relative}")
            new.parent.mkdir(parents=True, exist_ok=True)
            new.write_text(text, encoding="utf-8")
            old.unlink()
            renames.append((str(relative), str(new_relative)))
        elif text != original:
            old.write_text(text, encoding="utf-8")
        else:
            continue
        edits[str(relative).split("/")[0]] += 1
    print(f"Rebranded {sum(edits.values())} files ({len(renames)} renamed paths)")
    for name, count in edits.most_common():
        print(f"  {name}: {count}")
    for old, new in renames:
        print(f"  RENAME {old} -> {new}")
    validate_xml()

def verify() -> None:
    files = [p.relative_to(ROOT) for p in ROOT.rglob("*") if p.is_file()
             and ".git" not in p.parts and "target" not in p.parts
             and "node_modules" not in p.parts]
    totals, examples = list_matches(files)
    if examples:
        print("Unexpected current-code references:", file=sys.stderr)
        for name, count in examples[:60]:
            print(f"  {count} {name}", file=sys.stderr)
        raise RuntimeError(f"Found {sum(totals.values())} stale references "
                           f"across {len(examples)} files")
    validate_xml()
    print("No unprotected DolphinScheduler references in active source/configuration")

def main() -> None:
    parser = argparse.ArgumentParser()
    mode = parser.add_mutually_exclusive_group(required=True)
    mode.add_argument("--audit", action="store_true")
    mode.add_argument("--apply", action="store_true")
    mode.add_argument("--verify", action="store_true")
    args = parser.parse_args()
    paths = tracked_paths()
    if args.audit:
        counts, examples = list_matches(paths)
        print(f"Audit: {sum(counts.values())} current-code occurrences in "
              f"{len(examples)} files")
        for name, count in counts.most_common():
            print(f"  {name}: {count}")
        for name, count in examples[:65]:
            print(f"  SAMPLE {count} {name}")
    elif args.apply:
        apply(paths)
    else:
        verify()

if __name__ == "__main__":
    main()
