"""Map service-layer session errors to HTTP responses."""

from __future__ import annotations

from http import HTTPStatus

from fastapi import Request
from fastapi.responses import JSONResponse

from yak_agent.service.errors import (
    AgentSessionError,
    ConfirmationInvalid,
    ConfirmationNotPending,
    ContextBudgetExceeded,
    MemoryDisabled,
    QueueItemNotFound,
    QueueItemStarted,
    SessionBusy,
    SessionForbidden,
    SessionNotFound,
    SessionQueueFull,
    SessionStateInvalid,
    ToolConfigurationExceeded,
)

_STATUS_BY_ERROR: dict[type[AgentSessionError], int] = {
    SessionForbidden: HTTPStatus.FORBIDDEN,
    SessionNotFound: HTTPStatus.NOT_FOUND,
    SessionBusy: HTTPStatus.CONFLICT,
    SessionQueueFull: HTTPStatus.TOO_MANY_REQUESTS,
    QueueItemNotFound: HTTPStatus.NOT_FOUND,
    QueueItemStarted: HTTPStatus.CONFLICT,
    ConfirmationNotPending: HTTPStatus.CONFLICT,
    ConfirmationInvalid: HTTPStatus.UNPROCESSABLE_ENTITY,
    SessionStateInvalid: HTTPStatus.UNPROCESSABLE_ENTITY,
    ContextBudgetExceeded: HTTPStatus.UNPROCESSABLE_ENTITY,
    MemoryDisabled: HTTPStatus.CONFLICT,
    ToolConfigurationExceeded: HTTPStatus.UNPROCESSABLE_ENTITY,
}


async def session_error_handler(_request: Request, exc: AgentSessionError) -> JSONResponse:
    status_code = _STATUS_BY_ERROR.get(type(exc), HTTPStatus.BAD_REQUEST)
    return JSONResponse(status_code=status_code, content={"detail": str(exc)})
