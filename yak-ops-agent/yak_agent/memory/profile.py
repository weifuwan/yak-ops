"""Pinned profile memory: names and hard preferences shared by (user, workspace).

ReMe keeps episodic and procedural memory searchable; this module keeps the few
facts that must be present in every turn -- the assistant name, the user name
and explicitly stated preferences -- in one small JSON file that is rendered
into the system prompt on every agent rebuild.
"""

from __future__ import annotations

import json
import logging
import os
import re
from dataclasses import dataclass, field
from datetime import datetime
from pathlib import Path
from typing import Any

from yak_agent.memory.sanitize import redact

_LOGGER = logging.getLogger(__name__)

PROFILE_FILE = "profile.json"
PROFILE_VERSION = 1

_MAX_NAME_CHARS = 12
_MAX_PREFERENCES = 10
_MAX_PREFERENCE_CHARS = 200
_MAX_BOOTSTRAP_CARDS = 2
_MAX_BOOTSTRAP_CHARS = 300
_MAX_PROFILE_BYTES = 64 * 1024

_HEADER = (
    "## 工作区记忆（用户显式指定，优先于默认角色描述）\n\n"
    "以下是用户在本工作区显式设定的称呼与偏好。回答身份、称呼类问题时以此为准，"
    "不要声称自己没有名字。与用户当前指令冲突时，以当前指令为准。"
)
_NOTES_HEADER = "历史记忆摘要（由历史会话提取，可能与用户原话有出入）："

_NAME = r"[^\s，。！？；、,.!?;「」“”\"']{1,12}"
_AGENT_NAME_PATTERNS = (
    re.compile(rf"你(?:以后)?(?:就)?叫\s*[「“\"']?({_NAME})"),
    re.compile(rf"你(?:以后)?(?:的)?名字(?:是|叫)\s*[「“\"']?({_NAME})"),
)
_USER_NAME_PATTERNS = (
    re.compile(rf"我(?:以后)?(?:就)?叫\s*[「“\"']?({_NAME})"),
    re.compile(rf"我(?:以后)?(?:的)?名字(?:是|叫)\s*[「“\"']?({_NAME})"),
    re.compile(rf"叫我\s*[「“\"']?({_NAME})"),
)
_PREFERENCE_PREFIX = re.compile(r"^(?:请?记住|记一下|从今以后)\s*[：:，,]?\s*")
_PREFERENCE_CLAUSE = re.compile(r"^以后(?:都|要|回答|必须|请|你)")
_CLAUSE = re.compile(r"[^。！？；\n，,;、]+[。！？；]?")

# ``哪`` alone is too aggressive (哪吒 is a name); only treat it as a question
# word when it starts 哪里/哪个/... or ends the clause.
_QUESTION = re.compile(
    r"[？?]|什么|啥|谁|吗|呢|哪(?=$|[\s，。！？；里儿个些边种位块天年月日次条张本])"
)
_NON_NAME_WORDS = ("一下", "一声", "一个", "起来", "帮", "先", "再")
_PRONOUNS = frozenset({"你", "我", "他", "她", "它", "您", "我们", "你们", "他们", "大家"})


@dataclass
class Profile:
    """Structured profile facts; ``notes`` holds bootstrap summaries only."""

    agent_name: str = ""
    user_name: str = ""
    preferences: list[str] = field(default_factory=list)
    notes: list[str] = field(default_factory=list)
    updated_at: str = ""


def user_profile_path(workspace_dir: Path) -> Path:
    """Return the user-level profile path for one memory scope directory."""
    return Path(workspace_dir).parent / PROFILE_FILE


def workspace_profile_path(workspace_dir: Path) -> Path:
    """Return the workspace-level profile path."""
    return Path(workspace_dir) / PROFILE_FILE


def load(workspace_dir: Path) -> Profile:
    """Merge the user-level and workspace-level profile, then bootstrap notes."""
    user = _from_dict(_read(user_profile_path(workspace_dir)))
    workspace = _from_dict(_read(workspace_profile_path(workspace_dir)))
    merged = Profile(
        agent_name=workspace.agent_name or user.agent_name,
        user_name=workspace.user_name or user.user_name,
        preferences=_dedupe([*user.preferences, *workspace.preferences])[-_MAX_PREFERENCES:],
        updated_at=max(filter(None, (user.updated_at, workspace.updated_at)), default=""),
    )
    if not merged.agent_name and not merged.user_name:
        merged.notes = _bootstrap_notes(workspace_dir)
    return merged


def render(profile: Profile, max_bytes: int) -> str | None:
    """Render one bounded prompt block; names survive truncation."""
    if max_bytes <= 0:
        return None
    bullets: list[str] = []
    if profile.agent_name:
        bullets.append(f"- 助手称呼：{profile.agent_name}")
    if profile.user_name:
        bullets.append(f"- 用户称呼：{profile.user_name}")
    bullets.extend(f"- 硬性偏好：{preference}" for preference in profile.preferences)
    if not bullets and not profile.notes:
        return None

    block = _HEADER
    for index, bullet in enumerate(bullets):
        separator = "\n\n" if index == 0 else "\n"
        candidate = f"{block}{separator}{bullet}"
        if len(candidate.encode("utf-8")) > max_bytes:
            return _truncate(block, max_bytes)
        block = candidate

    if profile.notes:
        notes = "\n".join(f"- {note}" for note in profile.notes)
        prefix = f"{block}\n\n{_NOTES_HEADER}\n"
        candidate = prefix + notes
        if len(candidate.encode("utf-8")) <= max_bytes:
            block = candidate
        else:
            room = max_bytes - len(prefix.encode("utf-8"))
            if room > 40:
                block = prefix + _truncate(notes, room)
    return block


def remember(workspace_dir: Path, text: str) -> str | None:
    """Apply explicit naming / preference statements to the user-level profile."""
    agent_name, user_name, preferences = _extract(text)
    if not (agent_name or user_name or preferences):
        return None

    path = user_profile_path(workspace_dir)
    stored = _from_dict(_read(path))
    changes: list[str] = []
    if agent_name and agent_name != stored.agent_name:
        stored.agent_name = agent_name
        changes.append(f"agentName={agent_name}")
    if user_name and user_name != stored.user_name:
        stored.user_name = user_name
        changes.append(f"userName={user_name}")
    for preference in preferences:
        if preference not in stored.preferences:
            stored.preferences.append(preference)
            changes.append("preference")
    if not changes:
        return None

    stored.preferences = stored.preferences[-_MAX_PREFERENCES:]
    stored.updated_at = datetime.now().astimezone().isoformat(timespec="seconds")
    _write(path, stored)
    return ", ".join(changes)


def _extract(text: str) -> tuple[str, str, list[str]]:
    """Extract names and preferences from one user message."""
    agent_name = ""
    user_name = ""
    preferences: list[str] = []
    for raw in _CLAUSE.findall(text):
        clause = raw.strip()
        if not clause:
            continue
        clause_agent = _match_name(clause, _AGENT_NAME_PATTERNS)
        clause_user = _match_name(clause, _USER_NAME_PATTERNS)
        agent_name = agent_name or clause_agent
        user_name = user_name or clause_user
        if clause_agent or clause_user:
            # ``记住 你叫狗蛋`` names the assistant; it is not a preference.
            continue
        preference = _match_preference(clause)
        if preference:
            preferences.append(preference)
    return agent_name, user_name, preferences


def _match_name(clause: str, patterns: tuple[re.Pattern[str], ...]) -> str:
    if _is_question(clause):
        return ""
    for pattern in patterns:
        match = pattern.search(clause)
        if not match:
            continue
        name = match.group(1).strip("「」“”\"'")
        if (
            name
            and name not in _PRONOUNS
            and len(name) <= _MAX_NAME_CHARS
            and not any(word in name for word in _NON_NAME_WORDS)
        ):
            return name
    return ""


def _match_preference(clause: str) -> str:
    if _PREFERENCE_CLAUSE.match(clause):
        preference = clause
    else:
        match = _PREFERENCE_PREFIX.match(clause)
        if not match:
            return ""
        preference = clause[match.end() :]
    preference = redact(preference.strip().strip("：:，,。.！!"))
    if len(preference) < 2:
        return ""
    return preference[:_MAX_PREFERENCE_CHARS]


def _is_question(clause: str) -> bool:
    return bool(_QUESTION.search(clause))


def _from_dict(data: dict[str, Any]) -> Profile:
    raw_preferences = data.get("preferences")
    preferences = (
        [
            item.strip()[:_MAX_PREFERENCE_CHARS]
            for item in raw_preferences
            if isinstance(item, str) and item.strip()
        ]
        if isinstance(raw_preferences, list)
        else []
    )
    return Profile(
        agent_name=_text(data.get("agentName")),
        user_name=_text(data.get("userName")),
        preferences=_dedupe(preferences)[-_MAX_PREFERENCES:],
        updated_at=_text(data.get("updatedAt")),
    )


def _text(value: Any) -> str:
    return value.strip() if isinstance(value, str) else ""


def _dedupe(values: list[str]) -> list[str]:
    seen: set[str] = set()
    result: list[str] = []
    for value in values:
        normalized = " ".join(value.split())
        if not normalized or normalized in seen:
            continue
        seen.add(normalized)
        result.append(normalized)
    return result


def _read(path: Path) -> dict[str, Any]:
    try:
        if path.stat().st_size > _MAX_PROFILE_BYTES:
            _LOGGER.warning("profile too large, ignored (%s)", path)
            return {}
        raw = path.read_text(encoding="utf-8")
    except FileNotFoundError:
        return {}
    except OSError as ex:
        _LOGGER.warning("profile unreadable (%s): %s", path, ex)
        return {}
    try:
        data = json.loads(raw)
    except ValueError as ex:
        _LOGGER.warning("profile ignored (%s): %s", path, ex)
        return {}
    return data if isinstance(data, dict) else {}


def _write(path: Path, profile: Profile) -> None:
    path.parent.mkdir(parents=True, exist_ok=True)
    payload = {
        "version": PROFILE_VERSION,
        "agentName": profile.agent_name,
        "userName": profile.user_name,
        "preferences": list(profile.preferences),
        "updatedAt": profile.updated_at,
    }
    tmp = path.with_name(f".{path.name}.tmp")
    try:
        tmp.write_text(
            json.dumps(payload, ensure_ascii=False, indent=2) + "\n",
            encoding="utf-8",
        )
        os.replace(tmp, path)
    finally:
        tmp.unlink(missing_ok=True)


def _bootstrap_notes(workspace_dir: Path) -> list[str]:
    """Summaries of existing personal digest cards before any structured profile."""
    base = Path(workspace_dir) / "digest" / "personal"
    if not base.is_dir():
        return []
    notes: list[str] = []
    for path in sorted(base.glob("*.md"))[:_MAX_BOOTSTRAP_CARDS]:
        try:
            text = path.read_text(encoding="utf-8")
        except OSError as ex:
            _LOGGER.warning("profile bootstrap skipped (%s): %s", path, ex)
            continue
        description = _description(text)
        if description:
            notes.append(_truncate(redact(description), _MAX_BOOTSTRAP_CHARS))
    return notes


def _description(text: str) -> str:
    """Read ``description`` from a markdown frontmatter block, folded or plain."""
    lines = text.splitlines()
    if not lines or lines[0].strip() != "---":
        return ""
    parts: list[str] = []
    collecting = False
    for line in lines[1:]:
        if line.strip() == "---":
            break
        if line.startswith("description:"):
            collecting = True
            parts.append(line.split(":", 1)[1].strip())
            continue
        if collecting:
            if line[:1].isspace():
                parts.append(line.strip())
                continue
            break
    value = " ".join(part for part in parts if part)
    for indicator in ("|-", ">-", "|+", ">+", "|", ">"):
        if value.startswith(indicator):
            value = value[len(indicator) :].lstrip()
            break
    return value.strip().strip("'\"").replace("''", "'")


def _truncate(text: str, max_bytes: int) -> str:
    data = text.encode("utf-8")
    if len(data) <= max_bytes:
        return text
    suffix = "…"
    room = max(0, max_bytes - len(suffix.encode("utf-8")))
    return data[:room].decode("utf-8", "ignore") + suffix
