#!/usr/bin/env python3
"""One-shot, reproducible Flink CDC 3.7.0 source import (MySQL connectors only)."""
from __future__ import annotations

import re
import shutil
import sys
from pathlib import Path
import xml.etree.ElementTree as ET

SOURCE_PREFIXES = (
    "flink-cdc-common/",
    "flink-cdc-runtime/",
    "flink-cdc-composer/",
    "flink-cdc-cli/",
    "flink-cdc-flink1-compat/",
    "flink-cdc-pipeline-model/",
    "flink-cdc-pipeline-udf-examples/",
    "flink-cdc-connect/flink-cdc-source-connectors/flink-cdc-base/",
    "flink-cdc-connect/flink-cdc-source-connectors/flink-connector-debezium/",
    "flink-cdc-connect/flink-cdc-source-connectors/flink-connector-mysql-cdc/",
    "flink-cdc-connect/flink-cdc-source-connectors/flink-connector-test-util/",
    "flink-cdc-connect/flink-cdc-source-connectors/flink-sql-connector-mysql-cdc/",
    "flink-cdc-connect/flink-cdc-pipeline-connectors/flink-cdc-pipeline-connector-mysql/",
)

EXTRA_FILES = {
    "pom.xml",
    "LICENSE",
    "NOTICE",
    "flink-cdc-connect/pom.xml",
    "flink-cdc-connect/flink-cdc-source-connectors/pom.xml",
    "flink-cdc-connect/flink-cdc-pipeline-connectors/pom.xml",
}

NAMES = {
    "flink-cdc-parent": "yak-ops-cdc",
    "flink-cdc-common": "yak-ops-cdc-common",
    "flink-cdc-runtime": "yak-ops-cdc-runtime",
    "flink-cdc-composer": "yak-ops-cdc-composer",
    "flink-cdc-cli": "yak-ops-cdc-cli",
    "flink-cdc-flink1-compat": "yak-ops-cdc-flink1-compat",
    "flink-cdc-connect": "yak-ops-cdc-connect",
    "flink-cdc-source-connectors": "yak-ops-cdc-source-connectors",
    "flink-cdc-pipeline-connectors": "yak-ops-cdc-pipeline-connectors",
    "flink-cdc-base": "yak-ops-cdc-base",
    "flink-connector-debezium": "yak-ops-connector-debezium",
    "flink-connector-mysql-cdc": "yak-ops-connector-mysql-cdc",
    "flink-connector-test-util": "yak-ops-connector-test-util",
    "flink-sql-connector-mysql-cdc": "yak-ops-sql-connector-mysql-cdc",
    "flink-cdc-pipeline-connector-mysql": "yak-ops-cdc-pipeline-connector-mysql",
    "flink-cdc-pipeline-udf-examples": "yak-ops-cdc-pipeline-udf-examples",
    "flink-cdc-pipeline-model-parent": "yak-ops-cdc-pipeline-model-parent",
    "flink-cdc-pipeline-model": "yak-ops-cdc-pipeline-model",
    "flink-cdc-pipeline-model-legacy": "yak-ops-cdc-pipeline-model-legacy",
    "flink-cdc-pipeline-model-dummy": "yak-ops-cdc-pipeline-model-dummy",
    "flink-cdc-pipeline-model-openai-compatible": "yak-ops-cdc-pipeline-model-openai-compatible",
}

REACTOR = {
    "pom.xml": (
        "flink-cdc-flink1-compat", "flink-cdc-cli", "flink-cdc-common",
        "flink-cdc-composer", "flink-cdc-connect", "flink-cdc-runtime",
        "flink-cdc-pipeline-udf-examples", "flink-cdc-pipeline-model",
    ),
    "flink-cdc-connect/pom.xml": (
        "flink-cdc-source-connectors", "flink-cdc-pipeline-connectors",
    ),
    "flink-cdc-connect/flink-cdc-source-connectors/pom.xml": (
        "flink-cdc-base", "flink-connector-debezium",
        "flink-connector-mysql-cdc", "flink-connector-test-util",
        "flink-sql-connector-mysql-cdc",
    ),
    "flink-cdc-connect/flink-cdc-pipeline-connectors/pom.xml": (
        "flink-cdc-pipeline-connector-mysql",
    ),
    "flink-cdc-pipeline-model/pom.xml": (
        "flink-cdc-pipeline-model-legacy", "flink-cdc-pipeline-model-dummy",
        "flink-cdc-pipeline-model-openai-compatible",
    ),
}

REPLACED = set(NAMES.values())

def destination(relative: Path) -> Path:
    parts = list(relative.parts)
    for index, part in enumerate(parts):
        if part == "src":
            break
        parts[index] = NAMES.get(part, part)
    return Path(*parts)


def rewrite_pom(xml: str, relative: str) -> str:
    def rename_artifact(match: re.Match[str]) -> str:
        name = match.group(1)
        return f"<artifactId>{NAMES.get(name, name)}</artifactId>"

    def rename_module(match: re.Match[str]) -> str:
        name = match.group(1)
        return f"<module>{NAMES.get(name, name)}</module>"

    xml = re.sub(r"<artifactId>([^<]+)</artifactId>", rename_artifact, xml)
    xml = re.sub(r"<module>([^<]+)</module>", rename_module, xml)

    def update_local_coordinate(match: re.Match[str]) -> str:
        tag, inner = match.group(1), match.group(2)
        artifact = re.search(r"<artifactId>([^<]+)</artifactId>", inner)
        if not artifact:
            return match.group(0)
        artifact_id = artifact.group(1)
        if artifact_id in REPLACED:
            inner = inner.replace("<groupId>org.apache.flink</groupId>",
                                  "<groupId>io.yak.ops</groupId>", 1)
            if tag == "parent":
                inner = inner.replace("<version>3.7.0</version>",
                                      "<version>1.4.0</version>", 1)
        elif tag == "dependency" and artifact_id.startswith("flink-cdc-"):
            # Uncopied connector artifacts may be referenced by upstream tests.
            # They must never accidentally resolve against Yak Ops 1.4.0.
            inner = inner.replace("<version>${project.version}</version>",
                                  "<version>3.7.0</version>", 1)
        return f"<{tag}>{inner}</{tag}>"

    xml = re.sub(r"<(parent|dependency)>([\s\S]*?)</\1>",
                 update_local_coordinate, xml)

    if relative in REACTOR:
        if "<modules>" not in xml:
            raise ValueError(f"Missing Maven <modules> in {relative}")
        items = "\n".join(f"        <module>{NAMES[name]}</module>"
                          for name in REACTOR[relative])
        xml = re.sub(r"<modules>[\s\S]*?</modules>",
                     f"<modules>\n{items}\n    </modules>", xml, count=1)

    if relative == "pom.xml":
        old = ("<groupId>org.apache.flink</groupId>\n"
               "    <artifactId>yak-ops-cdc</artifactId>\n"
               "    <version>3.7.0</version>")
        new = ("<groupId>io.yak.ops</groupId>\n"
               "    <artifactId>yak-ops-cdc</artifactId>\n"
               "    <version>1.4.0</version>")
        if old not in xml:
            raise ValueError("Unexpected upstream root POM coordinates")
        xml = xml.replace(old, new, 1)
        xml = xml.replace("<revision>3.7.0</revision>",
                          "<revision>1.4.0</revision>", 1)
        xml = xml.replace("<scala.version>2.12.16</scala.version>",
                          "<scala.version>2.12.20</scala.version>", 1)
        xml = re.sub(r"<scm>[\s\S]*?</scm>",
                     "<scm><url>https://github.com/weifuwan/yak-ops</url></scm>",
                     xml, count=1)
    if relative == "pom.xml":
        # The original checkstyle/RAT/spotless bindings assume the upstream
        # repository's build root and are deliberately not inherited here.
        for plugin in ("maven-checkstyle-plugin", "spotless-maven-plugin", "apache-rat-plugin"):
            pattern = (
                r"\s*<plugin>\s*<groupId>[^<]+</groupId>\s*<artifactId>"
                + plugin + r"</artifactId>[\s\S]*?</plugin>"
            )
            xml, removed = re.subn(pattern, "", xml, count=1)
            if removed != 1:
                raise ValueError(f"Missing upstream plugin {plugin}")
    values_test_excludes = {"flink-cdc-composer/pom.xml":["RecoveryDataSink.java","FlinkPipelineAiFunctionITCase.java","TransformSpecsITCase.java","FlinkPipelineComposerTest.java","FlinkPipelineUdfITCase.java","FlinkPipelineComposerLenientITCase.java","FlinkPipelineComposerITCase.java","FlinkSchemaCoordinatorRecoveryITCase.java","FlinkPipelineBatchComposerITCase.java","FlinkParallelizedPipelineITCase.java","FlinkPipelineTransformITCase.java"],"flink-cdc-connect/flink-cdc-pipeline-connectors/flink-cdc-pipeline-connector-mysql/pom.xml":["MySqlParallelizedPipelineITCase.java","MySqlOscITCase.java","MySqlTablePatternMatchingTest.java"]}
    if relative in values_test_excludes:
        before = xml
        xml = re.sub(
            r"\s*<dependency>\s*<groupId>org\.apache\.flink</groupId>"
            r"\s*<artifactId>flink-cdc-pipeline-connector-values</artifactId>"
            r"(?s:.*?)</dependency>", "", xml, count=1)
        if xml == before:
            raise ValueError("Values test dependency missing in " + relative)
        exclusions = "\n".join(
            "                            <testExclude>**/" + name + "</testExclude>"
            for name in values_test_excludes[relative]
        )
        plugin = (
            "            <!-- Excluded upstream Values connector tests are preserved as sources. -->\n"
            "            <plugin>\n"
            "                <groupId>org.apache.maven.plugins</groupId>\n"
            "                <artifactId>maven-compiler-plugin</artifactId>\n"
            "                <configuration><testExcludes>\n"
            + exclusions +
            "\n                </testExcludes></configuration>\n"
            "            </plugin>\n"
        )
        if relative == "flink-cdc-composer/pom.xml":
            xml = xml.replace("    <profiles>", "    <build><plugins>\\n" + plugin + "    </plugins></build>\\n    <profiles>", 1)
        else:
            xml = re.sub(r"(<build>\\s*<plugins>)", r"\\1\\n" + plugin, xml, count=1)
    ET.fromstring(xml)
    return xml


def main() -> None:
    if len(sys.argv) != 3:
        raise SystemExit("usage: import_fl...py PATH_TO_UPSTREAM PATH_TO_YAK_OPS_CDC")
    upstream = Path(sys.argv[1]).resolve()
    target = Path(sys.argv[2]).resolve()
    if not (upstream / "flink-cdc-common/pom.xml").is_file():
        raise SystemExit(f"Not a Flink CDC source checkout: {upstream}")
    if target.name != "yak-ops-cdc":
        raise SystemExit(f"Refusing to remove unexpected path: {target}")
    if not (target.parent / "pom.xml").is_file():
        raise SystemExit(f"Yak Ops parent Maven POM not found: {target.parent}")
    if target.is_symlink():
        raise SystemExit("Refusing to remove a symlink")
    if target.exists():
        shutil.rmtree(target)

    count = 0
    for origin in sorted(upstream.rglob("*")):
        if not origin.is_file() or ".git" in origin.relative_to(upstream).parts:
            continue
        relative_path = origin.relative_to(upstream)
        relative = relative_path.as_posix()
        if relative not in EXTRA_FILES and not relative.startswith(SOURCE_PREFIXES):
            continue
        output = target / destination(relative_path)
        output.parent.mkdir(parents=True, exist_ok=True)
        if relative.endswith("/pom.xml") or relative == "pom.xml":
            output.write_text(rewrite_pom(origin.read_text(encoding="utf-8"), relative),
                              encoding="utf-8")
        else:
            shutil.copy2(origin, output)
        count += 1

    (target / "UPSTREAM.md").write_text(
        "# Flink CDC 3.7 source migration\n\n"
        "Upstream: https://github.com/apache/flink-cdc/tree/release-3.7.0\n\n"
        "Source and test code is copied from Apache Flink CDC 3.7.0 and keeps "
        "its original Java packages. Maven module names, local dependency "
        "coordinates and reactor module lists are adapted for Yak Ops.\n\n"
        "Only MySQL CDC connectors and their Debezium/Base dependencies are "
        "included. No other database connectors, Flink 2 compatibility, "
        "distribution, Python bindings or E2E harness are copied.\n\n"
        "Baseline: Flink 1.20.3, Debezium 1.9.8.Final. "
        "Yak Ops application/task integration intentionally deferred.\n\n"
        "Build from repository root: "
        "\`mvn -pl yak-ops-cdc -am -Dmaven.test.skip=true compile\`\n",
        encoding="utf-8"
    )

    ns = {"m": "http://maven.apache.org/POM/4.0.0"}
    pom_count = 0
    for pom in target.rglob("pom.xml"):
        root = ET.parse(pom).getroot()
        for child in root.findall("m:modules/m:module", ns):
            if not (pom.parent / child.text / "pom.xml").is_file():
                raise ValueError(f"Missing module {child.text} declared in {pom}")
        pom_count += 1

    source_main = target / (
        "yak-ops-cdc-composer/src/main/java/org/apache/flink/cdc/"
        "composer/flink/FlinkPipelineComposer.java"
    )
    if not source_main.is_file():
        raise ValueError("FlinkPipelineComposer was not copied")
    print(f"Imported {count} upstream files, {pom_count} Maven POMs, into {target}")


if __name__ == "__main__":
    main()
