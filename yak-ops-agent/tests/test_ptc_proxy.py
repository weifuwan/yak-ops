"""Regression tests for the PTC host-side proxy allowlist (review P1 #2)."""

from __future__ import annotations

import unittest

from yak_agent.ptc import runner


class PtcProxyPathTests(unittest.TestCase):
    def test_readonly_get_routes_are_allowed(self) -> None:
        allowed = (
            ("GET", "/projects/1/data-sync/tasks"),
            ("GET", "/projects/1/data-sync/tasks/"),
            ("HEAD", "/projects/1/data-sync/tasks/abc"),
            ("GET", "/projects/1/data-sync/overview"),
            ("GET", "/projects/1/data-sync/instances"),
            ("GET", "/projects/1/data-sync/instances/9"),
            ("GET", "/log/detail"),
            ("GET", "/log/download-log"),
        )
        for method, path in allowed:
            with self.subTest(method=method, path=path):
                self.assertTrue(runner._path_allowed(method, path))

    def test_preview_post_is_allowed(self) -> None:
        self.assertTrue(runner._path_allowed("POST", "/projects/1/data-sync/tasks/preview"))

    def test_configured_context_path_is_stripped_before_matching(self) -> None:
        self.assertEqual(
            runner._strip_base_path("/yak-ops/log/download-log", "/yak-ops"),
            "/log/download-log",
        )
        self.assertTrue(
            runner._path_allowed(
                "GET",
                runner._strip_base_path("/yak-ops/log/download-log", "/yak-ops"),
            )
        )
        self.assertEqual(
            runner._strip_base_path("/other/log/download-log", "/yak-ops"),
            "/other/log/download-log",
        )
        self.assertFalse(
            runner._path_allowed(
                "GET",
                runner._strip_base_path("/other/log/download-log", "/yak-ops"),
            )
        )

    def test_write_methods_and_paths_are_blocked(self) -> None:
        blocked = (
            ("POST", "/projects/1/data-sync/tasks"),
            ("PUT", "/projects/1/data-sync/tasks/1"),
            ("DELETE", "/projects/1/data-sync/tasks/1"),
            ("POST", "/projects/1/data-sync/tasks/1/publish"),
            ("POST", "/projects/1/data-sync/tasks/1/unpublish"),
            ("POST", "/projects/1/task-instances/1/stop"),
            ("POST", "/projects/1/task-instances/1/force-success"),
            ("GET", "/projects/1/data-sync/tasks/1/publish"),
            ("GET", "/projects/1/data-sync/tasks/1/extra"),
            ("PATCH", "/projects/1/data-sync/tasks/1"),
        )
        for method, path in blocked:
            with self.subTest(method=method, path=path):
                self.assertFalse(runner._path_allowed(method, path))

    def test_encoded_or_relative_traversal_is_blocked(self) -> None:
        blocked = (
            "/projects/1/../secret",
            "/projects/1/data-sync/tasks/%2e%2e%2f..%2fdelete",
            "/projects/1/data-sync/tasks/%2F..%2Fdelete",
        )
        for path in blocked:
            with self.subTest(path=path):
                self.assertFalse(runner._path_allowed("GET", path))


if __name__ == "__main__":
    unittest.main()
