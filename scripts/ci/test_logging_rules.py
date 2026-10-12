#!/usr/bin/env python3
"""Smoke tests for logging security, diagnostic language and TRACE support."""

import sys
import tempfile
import unittest
from pathlib import Path

SCRIPTS = Path(__file__).resolve().parents[1]
sys.path.insert(0, str(SCRIPTS))

from verify_logging_rules import ROOT, chinese_connector_exceptions, chinese_engine_literals, verify_file


class LoggingChecksTest(unittest.TestCase):
    def check(self, statement):
        with tempfile.TemporaryDirectory(dir=ROOT) as tempdir:
            source = Path(tempdir) / "Sample.java"
            source.write_text(
                "class Sample {\n"
                "    private static final Logger LOG = LoggerFactory.getLogger(Sample.class);\n"
                f"    void run() {{ {statement} }}\n"
                "}\n",
                encoding="utf-8",
            )
            return verify_file(source)

    def test_trace_and_english_message_are_allowed(self):
        self.assertEqual([], self.check('LOG.trace("Trace event");'))

    def test_sensitive_log_arguments_are_rejected(self):
        self.assertTrue(self.check('LOG.info("Value {}", password);'))

    def test_direct_console_output_is_rejected(self):
        self.assertTrue(self.check('System.out.println("unsafe");'))

    def test_chinese_log_template_is_rejected(self):
        self.assertTrue(self.check('LOG.warn("检查点超时, id={}", id);'))

    def test_product_message_is_not_classified_as_logging(self):
        self.assertEqual([], self.check('String userMessage = "任务启动失败";'))

    def test_engine_diagnostic_strings_use_english(self):
        code = 'throw new IllegalStateException("Checkpoint 等待超时");'
        self.assertEqual(1, len(chinese_engine_literals(code)))
        self.assertEqual([], chinese_engine_literals('throw new IllegalStateException("Checkpoint timed out");'))

    def test_java_comments_are_not_diagnostic_strings(self):
        source = '// "注释示例"\nthrow new IOException("Checkpoint timed out");'
        self.assertEqual([], chinese_engine_literals(source))

    def test_connector_exception_language_excludes_user_warnings(self):
        source = 'String warning = "目标使用 TEXT"; throw new IOException("CRC 校验失败");'
        diagnostics = chinese_connector_exceptions(source)
        self.assertEqual(1, len(diagnostics))
        self.assertIn("CRC", diagnostics[0][1])
        self.assertEqual([], chinese_connector_exceptions('String warning = "目标使用 TEXT";'))


if __name__ == "__main__":
    unittest.main()
