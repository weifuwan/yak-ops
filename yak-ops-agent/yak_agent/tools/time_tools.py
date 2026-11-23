"""Host-side clock tool: the authoritative current time for the model."""

from __future__ import annotations

import json
from datetime import UTC, datetime
from zoneinfo import ZoneInfo

from agentscope.tool import FunctionTool

from yak_agent.config import PLATFORM_TIMEZONE

_WEEKDAYS = ("星期一", "星期二", "星期三", "星期四", "星期五", "星期六", "星期日")
_API_TIME_FORMAT = "%Y-%m-%d %H:%M:%S"


def build_time_tools() -> list[FunctionTool]:
    def get_current_time() -> str:
        """获取 agent 服务器当前时间（平台时区 Asia/Shanghai 与 UTC）。涉及“现在/今天/昨天/最近 N 小时”等时间口径，或需要与任务实例 startTime/endTime、日志时间比较时，先调用本工具，不要凭记忆或推测。返回 JSON：timezone（平台时区）、iso（带偏移的平台时间）、utc（UTC ISO）、apiUtcTime（平台接口时间格式 yyyy-MM-dd HH:mm:ss，UTC）、date/time/weekday（平台时区）、epochMillis（Unix 毫秒）。"""
        platform = datetime.now(ZoneInfo(PLATFORM_TIMEZONE))
        utc = platform.astimezone(UTC)
        return json.dumps(
            {
                "timezone": PLATFORM_TIMEZONE,
                "iso": platform.isoformat(timespec="seconds"),
                "utc": utc.isoformat(timespec="seconds").replace("+00:00", "Z"),
                "apiUtcTime": utc.strftime(_API_TIME_FORMAT),
                "date": platform.strftime("%Y-%m-%d"),
                "time": platform.strftime("%H:%M:%S"),
                "weekday": _WEEKDAYS[platform.weekday()],
                "epochMillis": int(platform.timestamp() * 1000),
            },
            ensure_ascii=False,
        )

    return [FunctionTool(get_current_time, is_read_only=True, is_concurrency_safe=True)]
