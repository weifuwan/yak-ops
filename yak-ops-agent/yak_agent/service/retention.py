"""Session retention: delete stale session files on disk.

meta is the authority — only sids with an owner/workspace meta are candidates,
so unrelated JSON files and orphan states are never touched. Sessions loaded
in this process (SESSIONS) are skipped entirely: their in-memory AgentState
would write the files straight back after deletion. Loaded sessions still
count toward the capacity total but are not deletable. User-level ReMe
memory (memory/) is outside sessions/ and is never deleted here.
"""

from __future__ import annotations

import asyncio
import logging
import time
from collections.abc import Iterator
from dataclasses import dataclass
from pathlib import Path
from typing import Any

from yak_agent import audit
from yak_agent.config import Settings
from yak_agent.service import persistence
from yak_agent.service.session import SESSIONS

_LOGGER = logging.getLogger(__name__)
_SECONDS_PER_DAY = 86400
_BYTES_PER_MB = 1024 * 1024


@dataclass(frozen=True)
class RetentionStats:
    scanned: int = 0
    deleted: int = 0
    skipped_loaded: int = 0
    bytes_freed: int = 0


def _session_files(sid: str) -> Iterator[Path]:
    yield persistence.state_path(sid)
    yield persistence.meta_path(sid)
    workspace = persistence.SESSION_DIR / sid
    if workspace.is_dir():
        for path in workspace.rglob("*"):
            if path.is_file():
                yield path


def _measure(sid: str) -> tuple[float, int]:
    """Newest mtime and total byte size of one session's files."""
    latest = 0.0
    total = 0
    for path in _session_files(sid):
        try:
            stat = path.stat()
        except OSError:
            continue
        latest = max(latest, stat.st_mtime)
        total += stat.st_size
    return latest, total


def cleanup_sessions(
    settings: Settings,
    *,
    now: float | None = None,
    dry_run: bool | None = None,
    sessions: dict[str, Any] | None = None,
) -> RetentionStats:
    """Delete stale sessions by age and capacity; dry_run only counts them.

    `deleted`/`bytes_freed` report what was (or, under dry_run, would be)
    removed. Runs synchronously on the event loop thread: the batch cap bounds
    the blocking time and a thread would race the in-process SESSIONS snapshot.
    """
    current = time.time() if now is None else now
    effective_dry_run = settings.session_cleanup_dry_run if dry_run is None else dry_run
    loaded = SESSIONS if sessions is None else sessions
    scanned = skipped_loaded = deleted = freed = 0
    total_bytes = 0
    candidates: list[tuple[str, float, int]] = []
    if persistence.SESSION_DIR.exists():
        for meta_path in persistence.SESSION_DIR.glob("*.meta.json"):
            sid = meta_path.name[: -len(".meta.json")]
            if not sid:
                # A bare ".meta.json" would resolve to the sessions directory
                # itself; never let rmtree reach it.
                continue
            meta = persistence.read_meta(sid)
            if not meta.get("owner") or not meta.get("workspace"):
                continue  # ownership unknown: never delete
            scanned += 1
            mtime, size = _measure(sid)
            total_bytes += size
            if sid in loaded:
                skipped_loaded += 1
            else:
                candidates.append((sid, mtime, size))

    def delete(sid: str, size: int) -> None:
        nonlocal deleted, freed
        if effective_dry_run:
            _LOGGER.info("session retention dry-run: would delete session %s", sid)
        else:
            persistence.delete_files(sid)
        deleted += 1
        freed += size

    remaining_batch = max(0, settings.session_cleanup_batch)
    days = settings.session_retention_days
    if days > 0:
        cutoff = current - days * _SECONDS_PER_DAY
        # Oldest first: with a batch cap, newer stale sessions must not crowd
        # out the ones that have been idle longest.
        for sid, mtime, size in sorted(candidates, key=lambda entry: entry[1]):
            if remaining_batch <= 0:
                break
            if mtime >= cutoff:
                continue
            delete(sid, size)
            candidates.remove((sid, mtime, size))
            total_bytes -= size
            remaining_batch -= 1

    limit_mb = settings.session_max_mb
    if limit_mb > 0:
        limit = limit_mb * _BYTES_PER_MB
        for sid, mtime, size in sorted(candidates, key=lambda entry: entry[1]):
            if total_bytes <= limit or remaining_batch <= 0:
                break
            delete(sid, size)
            candidates.remove((sid, mtime, size))
            total_bytes -= size
            remaining_batch -= 1
        if total_bytes > limit:
            _LOGGER.warning(
                "session storage %d bytes still above the %d MB limit; "
                "the rest belongs to sessions loaded in this process",
                total_bytes,
                limit_mb,
            )

    if deleted:
        audit.record_tool_call(
            "session_retention",
            {
                "scanned": scanned,
                "deleted": deleted,
                "bytesFreed": freed,
                "dryRun": effective_dry_run,
            },
            True,
            0,
            None,
        )
    return RetentionStats(
        scanned=scanned,
        deleted=deleted,
        skipped_loaded=skipped_loaded,
        bytes_freed=freed,
    )


async def retention_loop(settings: Settings) -> None:
    """Clean once at startup, then every interval; interval <= 0 means once."""
    interval = settings.session_cleanup_interval_seconds
    if interval < 0:
        _LOGGER.warning(
            "session cleanup interval %s < 0; using 0 (single pass at startup)",
            interval,
        )
        interval = 0
    while True:
        try:
            stats = cleanup_sessions(settings)
            _LOGGER.info(
                "session retention: scanned=%d deleted=%d skippedLoaded=%d bytesFreed=%d dryRun=%s",
                stats.scanned,
                stats.deleted,
                stats.skipped_loaded,
                stats.bytes_freed,
                settings.session_cleanup_dry_run,
            )
        except Exception as ex:  # noqa: BLE001 - one bad pass must not kill the loop
            _LOGGER.warning("session retention pass failed: %s", ex)
        if interval <= 0:
            return
        await asyncio.sleep(interval)
