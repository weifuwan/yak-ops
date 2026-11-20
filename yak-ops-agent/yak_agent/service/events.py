"""Per-session event log with replay and live fan-out.

One log per session: a bounded deque keeps recent events for reconnect replay,
while every subscriber also receives live events through its own asyncio queue.
A slow subscriber therefore never loses events because the replay window was
trimmed; only a subscriber that reconnects with an expired cursor falls back to
`replay_reset` and the message history.
"""

from __future__ import annotations

import asyncio
import time
from collections import deque
from collections.abc import AsyncIterator
from dataclasses import dataclass, field
from typing import Any

TERMINAL_TYPES = frozenset({"done", "error"})


def _now() -> str:
    return time.strftime("%Y-%m-%dT%H:%M:%S%z")


@dataclass
class EventLog:
    capacity: int = 1000
    events: deque[dict[str, Any]] = field(default_factory=deque)
    next_seq: int = 1
    terminal: dict[str, Any] | None = None
    closed: bool = False
    _lock: asyncio.Lock = field(default_factory=asyncio.Lock)
    _subscribers: set[asyncio.Queue] = field(default_factory=set)

    async def reset(self) -> None:
        """Drop the previous turn's events; `seq` stays monotonic."""
        async with self._lock:
            self.events.clear()
            self.terminal = None


async def emit(session: Any, payload: dict[str, Any]) -> None:
    """Append one event, stamp its sequence/turn, and fan it out."""
    log: EventLog = session.event_log
    async with log._lock:
        record = {"seq": log.next_seq, "at": _now(), **payload}
        if session.turn is not None:
            record.setdefault("turnId", session.turn.turn_id)
        log.next_seq += 1
        log.events.append(record)
        while len(log.events) > log.capacity:
            log.events.popleft()
        if record["type"] in TERMINAL_TYPES:
            log.terminal = record
        subscribers = list(log._subscribers)
    for queue in subscribers:
        queue.put_nowait(record)


async def close(session: Any, message: str) -> None:
    """Terminate every subscriber and reject later ones (deleted session)."""
    log: EventLog = session.event_log
    await emit(session, {"type": "error", "message": message})
    async with log._lock:
        log.closed = True
        subscribers = list(log._subscribers)
        log._subscribers.clear()
    for queue in subscribers:
        queue.put_nowait(None)


async def subscribe(session: Any, after: int = 0) -> AsyncIterator[dict[str, Any]]:
    """Replay retained events after `after`, then follow live events."""
    log: EventLog = session.event_log
    queue: asyncio.Queue = asyncio.Queue()
    reset = False
    idle = False
    async with log._lock:
        first = log.events[0]["seq"] if log.events else log.next_seq
        if after + 1 < first:
            reset = True
            after = first - 1
        if after >= log.next_seq:
            after = log.next_seq - 1
        retained = [event for event in log.events if event["seq"] > after]
        terminal_consumed = log.terminal is not None and after >= log.terminal["seq"]
        idle = terminal_consumed or (not retained and log.terminal is None and session.turn is None)
        if not idle:
            log._subscribers.add(queue)
    if reset:
        yield {"type": "replay_reset", "firstSeq": first, "seq": after}
    if idle:
        yield {"type": "stream_idle", "state": "idle"}
        return
    try:
        for event in retained:
            yield event
            if event["type"] in TERMINAL_TYPES:
                return
        while True:
            event = await queue.get()
            if event is None:
                return
            yield event
            if event["type"] in TERMINAL_TYPES:
                return
    finally:
        async with log._lock:
            log._subscribers.discard(queue)
