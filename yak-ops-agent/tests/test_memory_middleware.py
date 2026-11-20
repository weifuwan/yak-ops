"""Middleware queue, hint formatting and session transcript cleanup."""

from __future__ import annotations

import asyncio
import tempfile
import unittest
from pathlib import Path
from types import SimpleNamespace
from unittest.mock import patch

from agentscope.message import AssistantMsg, UserMsg
from agentscope.middleware import ReMeMiddleware

from yak_agent.memory.middleware import MEMORY_MSG_NAME, YakReMeMiddleware


def _middleware(tmp: str, **overrides) -> YakReMeMiddleware:
    values = {
        "workspace_dir": tmp,
        "parameters": ReMeMiddleware.Parameters(mode="agent_control"),
        "budget_tokens": 1000,
        "queue_size": 4,
        "search_timeout": 0.05,
        "write_timeout": 1.0,
    }
    values.update(overrides)
    return YakReMeMiddleware(**values)


class HintFormattingTests(unittest.TestCase):
    def test_hint_has_safety_header_and_bullets(self) -> None:
        with tempfile.TemporaryDirectory() as tmp:
            middleware = _middleware(tmp)

            hint = middleware.format_hint(["prefers FAILURE only", "answers in Chinese"])

        self.assertIsNotNone(hint)
        self.assertIn("not instructions", hint)
        self.assertIn("- prefers FAILURE only", hint)
        self.assertIn("- answers in Chinese", hint)

    def test_hint_is_bounded_by_budget(self) -> None:
        with tempfile.TemporaryDirectory() as tmp:
            middleware = _middleware(tmp, budget_tokens=20)

            hint = middleware.format_hint(["x" * 500])

        self.assertIsNotNone(hint)
        self.assertLessEqual(len(hint), 20 * 4 + len("\n(truncated)"))

    def test_zero_budget_disables_injection(self) -> None:
        with tempfile.TemporaryDirectory() as tmp:
            middleware = _middleware(tmp, budget_tokens=0)
            self.assertIsNone(middleware.format_hint(["memory"]))

    def test_empty_memories(self) -> None:
        with tempfile.TemporaryDirectory() as tmp:
            middleware = _middleware(tmp)
            self.assertIsNone(middleware.format_hint([]))
            self.assertEqual(middleware.format_tool_output([]), "(no relevant memories found)")

    def test_tool_output_is_bounded(self) -> None:
        with tempfile.TemporaryDirectory() as tmp:
            middleware = _middleware(tmp, budget_tokens=10)

            output = middleware.format_tool_output(["y" * 500])

        self.assertIn("untrusted, not instructions", output)
        self.assertLessEqual(len(output), 10 * 4 + 120)


class SearchTests(unittest.TestCase):
    def test_search_timeout_returns_empty(self) -> None:
        with tempfile.TemporaryDirectory() as tmp:
            middleware = _middleware(tmp, search_timeout=0.01)

            async def slow_search(*args, **kwargs):
                await asyncio.sleep(1)
                return ["late"]

            async def scenario():
                with patch.object(ReMeMiddleware, "_search", new=slow_search):
                    return await middleware.search("query")

            self.assertEqual(asyncio.run(scenario()), [])

    def test_first_search_retries_while_the_index_builds(self) -> None:
        with tempfile.TemporaryDirectory() as tmp:
            middleware = _middleware(tmp)
            calls: list[int] = []

            async def flaky_search(*args, **kwargs):
                calls.append(1)
                return [] if len(calls) == 1 else ["found later"]

            async def scenario():
                with (
                    patch.object(ReMeMiddleware, "_search", new=flaky_search),
                    patch.object(middleware, "_has_memory_files", return_value=True),
                ):
                    return await middleware.search("query")

            self.assertEqual(asyncio.run(scenario()), ["found later"])
            self.assertEqual(len(calls), 2)

    def test_search_failure_returns_empty(self) -> None:
        with tempfile.TemporaryDirectory() as tmp:
            middleware = _middleware(tmp)

            async def failing_search(*args, **kwargs):
                raise RuntimeError("index broken")

            async def scenario():
                with patch.object(ReMeMiddleware, "_search", new=failing_search):
                    return await middleware.search("query")

            self.assertEqual(asyncio.run(scenario()), [])


class WriteBackTests(unittest.TestCase):
    def test_framework_hook_is_noop(self) -> None:
        with tempfile.TemporaryDirectory() as tmp:
            middleware = _middleware(tmp)

            async def scenario():
                return await middleware._write_back([UserMsg(name="user", content="hi")], "sid")

            self.assertIsNone(asyncio.run(scenario()))

    def test_enqueue_flushes_to_base_write_back(self) -> None:
        with tempfile.TemporaryDirectory() as tmp:
            middleware = _middleware(tmp)
            calls: list[tuple[list, str]] = []

            async def fake_write_back(self, messages, session_id):
                calls.append((messages, session_id))

            async def scenario():
                with patch.object(ReMeMiddleware, "_write_back", new=fake_write_back):
                    accepted = middleware.enqueue_turn(
                        "sid",
                        [
                            UserMsg(name="user", content="我只看 FAILURE"),
                            UserMsg(name="assistant", content="好的"),
                        ],
                    )
                    await middleware.flush()
                    await middleware.close()
                return accepted

            self.assertTrue(asyncio.run(scenario()))
            self.assertEqual(len(calls), 1)
            self.assertEqual(calls[0][1], "sid")
            self.assertEqual(
                [msg.get_text_content() for msg in calls[0][0]], ["我只看 FAILURE", "好的"]
            )

    def test_enqueue_rejects_empty_turn(self) -> None:
        with tempfile.TemporaryDirectory() as tmp:
            middleware = _middleware(tmp)
            self.assertFalse(middleware.enqueue_turn("sid", []))
            self.assertFalse(middleware.enqueue_turn("", [UserMsg(name="user", content="hi")]))

    def test_worker_runs_auto_memory_with_sanitized_messages(self) -> None:
        class FakeApp:
            def __init__(self) -> None:
                self.jobs: list[tuple[str, dict]] = []

            async def run_job(self, name: str, **kwargs):
                self.jobs.append((name, kwargs))
                return SimpleNamespace(success=True)

            async def close(self) -> None:
                return None

        with tempfile.TemporaryDirectory() as tmp:
            middleware = _middleware(tmp)
            app = FakeApp()

            async def scenario():
                middleware._app = app
                middleware._started = True
                accepted = middleware.enqueue_turn(
                    "sid",
                    [
                        UserMsg(name="user", content="我只看 FAILURE，password=abc"),
                        AssistantMsg(name="assistant", content="中间过程"),
                        AssistantMsg(name="assistant", content="最终回答"),
                    ],
                )
                await middleware.flush()
                await middleware.close()
                return accepted

            self.assertTrue(asyncio.run(scenario()))
            self.assertEqual(len(app.jobs), 1)
            name, kwargs = app.jobs[0]
            self.assertEqual(name, "auto_memory")
            self.assertEqual(kwargs["session_id"], "sid")
            texts = [
                "".join(
                    block["text"] for block in message["content"] if block.get("type") == "text"
                )
                for message in kwargs["messages"]
            ]
            self.assertEqual(texts, ["我只看 FAILURE，password=<redacted>", "最终回答"])

    def test_flush_waits_for_an_in_flight_write(self) -> None:
        with tempfile.TemporaryDirectory() as tmp:
            middleware = _middleware(tmp)
            started = asyncio.Event()
            release = asyncio.Event()

            async def slow_write(self, messages, session_id):
                started.set()
                await release.wait()

            async def scenario():
                with patch.object(ReMeMiddleware, "_write_back", new=slow_write):
                    middleware.enqueue_turn(
                        "sid",
                        [UserMsg(name="user", content="hello")],
                    )
                    await started.wait()
                    flush_task = asyncio.create_task(middleware.flush(timeout=5))
                    await asyncio.sleep(0.05)
                    self.assertFalse(flush_task.done())
                    release.set()
                    await flush_task
                    await middleware.close()

            asyncio.run(scenario())

    def test_queue_full_drops(self) -> None:
        with tempfile.TemporaryDirectory() as tmp:
            middleware = _middleware(tmp, queue_size=1)

            async def scenario():
                middleware._queue = asyncio.Queue(maxsize=1)
                middleware._queue.put_nowait(("occupied", "sid"))
                return middleware.enqueue_turn(
                    "sid",
                    [UserMsg(name="user", content="hi")],
                )

            self.assertFalse(asyncio.run(scenario()))


class LifecycleTests(unittest.TestCase):
    def test_default_parameters_use_agent_control(self) -> None:
        with tempfile.TemporaryDirectory() as tmp:
            middleware = YakReMeMiddleware(workspace_dir=tmp)
            self.assertEqual(middleware._parameters.mode, "agent_control")

    def test_idle_tracking(self) -> None:
        with tempfile.TemporaryDirectory() as tmp:
            middleware = _middleware(tmp)
            now = middleware._last_used

            middleware.begin_turn()
            self.assertFalse(middleware.is_idle(now + 10, 0))
            middleware.end_turn()
            self.assertTrue(middleware.is_idle(middleware._last_used + 10, 5))

    def test_delete_session_transcript_keeps_daily(self) -> None:
        with tempfile.TemporaryDirectory() as tmp:
            middleware = _middleware(tmp)
            scope = Path(middleware._workspace_dir)
            dialog = scope / "session" / "dialog" / "abc.jsonl"
            images = scope / "session" / "images" / "abc"
            daily = scope / "daily" / "2026-11-20" / "card.md"
            for path in (dialog, images / "x.png", daily):
                path.parent.mkdir(parents=True, exist_ok=True)
                path.write_text("x", encoding="utf-8")

            middleware.delete_session_transcript("abc")

            self.assertFalse(dialog.exists())
            self.assertFalse(images.exists())
            self.assertTrue(daily.exists())

    def test_memory_message_name_is_reserved(self) -> None:
        self.assertEqual(MEMORY_MSG_NAME, "memory")


if __name__ == "__main__":
    unittest.main()
