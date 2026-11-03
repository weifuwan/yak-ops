"""Regression checks for reproducible MySQL-only Flink CDC source imports."""
from __future__ import annotations

import importlib.util
from pathlib import Path
import unittest

ROOT = Path(__file__).resolve().parents[2]
SPEC = importlib.util.spec_from_file_location(
    "import_flink_cdc_3_7", ROOT / "scripts/import_flink_cdc_3_7.py"
)
IMPORTER = importlib.util.module_from_spec(SPEC)
SPEC.loader.exec_module(IMPORTER)


class ImportFlinkCdcTest(unittest.TestCase):
    def test_optional_model_modules_do_not_return_during_import(self):
        self.assertEqual(
            ("flink-cdc-pipeline-model-dummy",),
            IMPORTER.REACTOR["flink-cdc-pipeline-model/pom.xml"],
        )
        self.assertNotIn("flink-cdc-pipeline-model/", IMPORTER.SOURCE_PREFIXES)
        self.assertIn(
            "flink-cdc-pipeline-model/flink-cdc-pipeline-model-dummy/",
            IMPORTER.SOURCE_PREFIXES,
        )
        self.assertIn("flink-cdc-pipeline-model/pom.xml", IMPORTER.EXTRA_FILES)

    def test_retired_model_fixture_is_replaced_without_losing_array_hint(self):
        old_source = (
            "import org.apache.flink.cdc.runtime.model.OpenAIEmbeddingModel;\n"
            "    /** This is a Flink ScalarFunction. */\n"
            "    void test() {\n"
            '        String name = "GET_EMBEDDING";\n'
            "        assertThat(new UserDefinedFunctionDescriptor(name, "
            "OpenAIEmbeddingModel.class.getName()));\n"
            "    }\n"
        )
        result = IMPORTER.rewrite_runtime_descriptor_test(old_source)
        self.assertNotIn("OpenAIEmbeddingModel", result)
        self.assertIn("class ArrayTypeHintUdf implements UserDefinedFunction", result)
        self.assertIn("DataTypes.ARRAY(DataTypes.FLOAT())", result)
        self.assertIn("UserDefinedFunctionDescriptorTest$ArrayTypeHintUdf", result)

    def test_composer_factory_services_never_reference_excluded_tests(self):
        excluded = (
            "org.apache.flink.cdc.composer.testsource.recovery.RecoveryDataSinkFactory",
            "org.apache.flink.cdc.composer.flink.FlinkPipelineComposerTest$TestDataSinkFactory",
            "org.apache.flink.cdc.composer.flink.FlinkPipelineComposerTest$TestDataSourceFactory",
        )
        retained = "org.apache.flink.cdc.composer.utils.factory.DataSourceFactory1"
        source = "\n".join((retained, *excluded, ""))
        rewritten = IMPORTER.rewrite_composer_test_factories(source)
        self.assertEqual(retained + "\n", rewritten)
        for provider in excluded:
            self.assertNotIn(provider, rewritten)
        with self.assertRaisesRegex(ValueError, "Unexpected upstream composer"):
            IMPORTER.rewrite_composer_test_factories(retained + "\n")

    def test_upstream_fixture_shape_changes_fail_closed(self):
        with self.assertRaisesRegex(ValueError, "Unexpected upstream"):
            IMPORTER.rewrite_runtime_descriptor_test("class WrongTest {}")


if __name__ == "__main__":
    unittest.main()
