import sys
from pathlib import Path
import unittest

sys.path.insert(0, str(Path(__file__).resolve().parent))
import impact


class ImpactTest(unittest.TestCase):
    def test_docs_and_ci_unit_tests_are_cheap(self):
        for filename in ("README.md", "docs/architecture.md", "scripts/ci/impact.py", "scripts/tests/test_backend_tests.py"):
            with self.subTest(filename=filename):
                check = impact.analyze([filename])
                self.assertFalse(any(check[key] for key in ("backend", "frontend", "distribution", "release")))
                self.assertEqual(set(), check["suites"])

    def test_fast_preflight_direct_modules(self):
        source = "yak-ops-cdc/yak-ops-cdc-connect/yak-ops-cdc-source-connectors/yak-ops-connector-mysql-cdc/src/main/java/Source.java"
        result = impact.analyze([source])
        self.assertTrue(result["preflight"])
        self.assertEqual({"yak-ops-cdc/yak-ops-cdc-connect/yak-ops-cdc-source-connectors/yak-ops-connector-mysql-cdc"}, result["preflight_modules"])
        self.assertIn("yak-ops-cdc/yak-ops-cdc-connect/yak-ops-cdc-source-connectors/yak-ops-connector-mysql-cdc", result["modules"])
        outputs = dict(line.split("=", 1) for line in impact.outputs([source]).splitlines())
        self.assertEqual("true", outputs["preflight"])

    def test_fast_preflight_skips_docs_frontend_and_test_only_changes(self):
        from unittest.mock import patch
        paths = (
            ("docs/engineering/note.md", False),
            ("yak-ops-ui/apps/web/app/App.tsx", False),
            ("yak-ops-cdc/yak-ops-cdc-connect/yak-ops-cdc-source-connectors/yak-ops-connector-mysql-cdc/src/test/java/SourceTest.java", False),
            ("scripts/tests/backend_tests.py", False),
            ("scripts/ci/test_impact.py", False),
            ("yak-ops-cdc/yak-ops-cdc-connect/yak-ops-cdc-source-connectors/yak-ops-connector-mysql-cdc/src/main/java/Source.java", True),
            ("yak-ops-cdc/yak-ops-cdc-connect/yak-ops-cdc-source-connectors/yak-ops-connector-mysql-cdc/pom.xml", True),
            ("yak-ops-bom/pom.xml", False),
        )
        with patch.object(impact, "suite_registration_changes", return_value={"cdc"}):
            for path, expected in paths:
                with self.subTest(path=path):
                    self.assertEqual(expected, impact.analyze([path])["preflight"])
            self.assertEqual({"cdc"}, impact.analyze(["scripts/tests/backend_tests.py"])["suites"])

    def test_fast_preflight_full_scope_for_build_and_workflow(self):
        for path in ("pom.xml", "tools/maven/checkstyle.xml",
                     ".github/workflows/quality-check.yml", ".editorconfig"):
            with self.subTest(path=path):
                result = impact.analyze([path])
                self.assertTrue(result["preflight"])
                self.assertTrue(result["full"])
                self.assertEqual(set(impact.SUITES), result["suites"])
                outputs = dict(line.split("=", 1) for line in impact.outputs([path]).splitlines())
                self.assertEqual("!yak-ops-bom,!yak-ops-ui,!yak-ops-dist", outputs["preflight_modules"])
        self.assertTrue(impact.analyze([], full=True)["preflight"])

    def test_fast_preflight_union_and_full_precedence(self):
        java = impact.RUNTIME + "src/main/java/StreamTask.java"
        frontend = "yak-ops-ui/apps/web/app/App.tsx"
        selected = impact.analyze([java, frontend])
        self.assertEqual({impact.RUNTIME.rstrip("/")}, selected["preflight_modules"])
        full = impact.analyze([java, "pom.xml"])
        self.assertEqual(set(), full["preflight_modules"])
        self.assertEqual(set(impact.SUITES), full["suites"])

    def test_backend_module_uses_reactor_path(self):
        check = impact.analyze(["yak-ops-common/src/main/java/io/yak/ops/common/enums/common/CommonErrorCode.java"])
        self.assertIn("yak-ops-common", check["modules"])
        self.assertIn("yak-ops-boot", check["modules"])
        self.assertEqual(set(), check["suites"])
        self.assertTrue(check["backend"])
        self.assertIn("yak-ops-boot", impact.outputs(["yak-ops-common/src/main/java/Example.java"]).split("modules=")[1].splitlines()[0])

    def test_business_task_only_relevant_suites(self):
        check = impact.analyze(["yak-ops-business/yak-ops-business-task/src/main/java/X.java"])
        self.assertIn("yak-ops-business/yak-ops-business-task", check["modules"])
        self.assertIn("yak-ops-boot", check["modules"])
        self.assertEqual({"business"}, check["suites"])

    def test_cdc_composer_changes_select_cdc(self):
        path = impact.ENGINE + "src/main/java/io/yak/ops/composer/yakflow/YakFlowPipelineComposer.java"
        result = impact.analyze([path])
        self.assertEqual({"cdc"}, result["suites"])
        self.assertIn(impact.ENGINE.rstrip("/"), result["modules"])
        self.assertEqual([], impact.select_suites([
            impact.ENGINE + "src/test/java/io/yak/ops/composer/PipelineComposerTest.java"]))

    def test_task_parameter_contract_selects_business_suite(self):
        path = "yak-ops-plugins/yak-ops-plugin-task/yak-ops-plugin-task-api/src/main/java/PipelineTaskChannel.java"
        self.assertEqual({"business"}, set(impact.select_suites([path])))

    def test_downstream_checks_follow_runtime_dependencies(self):
        selected = impact.affected_modules(["yak-ops-common"])
        self.assertIn("yak-ops-common", selected)
        self.assertIn("yak-ops-boot", selected)
        self.assertNotIn("yak-ops-dist", selected)
        self.assertNotIn("yak-ops-ui", selected)

    def test_removed_jdbc_reactor_paths_fail_closed(self):
        # The old yak-flow JDBC module is not in the current Maven reactor.
        # Unknown/deleted module paths must not be mistaken for narrow changes.
        for path in (
            "yak-flow/yak-flow-connector-jdbc/src/main/java/dialect/MySqlDialect.java",
            "yak-flow/yak-flow-connector-jdbc/src/main/java/internal/JdbcOutputFormat.java",
        ):
            with self.subTest(path=path):
                result = impact.analyze([path])
                self.assertTrue(result["full"])
                self.assertEqual(set(impact.SUITES), result["suites"])

    def test_shared_cdc_base_selects_cdc_checks(self):
        path = impact.CDC_BASE + "src/main/java/io/yak/ops/connector/cdc/base/source/assigner/SnapshotSplitAssigner.java"
        self.assertEqual({"cdc"}, set(impact.select_suites([path])))
        self.assertIn(impact.CDC_BASE.rstrip("/"), impact.analyze([path])["modules"])
        self.assertEqual([], impact.select_suites([
            impact.CDC_BASE + "src/test/java/SnapshotSplitAssignerTest.java"]))

    def test_connector_cdc_it_paths_select_owning_suite(self):
        path = impact.CDC + "src/test/java/org/apache/flink/cdc/connectors/mysql/source/TableExclusionDuringSnapshotIT.java"
        self.assertEqual({"cdc"}, set(impact.select_suites([path])))
        self.assertEqual([], impact.select_suites([impact.CDC + "src/test/java/source/reader/BinlogSplitReaderTest.java"]))

    def test_snapshot_and_unknown_cdc_changes_select_cdc(self):
        for path in (
            impact.CDC + "src/main/java/io/yak/ops/connector/cdc/mysql/source/assigners/MySqlChunkSplitter.java",
            impact.CDC + "src/main/java/source/UnexpectedChange.java",
            impact.CDC + "src/main/java/source/CheckpointOffset.java",
            impact.CDC_BASE + "src/main/java/source/assigner/SnapshotSplitAssigner.java",
        ):
            with self.subTest(path=path):
                self.assertEqual({"cdc"}, set(impact.select_suites([path])))

    def test_current_mysql_cdc_changes_keep_cdc_coverage(self):
        selected = impact.select_suites([
            impact.CDC + "src/main/java/io/yak/ops/connector/cdc/mysql/source/MySqlSource.java"
        ])
        self.assertEqual(["cdc"], selected)

    def test_unknown_cdc_and_shared_runtime_select_cdc(self):
        for path in (
            impact.CDC + "src/main/java/source/UnknownSource.java",
            impact.CDC_BASE + "src/main/java/source/assigner/SnapshotSplitAssigner.java",
            impact.RUNTIME + "src/main/java/execution/CheckpointCoordinator.java",
        ):
            with self.subTest(path=path):
                self.assertEqual({"cdc"}, set(impact.select_suites([path])))

    def test_test_only_changes_do_not_start_database(self):
        self.assertEqual([], impact.select_suites([
            impact.CDC + "src/test/java/reader/BinlogSplitReaderTest.java"
        ]))
        self.assertEqual([], impact.select_suites([
            "yak-ops-business/yak-ops-business-task/src/test/java/TaskCursorTest.java"
        ]))
        self.assertEqual(["cdc"], impact.select_suites([
            impact.CDC + "src/test/java/org/apache/flink/cdc/connectors/mysql/source/TableExclusionDuringSnapshotIT.java"
        ]))

    def test_rename_union_and_release(self):
        check = impact.analyze(["yak-ops-ui/apps/web/app/data-sync/index.tsx", "yak-ops-dao/src/main/resources/db/migration/V1__init.sql"])
        self.assertTrue(check["frontend"])
        self.assertTrue(check["backend"])
        self.assertTrue(check["release"])
        self.assertIn("business", check["suites"])

    def test_unknown_and_root_build_are_fail_closed(self):
        for path in (".editorconfig", "pom.xml", "unknown/Config.java"):
            with self.subTest(path=path):
                check = impact.analyze([path])
                self.assertTrue(all(check[key] for key in ("backend", "frontend", "distribution", "release")))
                self.assertEqual(set(impact.SUITES), check["suites"])

    def test_release_full_and_release_only(self):
        self.assertEqual(set(impact.SUITES), impact.analyze([], full=True)["suites"])
        self.assertFalse(impact.analyze(["scripts/release/check-release-metadata.sh"])["backend"])
        self.assertTrue(impact.analyze(["scripts/release/check-release-metadata.sh"])["release"])

    def test_nested_aggregator_module(self):
        modules = impact.module_for("yak-ops-business/pom.xml")
        self.assertIn("yak-ops-business/yak-ops-business-task", modules)
        self.assertNotIn("yak-ops-business", modules)


    def test_registry_change_only_selects_affected_suite(self):
        before = 'SUITES = {"business": (1, 2), "cdc": (1, 2)}\n\ndef runner():\n    return True\n'
        after = 'SUITES = {"business": (1, 2), "cdc": (1, 3)}\n\ndef runner():\n    return True\n'
        self.assertEqual({"cdc"}, impact.compare_suite_registry(before, after))
        self.assertEqual(set(), impact.compare_suite_registry(before, before + "# comment only\n"))

    def test_registry_logic_change_fails_closed(self):
        before = 'SUITES = {"cdc": 1}\ndef runner():\n    return 1\n'
        after = 'SUITES = {"cdc": 2}\ndef runner():\n    return 2\n'
        self.assertIsNone(impact.compare_suite_registry(before, after))
        self.assertIsNone(impact.compare_suite_registry(before, "SUITES = dict(cdc=1)"))
        self.assertIsNone(impact.compare_suite_registry(before, "SUITES = {'other': 1}"))

    def test_registry_selection_and_reason_are_consistent(self):
        from unittest.mock import patch
        with patch.object(impact, "suite_registration_changes", return_value={"cdc"}):
            selected = impact.select_suites(["scripts/tests/backend_tests.py"])
            self.assertEqual(["cdc"], selected)
            self.assertIn("suite registration only", impact.summary(["scripts/tests/backend_tests.py"]))
        with patch.object(impact, "suite_registration_changes", return_value=None):
            self.assertEqual(list(impact.SUITES), impact.select_suites(["scripts/tests/backend_tests.py"]))


if __name__ == "__main__":
    unittest.main()
