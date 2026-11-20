"""One embedded ReMe instance per (owner, workspace), with idle eviction."""

from __future__ import annotations

import asyncio
import logging
import shutil
import time
from collections import OrderedDict
from collections.abc import Callable
from dataclasses import dataclass
from pathlib import Path

from agentscope.credential import OpenAICredential
from agentscope.middleware import ReMeMiddleware
from agentscope.model import OpenAIChatModel

from yak_agent.config import Settings
from yak_agent.memory.middleware import YakReMeMiddleware
from yak_agent.memory.scope import scope_dir, validate_component

_LOGGER = logging.getLogger(__name__)
_MAX_OVERVIEW_FILES = 500
InUse = Callable[[str, str], bool]


@dataclass
class _Entry:
    middleware: YakReMeMiddleware
    last_used: float


def _build_middleware(settings: Settings, workspace: Path) -> YakReMeMiddleware:
    model = OpenAIChatModel(
        credential=OpenAICredential(api_key=settings.llm_api_key, base_url=settings.llm_base_url),
        model=settings.llm_model,
        context_size=settings.llm_context_size,
    )
    parameters = ReMeMiddleware.Parameters(
        chat_model=model,
        embedding_model=None,
        mode="agent_control",
        top_k=settings.memory_top_k,
    )
    return YakReMeMiddleware(
        workspace_dir=str(workspace),
        parameters=parameters,
        budget_tokens=settings.llm_memory_budget_tokens,
        queue_size=settings.memory_write_queue,
        search_timeout=settings.memory_search_timeout_seconds,
        write_timeout=settings.memory_write_timeout_seconds,
    )


_CLOSE_TASKS: set[asyncio.Task] = set()


def _schedule_close(middleware: YakReMeMiddleware) -> None:
    try:
        loop = asyncio.get_running_loop()
    except RuntimeError:
        return
    task = loop.create_task(middleware.close())
    _CLOSE_TASKS.add(task)
    task.add_done_callback(_CLOSE_TASKS.discard)


def _rmtree(path: Path) -> bool:
    """Remove a scope directory, retrying while Windows watchers release handles."""
    for attempt in range(3):
        if not path.exists():
            return True
        try:
            shutil.rmtree(path)
            return True
        except OSError:
            if attempt == 2:
                _LOGGER.warning("memory cleanup failed: %s", path)
                return False
            time.sleep(0.2)
    return False


class MemoryRegistry:
    """Lazily create and reuse one middleware per memory scope."""

    def __init__(self) -> None:
        self._entries: OrderedDict[tuple[str, str], _Entry] = OrderedDict()

    def get(
        self,
        owner: str,
        workspace_id: str,
        settings: Settings,
        in_use: InUse | None = None,
    ) -> YakReMeMiddleware | None:
        if not getattr(settings, "memory_enabled", False):
            return None
        key = (owner, workspace_id)
        entry = self._entries.get(key)
        if entry is not None:
            entry.last_used = time.monotonic()
            self._entries.move_to_end(key)
            self._evict(settings, in_use, exclude=key)
            return entry.middleware

        try:
            workspace = scope_dir(settings.memory_dir, owner, workspace_id)
            workspace.mkdir(parents=True, exist_ok=True)
        except (OSError, ValueError) as ex:
            _LOGGER.warning("memory disabled for %s/%s: %s", owner, workspace_id, ex)
            return None
        try:
            middleware = _build_middleware(settings, workspace)
        except Exception as ex:  # noqa: BLE001 - memory must never break a session
            _LOGGER.warning("memory init failed for %s/%s: %s", owner, workspace_id, ex)
            return None

        self._entries[key] = _Entry(middleware=middleware, last_used=time.monotonic())
        self._evict(settings, in_use, exclude=key)
        return middleware

    def _evict(
        self,
        settings: Settings,
        in_use: InUse | None,
        exclude: tuple[str, str],
    ) -> None:
        now = time.monotonic()
        while len(self._entries) > max(1, settings.memory_max_scopes):
            candidate = next(
                (
                    key
                    for key, entry in self._entries.items()
                    if key != exclude
                    and entry.middleware.is_idle(now, settings.memory_idle_seconds)
                    and not (in_use is not None and in_use(*key))
                ),
                None,
            )
            if candidate is None:
                return
            entry = self._entries.pop(candidate)
            _LOGGER.info("evicting idle memory scope %s/%s", *candidate)
            _schedule_close(entry.middleware)

    async def close_scope(self, owner: str, workspace_id: str) -> None:
        entry = self._entries.pop((owner, workspace_id), None)
        if entry is not None:
            await entry.middleware.close()

    async def clear_scope(
        self,
        owner: str,
        workspace_id: str,
        settings: Settings,
        in_use: InUse | None = None,
    ) -> int:
        try:
            path = scope_dir(settings.memory_dir, owner, workspace_id)
        except ValueError:
            return 0
        existed = path.exists()
        keep_handle = in_use is not None and in_use(owner, workspace_id)
        entry = self._entries.get((owner, workspace_id))
        if entry is not None:
            await entry.middleware.close()
            if not keep_handle:
                self._entries.pop((owner, workspace_id), None)
        removed = _rmtree(path)
        return 1 if existed and removed else 0

    async def clear_owner(
        self,
        owner: str,
        settings: Settings,
        in_use: InUse | None = None,
    ) -> int:
        try:
            owner_key = validate_component(owner, "owner")
        except ValueError:
            return 0
        keys = [key for key in self._entries if key[0] == owner]
        for key in keys:
            entry = self._entries[key]
            await entry.middleware.close()
            if in_use is None or not in_use(*key):
                self._entries.pop(key, None)
        owner_dir = Path(settings.memory_dir) / owner_key
        scopes = 0
        if owner_dir.exists():
            scopes = sum(
                1 for path in owner_dir.iterdir() if path.is_dir() and path.name.startswith("w-")
            )
        removed = _rmtree(owner_dir)
        return scopes if removed else 0

    async def close_all(self) -> None:
        for key in list(self._entries):
            entry = self._entries.pop(key)
            await entry.middleware.close()


MEMORY = MemoryRegistry()


def get_memory(
    owner: str,
    workspace_id: str,
    settings: Settings,
    in_use: InUse | None = None,
) -> YakReMeMiddleware | None:
    return MEMORY.get(owner, workspace_id, settings, in_use)


async def close_memory() -> None:
    await MEMORY.close_all()


def memory_overview(settings: Settings, owner: str, workspace_id: str) -> dict:
    """Summarize memory files for the current scope without returning content."""
    try:
        root = scope_dir(settings.memory_dir, owner, workspace_id)
    except ValueError:
        return {"files": [], "totalBytes": 0, "truncated": False}
    files: list[dict] = []
    total = 0
    truncated = False
    for subdir in ("daily", "digest"):
        base = root / subdir
        if not base.exists():
            continue
        for path in sorted(base.rglob("*.md")):
            if len(files) >= _MAX_OVERVIEW_FILES:
                truncated = True
                break
            try:
                size = path.stat().st_size
            except OSError:
                continue
            files.append({"path": path.relative_to(root).as_posix(), "bytes": size})
            total += size
        if truncated:
            break
    return {"files": files, "totalBytes": total, "truncated": truncated}
