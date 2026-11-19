"""Session lifecycle errors; the API layer maps them to HTTP responses."""

from __future__ import annotations


class AgentSessionError(Exception):
    """Base error for session access and lifecycle conflicts."""

    default_message = "会话请求无法处理"

    def __init__(self, message: str | None = None) -> None:
        super().__init__(message or self.default_message)


class SessionForbidden(AgentSessionError):
    default_message = "当前用户没有该会话或工作空间的访问权限"


class SessionNotFound(AgentSessionError):
    default_message = "会话不存在或已过期"


class SessionBusy(AgentSessionError):
    default_message = "当前会话正在处理上一条消息，请稍后再试"


class ConfirmationNotPending(AgentSessionError):
    default_message = "当前没有待确认的操作"


class SessionStateInvalid(AgentSessionError):
    default_message = "会话状态不可读，请新建会话"
