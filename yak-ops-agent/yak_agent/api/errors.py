"""Map service-layer session errors to HTTP responses."""

from __future__ import annotations

from fastapi import Request
from fastapi.responses import JSONResponse
from starlette import status

from yak_agent.service.errors import (
    AgentSessionError,
    ConfirmationNotPending,
    SessionBusy,
    SessionForbidden,
    SessionNotFound,
    SessionStateInvalid,
)

_STATUS_BY_ERROR: dict[type[AgentSessionError], int] = {
    SessionForbidden: status.HTTP_403_FORBIDDEN,
    SessionNotFound: status.HTTP_404_NOT_FOUND,
    SessionBusy: status.HTTP_409_CONFLICT,
    ConfirmationNotPending: status.HTTP_409_CONFLICT,
    SessionStateInvalid: status.HTTP_422_UNPROCESSABLE_CONTENT,
}


async def session_error_handler(_request: Request, exc: AgentSessionError) -> JSONResponse:
    status_code = _STATUS_BY_ERROR.get(type(exc), status.HTTP_400_BAD_REQUEST)
    return JSONResponse(status_code=status_code, content={"detail": str(exc)})
