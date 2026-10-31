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
        source = "yak-flow/yak-flow-connector-cdc-mysql/src/main/java/Source.java"
        result = impact.analyze([source])
        self.assertTrue(result["preflight"])
        self.assertEqual({"yak-flow/yak-flow-connector-cdc-mysql"}, result["preflight_modules"])
        self.assertIn("yak-ops-boot", result["modules"])
        outputs = dict(line.split("=", 1) for line in impact.outputs([source]).splitlines())
        self.assertEqual("true", outputs["preflight"])

    def test_fast_preflight_skips_docs_frontend_and_test_only_changes(self):
        from unittest.mock import patch
        paths = (
            ("docs/engineering/note.md", False),
            ("yak-ops-ui/apps/web/app/App.tsx", False),
            ("yak-flow/yak-flow-connector-cdc-mysql/src/test/java/SourceTest.java", False),
            ("scripts/tests/backend_tests.py", False),
            ("scripts/ci/test_impact.py", False),
            ("yak-flow/yak-flow-connector-cdc-mysql/src/main/java/Source.java", True),
            ("yak-flow/yak-flow-connector-cdc-mysql/pom.xml", True),
            ("yak-ops-bom/pom.xml", False),
        )
        with patch.object(impact, "suite_registration_changes", return_value={"backend-e2e"}):
            for path, expected in paths:
                with self.subTest(path=path):
                    self.assertEqual(expected, impact.analyze([path])["preflight"])
            self.assertEqual({"backend-e2e"}, impact.analyze(["scripts/tests/backend_tests.py"])["suites"])

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
        java = "yak-flow/yak-flow-runtime/src/main/java/StreamTask.java"
        frontend = "yak-ops-ui/apps/web/app/App.tsx"
        selected = impact.analyze([java, frontend])
        self.assertEqual({"yak-flow/yak-flow-runtime"}, selected["preflight_modules"])
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
        self.assertEqual({"business", "backend-e2e"}, check["suites"])

    def test_pipeline_composition_selects_database_and_recovery_suites(self):
        path = impact.ENGINE + "src/main/java/io/yak/ops/composer/yakflow/YakFlowPipelineComposer.java"
        result = impact.analyze([path])
        self.assertEqual({"pipeline", "cdc", "backend-e2e", "recovery-e2e"}, result["suites"])
        self.assertIn("yak-ops-engine", result["modules"])
        self.assertIn("yak-ops-boot", result["modules"])
        self.assertEqual([], impact.select_suites([
            impact.ENGINE + "src/test/java/io/yak/ops/composer/PipelineComposerTest.java"]))

    def test_task_parameter_contract_selects_recovery_acceptance(self):
        path = "yak-ops-plugins/yak-ops-plugin-task/yak-ops-plugin-task-data-sync/src/main/java/PipelineTaskChannel.java"
        self.assertEqual({"backend-e2e", "recovery-e2e"}, set(impact.select_suites([path])))

    def test_downstream_checks_follow_runtime_dependencies(self):
        selected = impact.affected_modules(["yak-flow/yak-flow-runtime"])
        self.assertIn("yak-flow/yak-flow-runtime", selected)
        self.assertIn("yak-ops-boot", selected)
        self.assertNotIn("yak-ops-dist", selected)
        self.assertNotIn("yak-ops-ui", selected)

    def test_vendor_specific_and_shared_jdbc(self):
        vendor = impact.select_suites(["yak-flow/yak-flow-connector-jdbc/src/main/java/dialect/MySqlDialect.java"])
        self.assertIn("jdbc-mysql", vendor)
        self.assertNotIn("jdbc-oracle", vendor)
        self.assertIn("backend-e2e", vendor)
        self.assertIn("recovery-e2e", vendor)
        common = impact.select_suites(["yak-flow/yak-flow-connector-jdbc/src/main/java/internal/JdbcOutputFormat.java"])
        self.assertEqual(
            ["jdbc-mysql", "jdbc-postgres", "jdbc-oracle", "pipeline", "backend-e2e", "recovery-e2e"],
            common,
        )

    def test_shared_cdc_base_selects_cdc_and_e2e_checks(self):
        path = "yak-flow/yak-flow-connector-cdc-base/src/main/java/io/yak/ops/connector/cdc/base/source/assigner/SnapshotSplitAssigner.java"
        self.assertEqual({"cdc", "backend-e2e", "recovery-e2e"}, set(impact.select_suites([path])))
        self.assertIn("yak-flow/yak-flow-connector-cdc-mysql", impact.analyze([path])["modules"])
        self.assertEqual([], impact.select_suites([
            "yak-flow/yak-flow-connector-cdc-base/src/test/java/SnapshotSplitAssignerTest.java"]))

    def test_connector_cdc_it_paths_select_owning_suite(self):
        path = impact.CDC + "src/test/java/integration/MySqlCdcToMySqlCrashRecoveryIT.java"
        self.assertEqual({"cdc"}, set(impact.select_suites([path])))
        self.assertEqual([], impact.select_suites([impact.CDC + "src/test/java/source/reader/BinlogSplitReaderTest.java"]))

    def test_snapshot_and_unknown_cdc_changes_preserve_e2e_coverage(self):
        for path in (
            impact.CDC + "src/main/java/io/yak/ops/connector/cdc/mysql/source/assigners/MySqlChunkSplitter.java",
            impact.CDC + "src/main/java/source/UnexpectedChange.java",
            impact.CDC + "src/main/java/source/CheckpointOffset.java",
            impact.CDC_BASE + "src/main/java/source/assigner/SnapshotSplitAssigner.java",
        ):
            with self.subTest(path=path):
                self.assertEqual({"cdc", "backend-e2e", "recovery-e2e"}, set(impact.select_suites([path])))

    def test_jdbc_vendor_changes_keep_end_to_end_acceptance(self):
        postgres = impact.select_suites([
            impact.JDBC + "src/main/java/io/yak/ops/connector/jdbc/database/dialect/PostgresDialect.java"
        ])
        self.assertIn("jdbc-postgres", postgres)
        self.assertIn("backend-e2e", postgres)
        self.assertNotIn("recovery-e2e", postgres)
        mysql = impact.select_suites([
            impact.JDBC + "src/main/java/io/yak/ops/connector/jdbc/database/dialect/MySqlDialect.java"
        ])
        self.assertIn("jdbc-mysql", mysql)
        self.assertIn("recovery-e2e", mysql)

    def test_unknown_cdc_and_shared_runtime_keep_recovery(self):
        for path in (
            impact.CDC + "src/main/java/source/UnknownSource.java",
            impact.CDC_BASE + "src/main/java/source/assigner/SnapshotSplitAssigner.java",
            impact.RUNTIME + "src/main/java/execution/CheckpointCoordinator.java",
        ):
            with self.subTest(path=path):
                self.assertTrue({"backend-e2e", "recovery-e2e"}.issubset(
                    set(impact.select_suites([path]))))

    def test_test_only_changes_do_not_start_database(self):
        self.assertEqual([], impact.select_suites(["yak-flow/yak-flow-connector-jdbc/src/test/java/JdbcBatchExecutionTest.java"]))
        self.assertEqual(["jdbc-mysql"], impact.select_suites(["yak-flow/yak-flow-connector-jdbc/src/test/java/MySqlSourceIT.java"]))
        self.assertEqual(["recovery-e2e"], impact.select_suites(["yak-ops-e2e/src/test/java/ProcessRecoveryIT.java"]))

    def test_rename_union_and_release(self):
        check = impact.analyze(["yak-ops-ui/apps/web/app/data-sync/index.tsx", "yak-ops-dao/src/main/resources/db/migration/V1__init.sql"])
        self.assertTrue(check["frontend"])
        self.assertTrue(check["backend"])
        self.assertTrue(check["release"])
        self.assertIn("backend-e2e", check["suites"])

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
        before = 'SUITES = {"jdbc-mysql": (1, 2), "backend-e2e": (1, 2)}\n\ndef runner():\n    return True\n'
        after = 'SUITES = {"jdbc-mysql": (1, 2), "backend-e2e": (1, 3)}\n\ndef runner():\n    return True\n'
        self.assertEqual({"backend-e2e"}, impact.compare_suite_registry(before, after))
        self.assertEqual(set(), impact.compare_suite_registry(before, before + "# comment only\n"))

    def test_registry_logic_change_fails_closed(self):
        before = 'SUITES = {"cdc": 1}\ndef runner():\n    return 1\n'
        after = 'SUITES = {"cdc": 2}\ndef runner():\n    return 2\n'
        self.assertIsNone(impact.compare_suite_registry(before, after))
        self.assertIsNone(impact.compare_suite_registry(before, "SUITES = dict(cdc=1)"))
        self.assertIsNone(impact.compare_suite_registry(before, "SUITES = {'other': 1}"))

    def test_registry_selection_and_reason_are_consistent(self):
        from unittest.mock import patch
        with patch.object(impact, "suite_registration_changes", return_value={"backend-e2e"}):
            selected = impact.select_suites(["scripts/tests/backend_tests.py"])
            self.assertEqual(["backend-e2e"], selected)
            self.assertIn("suite registration only", impact.summary(["scripts/tests/backend_tests.py"]))
        with patch.object(impact, "suite_registration_changes", return_value=None):
            self.assertEqual(list(impact.SUITES), impact.select_suites(["scripts/tests/backend_tests.py"]))


if __name__ == "__main__":
    unittest.main()
