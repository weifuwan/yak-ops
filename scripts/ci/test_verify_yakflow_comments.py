#!/usr/bin/env python3
"""Regression tests for the comment scanner's Java-string and type-doc boundaries."""

import unittest

from verify_yakflow_comments import check_source, scan_comments


class YakFlowCommentChecksTest(unittest.TestCase):
    def test_ignores_chinese_literals_and_inline_urls(self):
        text = '''/** A documented type. */
public class Probe {
    String error = "错误";
    String url = "https://example.com/path";
    String text = """
        // 中文 text-block data
        /* comment-looking text */
        """;
    char quote = '/';
}'''
        self.assertEqual([], check_source(text, "Probe.java", True))

    def test_rejects_chinese_javadoc_and_line_comment(self):
        text = '''/** 中文类型 */
public class Probe {
    // 中文注释
    void run() {}
}'''
        self.assertEqual(2, len(check_source(text, "Probe.java", True)))

    def test_requires_type_javadoc(self):
        text = '''package test;
public final class Probe {
}'''
        self.assertIn("missing Javadoc", check_source(text, "Probe.java", True)[0][1])

    def test_accepts_type_annotations_between_javadoc_and_declaration(self):
        text = '''/** A functional interface. */
@FunctionalInterface
public interface Probe {
    void run();
}'''
        self.assertEqual([], check_source(text, "Probe.java", True))

    def test_tests_do_not_require_type_javadoc(self):
        self.assertEqual([], check_source("class Probe {}", "Probe.java", False))

    def test_ignores_chinese_inside_escaped_strings_and_chars(self):
        text = r'''/** An example. */
public class Probe {
    String escaped = "\\\"中文//text\\\"";
    char slash = '/';
}'''
        self.assertEqual([], check_source(text, "Probe.java", True))

    def test_detects_unterminated_block_comment(self):
        with self.assertRaisesRegex(ValueError, "Unterminated"):
            scan_comments("class Probe { /* never closed")


if __name__ == "__main__":
    unittest.main()
