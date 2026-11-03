import sys
from pathlib import Path
import unittest

sys.path.insert(0, str(Path(__file__).resolve().parent))
import impact
import ci_scope

class ImpactTest(unittest.TestCase):
    def test_frontend_only(self):
        result = impact.analyze(["yak-ops-ui/apps/web/main.tsx"])
        self.assertTrue(result["frontend_format"])
        self.assertEqual(set(), result["suites"])
        self.assertEqual(set(), result["modules"])
    def test_mysql_source_keeps_unit_and_database_coverage(self):
        result = impact.analyze([impact.CDC + "src/main/java/MySqlSource.java"])
        self.assertIn(impact.CDC.rstrip("/"), result["format_modules"])
        self.assertIn(impact.CDC.rstrip("/"), result["modules"])
        self.assertEqual({"cdc"}, result["suites"])
    def test_cdc_unit_only_does_not_run_database(self):
        result = impact.analyze([impact.CDC + "src/test/java/ReaderTest.java"])
        self.assertIn(impact.CDC.rstrip("/"), result["modules"])
        self.assertFalse(result["suites"])
    def test_business_changes(self):
        self.assertEqual(["business"], impact.select_suites([
            "yak-ops-business/yak-ops-business-task/src/main/java/Task.java"]))
    def test_shared_reactor_dependents(self):
        affected = impact.affected_modules(["yak-ops-common"])
        self.assertIn("yak-ops-boot", affected)
        self.assertNotIn("yak-ops-ui", affected)
    def test_nested_aggregator(self):
        self.assertIn("yak-ops-business/yak-ops-business-task",
                      impact.module_for("yak-ops-business/pom.xml"))
    def test_docs_skip_tests(self):
        result = impact.analyze(["README.md", "docs/design.md"])
        self.assertFalse(result["modules"])
        self.assertFalse(result["suites"])
    def test_build_and_runner_fail_closed(self):
        for path in ("pom.xml", "scripts/tests/backend_tests.py",
                     ".github/workflows/backend-tests.yml", "unknown/module.java"):
            with self.subTest(path=path):
                result = impact.analyze([path])
                self.assertTrue(result["full"])
                self.assertEqual(set(impact.SUITES), result["suites"])
    def test_main_push_full_unit_suite(self):
        fields = ci_scope.selection(["yak-ops-ui/apps/web/main.tsx"], mode="tests",
                                    event="push", branch="main")
        selected = fields["unit_modules"].split(",")
        self.assertIn("yak-ops-boot", selected)
        self.assertIn("yak-ops-business/yak-ops-business-task", selected)
        self.assertTrue(all(impact.is_ci_unit_module(module) for module in selected))
        self.assertEqual(
            sorted(module for module in impact.reactor_modules() if impact.is_ci_unit_module(module)),
            selected)
    def test_cdc_change_does_not_select_automatic_unit_tests(self):
        fields = ci_scope.selection([impact.CDC + "src/main/java/Reader.java"],
                                    mode="tests", event="pull_request", branch="")
        self.assertEqual("", fields["unit_modules"])
    def test_cdc_modules_cannot_enter_unit_test_dependency_closure(self):
        native = {module for module in impact.reactor_modules() if impact.is_ci_unit_module(module)}
        for module in impact.reactor_modules():
            if module.startswith(impact.CDC_ROOT):
                self.assertFalse(
                    native & impact.affected_modules([module]),
                    "Native backend depends on CDC module: " + module)

    def test_pr_format_scopes_java(self):
        fields = ci_scope.selection([impact.CDC + "src/main/java/Source.java"],
                                    mode="format", event="pull_request", branch="")
        self.assertEqual(impact.CDC.rstrip("/"), fields["backend_modules"])
        self.assertEqual("false", fields["frontend"])
    def test_manual_format_full(self):
        fields = ci_scope.selection(None, mode="format", event="workflow_dispatch", branch="main")
        self.assertEqual(impact.ALL_BACKEND, fields["backend_modules"])
        self.assertEqual("true", fields["frontend"])

if __name__ == "__main__":
    unittest.main()
