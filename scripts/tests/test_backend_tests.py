import json
import os
from pathlib import Path
import tempfile
import unittest
import xml.etree.ElementTree as ET
from unittest.mock import patch

import backend_tests as subject


class SuiteSelectionTest(unittest.TestCase):
    def test_docs_and_frontend_do_not_run_databases(self):
        self.assertEqual([], subject.select(["README.md", "yak-ops-ui/src/App.tsx"]))

    def test_unknown_build_changes_fail_closed_but_shared_modules_do_not(self):
        for path in ("pom.xml", "unknown/backend.java", "scripts/tests/run.sh"):
            self.assertEqual(list(subject.SUITES), subject.select([path]))
        for path in ("yak-ops-common/src/main/java/X.java", "yak-ops-spi/src/main/java/X.java"):
            self.assertEqual([], subject.select([path]))
        self.assertEqual(["business", "backend-e2e"], subject.select(["yak-ops-dao/src/main/java/Mapper.java"]))

    def test_unit_test_change_does_not_run_real_database(self):
        self.assertEqual([], subject.select([subject.JDBC + "/src/test/java/JdbcBatchExecutionTest.java"]))
        self.assertEqual([], subject.select([subject.CDC + "/src/test/java/SnapshotChunkBackfillBudgetTest.java"]))

    def test_connector_local_cdc_it_runs_real_mysql_suite(self):
        selected = subject.select([subject.CDC + "/src/test/java/MySqlSnapshotBackfillIT.java"])
        self.assertEqual(["cdc"], selected)

    def test_cdc_production_change_retains_sink_and_recovery_acceptance(self):
        selected = subject.select([subject.CDC + "/src/main/java/reader/Reader.java"])
        self.assertIn("cdc", selected)
        self.assertIn("backend-e2e", selected)
        self.assertIn("recovery-e2e", selected)

    def test_common_jdbc_includes_oracle(self):
        selected = subject.select([subject.JDBC + "/src/main/java/reader/JdbcReader.java"])
        self.assertIn("jdbc-oracle", selected)
        self.assertIn("pipeline", selected)
        self.assertIn("backend-e2e", selected)
        self.assertIn("recovery-e2e", selected)

    def test_mysql_change_does_not_select_postgres_leaf_tests(self):
        selected = subject.select([subject.JDBC + "/src/main/java/dialect/MySqlDialect.java"])
        self.assertIn("jdbc-mysql", selected)
        self.assertNotIn("jdbc-postgres", selected)

    def test_rename_includes_old_and_new_owner(self):
        selected = subject.select([subject.JDBC + "/src/main/java/MySqlDialect.java", subject.CDC + "/src/main/java/X.java"])
        self.assertIn("jdbc-mysql", selected)
        self.assertIn("backend-e2e", selected)
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
            self.assertRaises(ValueError, subject.require_results, ["backend-e2e"], "success", result)
        self.assertRaises(ValueError, subject.require_results, [], "failure", "skipped")


class NightlyAcceptanceTest(unittest.TestCase):
    def test_nightly_calls_full_suite_not_fast_or_selected_only(self):
        workflow = (subject.ROOT / ".github/workflows/nightly-backend-acceptance.yml").read_text()
        self.assertIn("schedule:", workflow)
        self.assertIn("uses: ./.github/workflows/backend-tests.yml", workflow)
        self.assertIn("suite: full", workflow)
        self.assertNotIn("continue-on-error: true", workflow)

    def test_release_full_retains_end_to_end_acceptance(self):
        self.assertIn("backend-e2e", subject.SUITES)
        self.assertIn("recovery-e2e", subject.SUITES)
        self.assertIn("cdc", subject.SUITES)
        self.assertFalse(any(name.startswith("cdc-") for name in subject.SUITES))


class MavenIntegrationProfileTest(unittest.TestCase):
    def test_integration_profile_skips_surefire_but_not_failsafe(self):
        namespace = {"m": "http://maven.apache.org/POM/4.0.0"}
        pom = ET.parse(subject.ROOT / "pom.xml").getroot()
        profiles = pom.findall("./m:profiles/m:profile", namespace)
        integration = next(
            profile for profile in profiles
            if profile.findtext("m:id", namespaces=namespace) == "integration-tests"
        )
        plugins = integration.findall("./m:build/m:plugins/m:plugin", namespace)
        by_artifact = {
            plugin.findtext("m:artifactId", namespaces=namespace): plugin
            for plugin in plugins
        }
        surefire = by_artifact["maven-surefire-plugin"]
        failsafe = by_artifact["maven-failsafe-plugin"]
        self.assertEqual(
            "true", surefire.findtext("./m:configuration/m:skipTests", namespaces=namespace)
        )
        self.assertIsNone(failsafe.find("./m:configuration/m:skipTests", namespace))
        self.assertEqual(
            ["integration-test", "verify"],
            [goal.text for goal in failsafe.findall(
                "./m:executions/m:execution/m:goals/m:goal", namespace
            )]
        )


class ReportTest(unittest.TestCase):
    COUNTS = {"TaskCursorIT": 6, "ScheduleAdmissionIT": 13}

    def setUp(self):
        self.temp = tempfile.TemporaryDirectory()
        self.addCleanup(self.temp.cleanup)
        self.root = Path(self.temp.name)
        self.reports = self.root / subject.BUSINESS / "target/failsafe-reports"
        self.reports.mkdir(parents=True)

    def write(self, name, body='<testcase name="test" classname="sample.NAME"/>', attributes=''):
        (self.reports / ("TEST-" + name + ".xml")).write_text(
            '<testsuite name="sample.' + name + '" ' + attributes + '>' + body.replace("NAME", name) + '</testsuite>')

    def complete_reports(self):
        for name, count in self.COUNTS.items():
            body = ''.join('<testcase name="test' + str(index) + '" classname="sample.NAME"/>'
                           for index in range(count))
            self.write(name, body)

    def test_complete_reports_pass(self):
        self.complete_reports()
        self.assertEqual(self.COUNTS, subject.SUITES["business"][2])
        self.assertEqual(self.COUNTS, subject.check_reports(["business"], self.root))

    def test_missing_zero_skipped_failed_or_partial_are_not_green(self):
        self.complete_reports()
        valid_case = '<testcase classname="sample.NAME"/>'
        for body in ('', valid_case, valid_case * 2,
                     valid_case * 2 + '<testcase classname="sample.NAME"><skipped/></testcase>',
                     valid_case * 2 + '<testcase classname="sample.NAME"><failure/></testcase>',
                     valid_case * 2 + '<testcase classname="sample.NAME"><error/></testcase>'):
            with self.subTest(body=body):
                self.write("TaskCursorIT", body)
                with self.assertRaisesRegex(ValueError, "TaskCursorIT"):
                    subject.check_reports(["business"], self.root)
        (self.reports / "TEST-TaskCursorIT.xml").unlink()
        with self.assertRaisesRegex(ValueError, "TaskCursorIT"):
            subject.check_reports(["business"], self.root)

    def test_both_registered_reports_are_required(self):
        self.complete_reports()
        self.write("TaskCursorIT")
        with self.assertRaisesRegex(ValueError, "TaskCursorIT"):
            subject.check_reports(["business"], self.root)
        self.complete_reports()
        self.write("ScheduleAdmissionIT")
        with self.assertRaisesRegex(ValueError, "ScheduleAdmissionIT"):
            subject.check_reports(["business"], self.root)

    def test_xml_summary_errors_block_even_without_testcase(self):
        self.complete_reports()
        self.write("TaskCursorIT", body='', attributes='errors="1"')
        with self.assertRaisesRegex(ValueError, "Failed or skipped selected integration test"):
            subject.check_reports(["business"], self.root)

    def test_removed_cdc_suites_are_not_registered(self):
        self.assertFalse(any(suite.startswith("cdc-") for suite in subject.SUITES))
        self.assertIn("backend-e2e", subject.SUITES)
        self.assertIn("recovery-e2e", subject.SUITES)

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
