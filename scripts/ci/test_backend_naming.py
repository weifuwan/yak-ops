#!/usr/bin/env python3
"""Regression cases for backend naming rules and legitimate network/domain vocabulary."""
import tempfile
import unittest
from pathlib import Path

from check_backend_naming import (
    CONFIG_RELOCATION_BASELINE_COMMIT,
    MOVED_CONFIG_PREFIX,
    ORIGINAL_CONFIG_PREFIX,
    RECORD_BASELINE_COMMIT,
    baseline_reference,
    new_record_violations,
    new_var_violations,
    verify_repository,
    verify_source,
)

CORE = "yak-ops-common/src/main/java/io/yak/ops/common/Foo.java"
CDC = "yak-ops-cdc/yak-ops-cdc-runtime/src/main/java/io/yak/ops/cdc/runtime/Foo.java"
NETWORK = "yak-ops-cdc/yak-ops-cdc-runtime/src/main/java/org/apache/flink/streaming/runtime/io/InputGate.java"


def source(package: str, body: str) -> str:
    return f"package {package};\n\n{body}\n"


class BackendNamingTest(unittest.TestCase):
    def check(self, path: str, package: str, body: str):
        return verify_source(path, source(package, body))

    def test_project_codec_types_are_rejected(self):
        for declaration in (
            "class ParameterCodec {}",
            "interface JsonCodec {}",
            "record RowCodec(int id) {}",
            "enum StateCodec { A }",
            "@interface FieldCodec {}",
        ):
            with self.subTest(declaration=declaration):
                self.assertTrue(self.check(CORE, "io.yak.ops.common", declaration))

    def test_codec_method_declaration_is_rejected(self):
        violations = self.check(
            CORE, "io.yak.ops.common",
            "class Foo {\n    private static String createCodec() { return null; }\n}",
        )
        self.assertEqual(1, len(violations))
        self.assertIn("Method 'createCodec'", violations[0][1])

    def test_cursor_methods_are_not_codecs(self):
        self.assertEqual([], self.check(
            CORE, "io.yak.ops.common",
            "class TaskLogService {\n"
            "    String decodeCursor(String cursor) { return cursor; }\n"
            "    String encodeCursor(String cursor) { return cursor; }\n"
            "}",
        ))

    def test_codec_package_is_rejected(self):
        errors = self.check(
            "yak-ops-common/src/main/java/io/yak/ops/common/codec/Parser.java",
            "io.yak.ops.common.codec",
            "class Parser {}",
        )
        self.assertEqual(1, len(errors))
        self.assertIn("Package segment 'codec'", errors[0][1])

    def test_ambiguous_business_and_cdc_gate_classes_are_rejected(self):
        for path, package, name in (
            (CORE, "io.yak.ops.common", "ScheduleAdmissionGate"),
            (CDC, "io.yak.ops.cdc.runtime", "MySqlSchemaChangeGate"),
        ):
            with self.subTest(name=name):
                self.assertTrue(self.check(path, package, f"class {name} {{}}"))

    def test_real_network_input_gate_is_allowed(self):
        self.assertEqual([], self.check(
            NETWORK, "org.apache.flink.streaming.runtime.io",
            "public final class InputGate {}",
        ))

    def test_network_gate_name_in_wrong_owner_is_rejected(self):
        self.assertTrue(self.check(
            CDC, "io.yak.ops.cdc.runtime",
            "public final class InputGate {}",
        ))

    def test_legitimate_normalization_and_domain_types_are_allowed(self):
        self.assertEqual([], self.check(
            CORE, "io.yak.ops.common",
            "class NameParser { String normalizeIdentifier(String name) { return name; } }\n"
            "class SchemaChangeHandler {}\n"
            "class ScheduleAdmissionController {}\n"
            "class ConnectionGateway {}",
        ))

    def test_comments_strings_and_method_calls_are_not_declarations(self):
        self.assertEqual([], self.check(
            CORE, "io.yak.ops.common",
            'class Foo {\n'
            '  // class BadCodec {} or class FakeGate {}\n'
            '  String message = "class BadCodec {}";\n'
            '  void update() { external.createCodec(); }\n'
            '}',
        ))

    def test_excludes_tests_and_third_party_packages(self):
        self.assertEqual([], verify_source(
            "yak-ops-common/src/test/java/io/yak/ops/common/BadCodec.java",
            source("io.yak.ops.common", "class BadCodec {}"),
        ))
        self.assertEqual([], self.check(
            CORE, "org.external.thirdparty",
            "class ExternalCodec {}",
        ))

    def test_configuration_relocation_uses_original_source_for_baselines(self):
        path = MOVED_CONFIG_PREFIX + "rule/ConditionRule.java"
        self.assertEqual(
            (CONFIG_RELOCATION_BASELINE_COMMIT, ORIGINAL_CONFIG_PREFIX + "rule/ConditionRule.java"),
            baseline_reference(path, RECORD_BASELINE_COMMIT),
        )
        self.assertEqual(
            (RECORD_BASELINE_COMMIT, CORE),
            baseline_reference(CORE, RECORD_BASELINE_COMMIT),
        )
        prior = source("io.yak.ops.configuration.rule", "public record ConditionRule(int id) {}")
        self.assertEqual([], new_record_violations(path, prior, prior))
        self.assertEqual(1, len(new_record_violations(
            path, prior + "\nrecord NewlyAdded(int value) {}", prior,
        )))

    def test_new_record_declarations_are_rejected_without_rewriting_existing_records(self):
        previous = source("io.yak.ops.common", "record Existing(int value) {}")
        current = source(
            "io.yak.ops.common",
            "record Existing(int value) {}\nrecord Added(String value) {}",
        )
        violations = new_record_violations(CORE, current, previous)
        self.assertEqual(1, len(violations))
        self.assertIn("Added", violations[0][1])

    def test_record_check_ignores_comments_strings_and_non_production_files(self):
        current = source(
            "io.yak.ops.common",
            '// record Fake(int id) {}\n'
            'class Value { String example = "record AlsoFake(int id) {}"; }',
        )
        self.assertEqual([], new_record_violations(CORE, current, ""))
        self.assertEqual([], new_record_violations(
            "yak-ops-common/src/test/java/io/yak/ops/common/Added.java",
            source("io.yak.ops.common", "record Added(int id) {}"),
            "",
        ))

    def test_retired_sync_runtime_packages_are_rejected(self):
        for package in ("io.yak.ops.sync.runtime", "io.yak.ops.sync.runtime.pipeline"):
            with self.subTest(package=package):
                self.assertTrue(self.check(
                    "yak-ops-task-execution/src/main/java/io/yak/ops/sync/runtime/Legacy.java", package,
                    "class Legacy {}"))
        self.assertEqual([], self.check(
            "yak-ops-task-execution/src/main/java/io/yak/ops/task/execution/PipelineComposer.java",
            "io.yak.ops.task.execution", "interface PipelineComposer {}"))

    def test_reintroduced_configuration_module_is_rejected(self):
        with tempfile.TemporaryDirectory() as directory:
            folder = Path(directory) / "yak-ops-configuration"
            folder.mkdir(parents=True)
            _, errors = verify_repository(Path(directory))
            self.assertTrue(any("retired configuration module" in error for error in errors))

    def test_obsolete_datasync_packages_are_rejected(self):
        with tempfile.TemporaryDirectory() as directory:
            folder = (Path(directory) / "yak-ops-common/src/main/java/io/yak/ops/common/bean/dto/datasync")
            folder.mkdir(parents=True)
            (folder / "ObsoleteTaskDTO.java").write_text(
                source("io.yak.ops.common.bean.dto.datasync", "class ObsoleteTaskDTO {}"),
                encoding="utf-8",
            )
            _, errors = verify_repository(Path(directory))
            self.assertTrue(any("Obsolete Data Sync contract package" in error for error in errors))

    def test_existing_var_is_grandfathered_but_added_var_is_rejected(self):
        previous = source("io.yak.ops.common", "class Example { void run() { var count = 1; } }")
        current = source("io.yak.ops.common", "class Example { void run() { var count = 1; var next = 2; } }")
        self.assertEqual([], new_var_violations(CORE, previous, previous))
        errors = new_var_violations(CORE, current, previous)
        self.assertEqual(1, len(errors))
        self.assertIn("New Java var", errors[0][1])
        self.assertEqual([], new_var_violations(
            "yak-ops-cdc/foo/src/main/java/org/apache/flink/Example.java",
            source("org.apache.flink", "class Example { void run() { var next = 2; } }"),
            "",
        ))

    def test_native_var_declarations_are_rejected(self):
        with tempfile.TemporaryDirectory() as root:
            folder = Path(root) / "yak-ops-common/src/main/java/io/yak/ops/common"
            folder.mkdir(parents=True)
            (folder / "UnsupportedVar.java").write_text(
                source("io.yak.ops.common", "class UnsupportedVar { void run() { var count = 1; } }"),
                encoding="utf-8",
            )
            _, errors = verify_repository(Path(root))
            self.assertTrue(any("Java var is prohibited" in error for error in errors))

    def test_repository_scan_reports_path_and_line(self):
        with tempfile.TemporaryDirectory() as root:
            folder = Path(root) / "yak-ops-common/src/main/java/io/yak/ops/common"
            folder.mkdir(parents=True)
            (folder / "BadCodec.java").write_text(
                source("io.yak.ops.common", "class BadCodec {}"), encoding="utf-8"
            )
            count, errors = verify_repository(Path(root))
            self.assertEqual(1, count)
            self.assertEqual(1, len(errors))
            self.assertIn("BadCodec.java:3:", errors[0])


if __name__ == "__main__":
    unittest.main()
