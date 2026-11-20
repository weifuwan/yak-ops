"""Map service-layer session errors to HTTP responses."""

from __future__ import annotations

from http import HTTPStatus

from fastapi import Request
from fastapi.responses import JSONResponse

from yak_agent.service.errors import (
    AgentSessionError,
    ConfirmationNotPending,
    SessionBusy,
    SessionForbidden,
    SessionNotFound,
    SessionStateInvalid,
)

_STATUS_BY_ERROR: dict[type[AgentSessionError], int] = {
    SessionForbidden: HTTPStatus.FORBIDDEN,
    SessionNotFound: HTTPStatus.NOT_FOUND,
    SessionBusy: HTTPStatus.CONFLICT,
    ConfirmationNotPending: HTTPStatus.CONFLICT,
    SessionStateInvalid: HTTPStatus.UNPROCESSABLE_ENTITY,
}


async def session_error_handler(_request: Request, exc: AgentSessionError) -> JSONResponse:
    status_code = _STATUS_BY_ERROR.get(type(exc), HTTPStatus.BAD_REQUEST)
    return JSONResponse(status_code=status_code, content={"detail": str(exc)})
