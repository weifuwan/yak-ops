#!/usr/bin/env python3
"""Preserve upstream DolphinScheduler source and adapt only Maven reactor metadata."""
import hashlib
import json
import re
import shutil
import subprocess
import sys
from pathlib import Path
import xml.etree.ElementTree as ET

SHA = "26228f9c5bff24b3ded7095484caf7a5ecffa050"
OLD_VERSION = "3.4.4-SNAPSHOT"
NEW_VERSION = "1.4.0"
SKIP_TOP = {"dolphinscheduler-ui","dolphinscheduler-dist","dolphinscheduler-e2e",
            "dolphinscheduler-api-test","dolphinscheduler-microbench","dolphinscheduler-yarn-aop"}
KEEP_TASK = {"dolphinscheduler-task-api","dolphinscheduler-task-all",
             "dolphinscheduler-task-shell","dolphinscheduler-task-sql",
             "dolphinscheduler-task-http","dolphinscheduler-task-java",
             "dolphinscheduler-task-python","dolphinscheduler-task-procedure",
             "dolphinscheduler-task-grpc","dolphinscheduler-task-remoteshell"}
SKIP_DS = {"dolphinscheduler-datasource-hive","dolphinscheduler-datasource-spark",
           "dolphinscheduler-datasource-k8s","dolphinscheduler-datasource-kyuubi",
           "dolphinscheduler-datasource-sagemaker","dolphinscheduler-datasource-aliyunserverlessspark",
           "dolphinscheduler-datasource-zeppelin","dolphinscheduler-datasource-athena",
           "dolphinscheduler-datasource-presto","dolphinscheduler-datasource-trino"}
SKIP_STORAGE = {"dolphinscheduler-storage-hdfs"}
SUPPORT = ["LICENSE","NOTICE","README.md","README_zh_CN.md",
           "lombok.config","style","config","licenses"]

def rename(n):
    return "yak-ops-" + n[len("dolphinscheduler-"):] if n.startswith("dolphinscheduler-") else n

def allow(parts):
    if parts[0] in SKIP_TOP:
        return False
    if len(parts) < 2 or not parts[1].startswith("dolphinscheduler-"):
        return True
    if parts[0] == "dolphinscheduler-task-plugin":
        return parts[1] in KEEP_TASK
    if parts[0] == "dolphinscheduler-datasource-plugin":
        return parts[1] not in SKIP_DS
    if parts[0] == "dolphinscheduler-storage-plugin":
        return parts[1] not in SKIP_STORAGE
    return True

def sha256(p):
    h = hashlib.sha256()
    with p.open("rb") as f:
        for chunk in iter(lambda: f.read(1048576), b""):
            h.update(chunk)
    return h.hexdigest()

def pom_rewrite(t):
    t = re.sub(r"(<groupId>\s*)org\.apache\.dolphinscheduler(\s*</groupId>)",
               r"\1io.yak.ops\2", t)
    t = re.sub(r"(<version>\s*)3\.4\.4-SNAPSHOT(\s*</version>)",
               r"\g<1>" + NEW_VERSION + r"\2", t)
    t = re.sub(r"(<artifactId>\s*)dolphinscheduler(\s*</artifactId>)",
               r"\1yak-ops-scheduler\2", t)
    return t.replace("dolphinscheduler-", "yak-ops-")

def artifact(p):
    ns = "{http://maven.apache.org/POM/4.0.0}"
    e = ET.parse(p).getroot().find(ns + "artifactId")
    if e is None or not e.text:
        raise ValueError("Missing Maven artifactId: " + str(p))
    return e.text.strip()

def adjust_pom(p, available=None):
    text = p.read_text(encoding="utf-8")
    removed_modules = []
    removed_deps = []
    def module_match(m):
        name = m.group(1).strip()
        if (p.parent / name).is_dir():
            return m.group(0)
        removed_modules.append(name)
        return ""
    text = re.sub(r"(?m)^[ \t]*<module>([^<]+)</module>[ \t]*\n?", module_match, text)
    if available is not None:
        def dep_match(m):
            block = m.group(0)
            g = re.search(r"<groupId>\s*([^<]+)\s*</groupId>", block)
            a = re.search(r"<artifactId>\s*([^<]+)\s*</artifactId>", block)
            if g and a and g.group(1).strip() == "io.yak.ops":
                name = a.group(1).strip()
                if name.startswith("yak-ops-") and name not in available:
                    removed_deps.append(name)
                    return ""
            return block
        text = re.sub(r"<dependency>\s*[\s\S]*?</dependency>", dep_match, text)
    p.write_text(text, encoding="utf-8")
    ET.parse(p)
    return removed_modules, sorted(set(removed_deps))

def main():
    repo = Path(__file__).resolve().parent.parent
    if len(sys.argv) != 2:
        sys.exit("Usage: migrate_ds343.py UPSTREAM_GIT_CHECKOUT")
    upstream = Path(sys.argv[1]).resolve()
    actual = subprocess.check_output(["git","-C",str(upstream),"rev-parse","HEAD"], text=True).strip()
    if actual != SHA:
        raise RuntimeError("Unexpected upstream commit " + actual)
    target = repo / "yak-ops-scheduler"
    if target.exists():
        raise RuntimeError("Refusing to overwrite " + str(target))
    target.mkdir()
    files, excluded, selected = [], set(), []
    for mod in sorted(upstream.glob("dolphinscheduler-*")):
        if not mod.is_dir():
            continue
        if mod.name in SKIP_TOP:
            excluded.add(mod.name)
            continue
        selected.append(mod.name)
        for p in sorted(mod.rglob("*")):
            rel = p.relative_to(upstream)
            if not allow(rel.parts):
                if len(rel.parts) == 2 and p.is_dir():
                    excluded.add(rel.as_posix())
                continue
            dest = target.joinpath(*(rename(n) for n in rel.parts))
            if p.is_symlink():
                raise RuntimeError("Unexpected upstream symlink " + str(p))
            if p.is_dir():
                dest.mkdir(parents=True, exist_ok=True)
            elif p.is_file():
                dest.parent.mkdir(parents=True, exist_ok=True)
                shutil.copy2(p, dest)
                record = {"source": rel.as_posix(),
                          "destination": dest.relative_to(repo).as_posix(),
                          "sha256": sha256(p), "maven_modified": p.name == "pom.xml"}
                files.append(record)
    upstream_pom = upstream / "pom.xml"
    (target / "UPSTREAM_ROOT_POM.xml").write_bytes(upstream_pom.read_bytes())
    for name in SUPPORT:
        src = upstream / name
        if src.is_file():
            shutil.copy2(src, target / name)
        elif src.is_dir():
            shutil.copytree(src, target / name)
    for info in files:
        dst = repo / info["destination"]
        if info["maven_modified"]:
            dst.write_text(pom_rewrite(dst.read_text(encoding="utf-8")), encoding="utf-8")
        elif sha256(dst) != info["sha256"]:
            raise RuntimeError("Non-POM source corruption: " + str(dst))
    (target / "pom.xml").write_text(pom_rewrite(upstream_pom.read_text(encoding="utf-8")), encoding="utf-8")
    poms = sorted(target.rglob("pom.xml"))
    removed_modules, removed_dependencies = {}, {}
    for p in poms:
        mods, _ = adjust_pom(p)
        if mods:
            removed_modules[p.relative_to(target).as_posix()] = mods
    available = {artifact(p) for p in poms}
    if len(available) != len(poms):
        raise RuntimeError("Duplicate imported Maven artifactIds")
    for p in poms:
        _, deps = adjust_pom(p, available)
        if deps:
            removed_dependencies[p.relative_to(target).as_posix()] = deps
    root_pom = repo / "pom.xml"
    old = root_pom.read_text(encoding="utf-8")
    needle = "        <module>yak-ops-ui</module>"
    if old.count(needle) != 1 or "<module>yak-ops-scheduler</module>" in old:
        raise RuntimeError("Unexpected root Maven reactor")
    root_pom.write_text(old.replace(needle, "        <module>yak-ops-scheduler</module>\n" + needle), encoding="utf-8")
    manifest = {
        "upstream_repo": "apache/dolphinscheduler",
        "upstream_ref": "3.4.3-release",
        "upstream_commit": SHA,
        "upstream_pom_version": OLD_VERSION,
        "yak_ops_maven_version": NEW_VERSION,
        "selected_roots": selected,
        "excluded_directories": sorted(excluded),
        "pom_count": len(poms),
        "imported_files": len(files),
        "excluded_maven_modules": removed_modules,
        "excluded_maven_dependencies": removed_dependencies,
        "files": files,
    }
    (target / "MIGRATION_MANIFEST.json").write_text(json.dumps(manifest,ensure_ascii=False,indent=2)+"\n", encoding="utf-8")
    (target / "UPSTREAM.md").write_text(
        "# Apache DolphinScheduler Source Migration\n\n"
        "Pinned upstream: apache/dolphinscheduler 3.4.3-release commit " + SHA + "\n\n"
        "Upstream Maven version: " + OLD_VERSION + "; Yak Ops Maven version: " + NEW_VERSION + ".\n\n"
        "Core Java sources, tests and resources are unchanged from upstream. Maven POM\n"
        "coordinates, module names and excluded optional plugin dependencies are adapted.\n"
        "For a SHA256 inventory and exclusion list, see MIGRATION_MANIFEST.json.\n"
        "Upstream parent POM retained as UPSTREAM_ROOT_POM.xml. Apache LICENSE, NOTICE\n"
        "and upstream source headers remain intact. Compilation and runtime require\n"
        "separate verification; source import does not establish runtime correctness.\n",
        encoding="utf-8")
    print("Imported",len(files),"source files,",len(poms),"Maven modules,",len(selected),"top-level modules.")
    print("Excluded",len(excluded),"module directories. See MIGRATION_MANIFEST.json.")

if __name__ == "__main__":
    main()
