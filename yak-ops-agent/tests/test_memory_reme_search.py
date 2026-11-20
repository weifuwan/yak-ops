"""Real ReMe search wiring and workspace isolation (no LLM required)."""

from __future__ import annotations

import asyncio
import importlib.util
import tempfile
import time
import unittest
from pathlib import Path

from agentscope.middleware import ReMeMiddleware

from yak_agent.memory.middleware import YakReMeMiddleware

_REME_AVAILABLE = importlib.util.find_spec("reme") is not None


def _seed(root: Path, text: str) -> None:
    card = root / "daily" / "2026-11-20" / "card.md"
    card.parent.mkdir(parents=True, exist_ok=True)
    card.write_text(f"---\nname: card\ndescription: test\n---\n{text}", encoding="utf-8")


def _middleware(root: Path) -> YakReMeMiddleware:
    return YakReMeMiddleware(
        workspace_dir=str(root),
        parameters=ReMeMiddleware.Parameters(mode="agent_control"),
        budget_tokens=1500,
        search_timeout=10.0,
    )


@unittest.skipUnless(_REME_AVAILABLE, "reme-ai is not installed")
class ReMeSearchTests(unittest.TestCase):
    def test_chinese_query_returns_the_local_card(self) -> None:
        with tempfile.TemporaryDirectory() as tmp:
            root = Path(tmp)
            _seed(root, "用户偏好：只看 FAILURE 状态的任务实例，回答用中文。")
            middleware = _middleware(root)

            async def scenario():
                try:
                    for _ in range(10):
                        memories = await middleware.search("FAILURE 实例")
                        if memories:
                            return memories
                        await asyncio.sleep(0.5)
                    return []
                finally:
                    await middleware.close()

            memories = asyncio.run(scenario())

        self.assertTrue(any("FAILURE" in memory for memory in memories))
        hint = middleware.format_hint(memories)
        self.assertIsNotNone(hint)
        self.assertIn("not instructions", hint)

    def test_two_workspaces_do_not_see_each_other(self) -> None:
        with tempfile.TemporaryDirectory() as a_tmp, tempfile.TemporaryDirectory() as b_tmp:
            a_root = Path(a_tmp)
            b_root = Path(b_tmp)
            _seed(a_root, "工作空间 A 的偏好：只看 FAILURE。")
            _seed(b_root, "工作空间 B 的偏好：只看 SUCCESS。")
            a_middleware = _middleware(a_root)
            b_middleware = _middleware(b_root)

            async def scenario():
                try:
                    a_memories: list[str] = []
                    b_memories: list[str] = []
                    for _ in range(10):
                        a_memories = await a_middleware.search("工作空间 偏好")
                        b_memories = await b_middleware.search("工作空间 偏好")
                        if a_memories and b_memories:
                            break
                        await asyncio.sleep(0.5)
                    return a_memories, b_memories
                finally:
                    await a_middleware.close()
                    await b_middleware.close()

            started = time.monotonic()
            a_memories, b_memories = asyncio.run(scenario())

        self.assertTrue(any("A 的偏好" in memory for memory in a_memories))
        self.assertTrue(any("B 的偏好" in memory for memory in b_memories))
        self.assertFalse(any("B 的偏好" in memory for memory in a_memories))
        self.assertFalse(any("A 的偏好" in memory for memory in b_memories))
        self.assertLess(time.monotonic() - started, 30)


if __name__ == "__main__":
    unittest.main()
