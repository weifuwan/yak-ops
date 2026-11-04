#!/usr/bin/env python3
"""Remove only standard ASF boilerplate headers from imported source files.

Do not remove third-party copyright notices; preserve repo LICENSE and NOTICE.
"""
from __future__ import annotations

import collections
from pathlib import Path
import re
import sys

SOURCE_EXTENSIONS = {
    ".java", ".xml", ".sql", ".sh", ".py", ".proto", ".properties",
    ".yaml", ".yml", ".gradle", ".scala", ".kt", ".js", ".jsx", ".ts",
    ".tsx", ".css", ".html", ".txt", ".md", ".json",
}
REQUIRED = (
    "Licensed to the Apache Software Foundation (ASF)",
    "The ASF licenses this file",
)

def strip_asf_header(text: str) -> tuple[str, bool]:
    """Remove only a leading ASF template, leaving other copyright notices intact."""
    pos = 1 if text.startswith("\ufeff") else 0
    if text.startswith("#!", pos):
        end = text.find("\n", pos)
        if end < 0:
            return text, False
        pos = end + 1
    if text.startswith("<?xml", pos):
        end = text.find("?>", pos)
        if end < 0:
            return text, False
        pos = end + 2
    at = pos
    while at < len(text) and text[at] in "\r\n \t":
        at += 1
    if text.startswith("/*", at):
        end = text.find("*/", at + 2)
        if end < 0 or end - at > 3000:
            return text, False
        end += 2
    elif text.startswith("<!--", at):
        end = text.find("-->", at + 4)
        if end < 0 or end - at > 3000:
            return text, False
        end += 3
    elif text.startswith("#", at):
        end = at
        found_end = False
        for line in text[at:].splitlines(keepends=True):
            if not line.lstrip().startswith("#"):
                break
            end += len(line)
            if "limitations under the License." in line:
                found_end = True
                break
        if not found_end or end - at > 3000:
            return text, False
        if text.startswith("#\r\n", end):
            end += 3
        elif text.startswith("#\n", end):
            end += 2
    else:
        return text, False
    header = text[at:end]
    if re.search(r"(?im)^\\s*(?:\\*|#)?\\s*copyright\\s+(?:\\(?c\\)?\\s*)?\\d{4}", header) or not all(token in header for token in REQUIRED):
        return text, False
    prefix = text[:pos]
    if prefix and not prefix.endswith(("\r", "\n")):
        prefix += "\r\n" if "\r\n" in text else "\n"
    return prefix + text[end:].lstrip("\r\n"), True

def main() -> int:
    repo = Path(__file__).resolve().parent.parent
    changed = collections.Counter()
    total = 0
    for file in sorted(repo.rglob("*")):
        if not file.is_file() or file.suffix not in SOURCE_EXTENSIONS:
            continue
        if any(part in (".git", "node_modules", "target", "dist", ".next") for part in file.parts):
            continue
        raw = file.read_bytes()
        try:
            content = raw.decode("utf-8")
        except UnicodeDecodeError:
            continue
        modified, removed = strip_asf_header(content)
        if not removed:
            continue
        file.write_bytes(modified.encode("utf-8"))
        changed[file.relative_to(repo).parts[0]] += 1
        total += 1

    # Ensure the formatter does not automatically reinstate the removed header.
    pom = repo / "pom.xml"
    pom_text = pom.read_text(encoding="utf-8")
    pom_text, count = re.subn(
        r"\n[ \t]*<licenseHeader>\s*<file>[^<]*</file>\s*</licenseHeader>",
        "", pom_text,
    )
    if count != 1:
        raise RuntimeError(f"Expected exactly one Spotless ASF header rule, found {count}")
    pom.write_text(pom_text, encoding="utf-8")
    header_template = repo / "style" / "license-header"
    if header_template.exists():
        header_template.unlink()

    # Keep upstream licensing and source-origin attributions at repository level.
    notice = repo / "NOTICE"
    note = notice.read_text(encoding="utf-8")
    note = note.replace(
        "The adapted files retain their Apache license headers and modification notices.",
        "The adapted files remain Apache-2.0 licensed; see this NOTICE and LICENSE for "
        "source attribution. The ASF boilerplate headers were removed as a formatting-only change.",
    )
    note = note.replace(
        "source files retain their Apache License 2.0 headers. See",
        "source files remain Apache-2.0 licensed, with attribution retained here. See",
    )
    note += (
        "\nApache DolphinScheduler 3.4.3\n"
        "This product contains source derived from Apache DolphinScheduler 3.4.3,\n"
        "Copyright The Apache Software Foundation and original contributors.\n"
        "Removal of ASF boilerplate source headers is a formatting-only change;\n"
        "the original licensing and attribution obligations remain in effect.\n"
    )
    notice.write_text(note, encoding="utf-8")

    print(f"ASF boilerplate removed from {total} files.")
    for module, count in changed.most_common():
        print(f"  {module}: {count}")
    if total < 1000:
        raise RuntimeError(f"Expected broad repository sweep, only {total} files changed")
    return 0

if __name__ == "__main__":
    sys.exit(main())
