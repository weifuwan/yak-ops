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
        self.assertEqual(["business"], subject.select(["yak-ops-dao/src/main/java/Mapper.java"]))

    def test_unit_test_change_does_not_run_real_database(self):
        self.assertEqual([], subject.select([subject.CDC + "/src/test/java/SnapshotChunkBackfillBudgetTest.java"]))
        self.assertEqual([], subject.select([subject.BUSINESS + "/src/test/java/TaskCursorTest.java"]))

    def test_connector_local_cdc_it_runs_real_mysql_suite(self):
        selected = subject.select([subject.CDC + "/src/test/java/org/apache/flink/cdc/connectors/mysql/source/TableExclusionDuringSnapshotIT.java"])
        self.assertEqual(["cdc"], selected)

    def test_cdc_production_change_selects_cdc(self):
        selected = subject.select([subject.CDC + "/src/main/java/reader/Reader.java"])
        self.assertIn("cdc", selected)
        self.assertEqual(["cdc"], selected)

    def test_removed_jdbc_module_is_treated_as_unknown(self):
        # The old yak-flow JDBC path no longer belongs to this reactor.
        selected = subject.select(["yak-flow/yak-flow-connector-jdbc/src/main/java/reader/JdbcReader.java"])
        self.assertEqual(list(subject.SUITES), selected)

    def test_mysql_cdc_change_selects_current_cdc_coverage(self):
        selected = subject.select([subject.CDC + "/src/main/java/source/MySqlSource.java"])
        self.assertEqual(["cdc"], selected)

    def test_rename_includes_old_and_new_owner(self):
        selected = subject.select([
            subject.BUSINESS + "/src/main/java/TaskService.java",
            subject.CDC + "/src/main/java/source/MySqlSource.java",
        ])
        self.assertIn("business", selected)
        self.assertIn("cdc", selected)
        self.assertEqual(["business", "cdc"], selected)

    def test_deleted_test_keeps_owner_selected(self):
        self.assertIn("cdc", subject.select([subject.CDC + "/src/test/java/org/apache/flink/cdc/connectors/mysql/source/TableExclusionDuringSnapshotIT.java"]))

    def test_non_pr_auto_is_full(self):
        with patch.dict(os.environ, {"CI_EVENT_NAME": "workflow_dispatch"}):
            self.assertEqual(list(subject.SUITES), subject.selected_suites("auto"))

    def test_incomplete_pr_identity_fails(self):
        with patch.dict(os.environ, {"CI_EVENT_NAME": "pull_request"}, clear=True):
            self.assertRaises(ValueError, subject.selected_suites, "auto")

    def test_unselected_is_not_pass(self):
        self.assertEqual("NOT SELECTED", subject.require_results([], "success", "skipped"))
        for result in ("skipped", "failure", "cancelled", ""):
            self.assertRaises(ValueError, subject.require_results, ["business"], "success", result)
        self.assertRaises(ValueError, subject.require_results, [], "failure", "skipped")


class ConsolidatedBackendWorkflowTest(unittest.TestCase):
    def test_one_workflow_owns_backend_testing(self):
        workflow = (subject.ROOT / ".github/workflows/backend-tests.yml").read_text()
        self.assertIn("  push:", workflow)
        self.assertIn("  pull_request:", workflow)
        self.assertIn("  unit-tests:", workflow)
        self.assertIn("  integration-tests:", workflow)
        self.assertIn("    name: Backend Tests Check", workflow)
        self.assertNotIn("continue-on-error: true", workflow)

    def test_full_retains_database_acceptance(self):
        self.assertNotIn("backend-e2e", subject.SUITES)
        self.assertNotIn("recovery-e2e", subject.SUITES)
        self.assertEqual({"business", "cdc"}, set(subject.SUITES))


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
    COUNTS = {"TaskCursorIT": 6, "ScheduleAdmissionIT": 26, "DefinitionPageQueryIT": 5}

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

    def test_every_registered_report_is_required(self):
        for name in self.COUNTS:
            with self.subTest(name=name):
                self.complete_reports()
                self.write(name)
                with self.assertRaisesRegex(ValueError, name):
                    subject.check_reports(["business"], self.root)

    def test_xml_summary_errors_block_even_without_testcase(self):
        self.complete_reports()
        self.write("TaskCursorIT", body='', attributes='errors="1"')
        with self.assertRaisesRegex(ValueError, "Failed or skipped selected integration test"):
            subject.check_reports(["business"], self.root)

    def test_removed_cdc_suites_are_not_registered(self):
        self.assertFalse(any(suite.startswith("cdc-") for suite in subject.SUITES))
        self.assertNotIn("backend-e2e", subject.SUITES)
        self.assertNotIn("recovery-e2e", subject.SUITES)

    def test_registered_it_classes_exist_in_current_reactor(self):
        subject.check_registration()
        for name, (module, _tag, tests) in subject.SUITES.items():
            with self.subTest(suite=name):
                self.assertTrue((subject.ROOT / module / "pom.xml").is_file())
                for test_class in tests:
                    matches = list((subject.ROOT / module / "src/test").rglob(test_class + ".java"))
                    self.assertEqual(1, len(matches))
                    source = matches[0].read_text(encoding="utf-8")
                    self.assertIn('@Tag("' + name + '")', source)

    def test_missing_cdc_report_fails_closed(self):
        with self.assertRaisesRegex(ValueError, "TableExclusionDuringSnapshotIT"):
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
