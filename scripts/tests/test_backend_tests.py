import json
import os
from pathlib import Path
import tempfile
import unittest
from unittest.mock import patch

import backend_tests as subject


class SuiteSelectionTest(unittest.TestCase):
    def test_docs_and_frontend_do_not_run_databases(self):
        self.assertEqual([], subject.select(["README.md", "yak-ops-ui/src/App.tsx"]))

    def test_shared_infrastructure_and_unknown_changes_run_all(self):
        for path in ("pom.xml", "yak-ops-test-utils/src/main/java/X.java", "unknown/backend.java", "yak-ops-dao/Mapper.java"):
            self.assertEqual(list(subject.SUITES), subject.select([path]))

    def test_common_jdbc_includes_oracle(self):
        selected = subject.select([subject.JDBC + "/src/main/java/reader/JdbcReader.java"])
        self.assertIn("jdbc-oracle", selected)
        self.assertIn("pipeline", selected)
        self.assertIn("cdc-pipeline", selected)

    def test_mysql_change_does_not_select_postgres_leaf_tests(self):
        selected = subject.select([subject.JDBC + "/src/main/java/dialect/MySqlDialect.java"])
        self.assertIn("jdbc-mysql", selected)
        self.assertNotIn("jdbc-postgres", selected)

    def test_rename_includes_old_and_new_owner(self):
        selected = subject.select([subject.JDBC + "/src/main/java/MySqlDialect.java", subject.CDC + "/src/main/java/X.java"])
        self.assertIn("jdbc-mysql", selected)
        self.assertIn("cdc", selected)
        self.assertIn("recovery-e2e", selected)

    def test_deleted_test_keeps_owner_selected(self):
        self.assertIn("pipeline", subject.select([subject.JDBC + "/src/test/java/integration/MySqlToMySqlIT.java"]))

    def test_non_pr_auto_is_full(self):
        with patch.dict(os.environ, {"CI_EVENT_NAME": "workflow_dispatch"}):
            self.assertEqual(list(subject.SUITES), subject.selected_suites("auto"))

    def test_incomplete_pr_identity_fails(self):
        with patch.dict(os.environ, {"CI_EVENT_NAME": "pull_request"}, clear=True):
            self.assertRaises(ValueError, subject.selected_suites, "auto")

    def test_unselected_is_not_pass(self):
        self.assertEqual("NOT SELECTED", subject.require_results([], "success", "skipped"))
        for result in ("skipped", "failure", "cancelled", ""):
            self.assertRaises(ValueError, subject.require_results, ["cdc"], "success", result)
        self.assertRaises(ValueError, subject.require_results, [], "failure", "skipped")


class ReportTest(unittest.TestCase):
    CDC_COUNTS = {"MySqlBinlogIT": 1, "MySqlHybridIT": 3, "MySqlBackfillIT": 2}

    def setUp(self):
        self.temp = tempfile.TemporaryDirectory()
        self.addCleanup(self.temp.cleanup)
        self.root = Path(self.temp.name)
        self.reports = self.root / subject.CDC / "target/failsafe-reports"
        self.reports.mkdir(parents=True)

    def write(self, name, body='<testcase name="test" classname="sample.NAME"/>', attributes=''):
        (self.reports / ("TEST-" + name + ".xml")).write_text(
            '<testsuite name="sample.' + name + '" ' + attributes + '>' + body.replace("NAME", name) + '</testsuite>')

    def complete_reports(self):
        for name, count in self.CDC_COUNTS.items():
            body = ''.join('<testcase name="test' + str(index) + '" classname="sample.NAME"/>'
                           for index in range(count))
            self.write(name, body)

    def test_complete_reports_pass(self):
        self.complete_reports()
        self.assertEqual(self.CDC_COUNTS, subject.SUITES["cdc"][2])
        self.assertEqual(self.CDC_COUNTS, subject.check_reports(["cdc"], self.root))

    def test_missing_zero_skipped_failed_or_partial_are_not_green(self):
        self.complete_reports()
        valid_case = '<testcase classname="sample.NAME"/>'
        for body in ('', valid_case, valid_case * 2,
                     valid_case * 2 + '<testcase classname="sample.NAME"><skipped/></testcase>',
                     valid_case * 2 + '<testcase classname="sample.NAME"><failure/></testcase>',
                     valid_case * 2 + '<testcase classname="sample.NAME"><error/></testcase>'):
            with self.subTest(body=body):
                self.write("MySqlHybridIT", body)
                with self.assertRaisesRegex(ValueError, "MySqlHybridIT"):
                    subject.check_reports(["cdc"], self.root)
        (self.reports / "TEST-MySqlHybridIT.xml").unlink()
        with self.assertRaisesRegex(ValueError, "MySqlHybridIT"):
            subject.check_reports(["cdc"], self.root)

    def test_backfill_reports_are_required_in_addition_to_hybrid_reports(self):
        self.complete_reports()
        self.write("MySqlBackfillIT")
        with self.assertRaisesRegex(ValueError, "MySqlBackfillIT"):
            subject.check_reports(["cdc"], self.root)
        (self.reports / "TEST-MySqlBackfillIT.xml").unlink()
        with self.assertRaisesRegex(ValueError, "MySqlBackfillIT"):
            subject.check_reports(["cdc"], self.root)

    def test_xml_summary_errors_block_even_without_testcase(self):
        self.complete_reports()
        self.write("MySqlHybridIT", body='', attributes='errors="1"')
        with self.assertRaisesRegex(ValueError, "Failed or skipped selected integration test"):
            subject.check_reports(["cdc"], self.root)

    def test_new_unregistered_it_fails(self):
        with patch.object(subject, "SUITES", {"small": ("module", "tag", {"NamedIT": 1})}):
            tests = self.root / "module/src/test/java"
            tests.mkdir(parents=True)
            (tests / "NamedIT.java").write_text("class NamedIT {}")
            subject.check_registration(self.root)
            (tests / "ForgottenIT.java").write_text("class ForgottenIT {}")
            self.assertRaises(ValueError, subject.check_registration, self.root)


if __name__ == "__main__":
    unittest.main()
