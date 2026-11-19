"""Minimal yak-ops REST client with pluggable authentication.

Security model (mirrors the OpenAPI agent-integration plan):
- credentials travel from the host process to the API server only; they are
  never exposed to the model;
- responses are envelope-checked (`{code, msg, data}`, code==0 is success)
  and hard-truncated before they can reach the model context.

Authentication modes:
- token: `token: <value>` request header (platform API-token feature);
- password: `POST /login` (form `userName` / `userPassword`) yields a
  `sessionId` cookie; requests then carry `Cookie: sessionId=<value>`.
  On HTTP 401 the session is refreshed once and the request retried.
"""

from __future__ import annotations

import json
import threading
import urllib.error
import urllib.parse
import urllib.request
from dataclasses import dataclass, field
from typing import Any

MAX_PAYLOAD_CHARS = 16_000
DEFAULT_TIMEOUT = 30

# Process-wide accounting of tool-level API calls, used by acceptance runs to
# judge outcomes on facts instead of guessing from model prose.
_TOOL_STATS: dict[str, Any] = {"calls": 0, "errors": []}
_STATS_LOCK = threading.Lock()


def stats_snapshot() -> dict[str, Any]:
    with _STATS_LOCK:
        return {"calls": _TOOL_STATS["calls"], "errors": list(_TOOL_STATS["errors"])}


def _record_success() -> None:
    with _STATS_LOCK:
        _TOOL_STATS["calls"] += 1


def _record_error(message: str) -> None:
    with _STATS_LOCK:
        _TOOL_STATS["errors"].append(message)


class YakApiError(RuntimeError):
    """Raised when yak-ops returns a non-success envelope or a transport error."""

    def __init__(self, message: str, status_code: int | None = None) -> None:
        super().__init__(message)
        self.status_code = status_code


def truncate(text: str, limit: int = MAX_PAYLOAD_CHARS) -> str:
    """Keep the tail of oversized payloads (logs/errors live at the end)."""
    if len(text) <= limit:
        return text
    return "...(truncated, head dropped)..." + text[-limit:]


def _to_query(params: dict[str, Any] | None) -> str:
    clean = {k: str(v) for k, v in (params or {}).items() if v is not None}
    return urllib.parse.urlencode(clean)


@dataclass
class YakAuth:
    """Holds credentials and resolves request auth headers/cookies.

    Thread-safe: AgentScope may run read-only tools concurrently
    (`is_concurrency_safe=True`), so session refresh is guarded by a lock.

    Three auth modes, in priority order:
    - `token`: platform API token (header `token`);
    - `session_id`: a sessionId cookie value handed over by the frontend
      (per-user identity) — used as-is, no password held; on 401 the caller
      re-authenticates upstream and sends a fresh sessionId;
    - `username`/`password`: server-side login (CLI / single-tenant default).
    """

    base_url: str
    token: str = ""
    session_id: str = ""
    username: str = ""
    password: str = ""
    timeout: int = DEFAULT_TIMEOUT

    _session_id: str = field(default="", init=False, repr=False)
    _lock: threading.Lock = field(default_factory=threading.Lock, init=False, repr=False)

    # ---- headers ----

    def headers(self) -> dict[str, str]:
        headers = {"Accept": "application/json"}
        if self.token:
            headers["token"] = self.token
        elif self.session_id:
            # Per-user sessionId from the frontend: used as-is, no login.
            headers["Cookie"] = f"sessionId={self.session_id}"
        else:
            session_id = self._ensure_session()
            if session_id:
                headers["Cookie"] = f"sessionId={session_id}"
        return headers

    # ---- session login ----

    def _ensure_session(self) -> str:
        if self.session_id or self._session_id:
            return self.session_id or self._session_id
        with self._lock:
            if not self._session_id:
                self._session_id = self._login()
            return self._session_id

    def refresh_session(self) -> None:
        with self._lock:
            self._session_id = ""

    def _login(self) -> str:
        body = urllib.parse.urlencode(
            {"userName": self.username, "userPassword": self.password}
        ).encode()
        request = urllib.request.Request(
            f"{self.base_url}/login",
            data=body,
            headers={
                "Content-Type": "application/x-www-form-urlencoded",
                "Accept": "application/json",
            },
            method="POST",
        )
        try:
            with urllib.request.urlopen(request, timeout=self.timeout) as response:
                payload = response.read().decode("utf-8")
                set_cookies = response.headers.get_all("Set-Cookie") or []
        except urllib.error.HTTPError as ex:
            raise YakApiError(f"login failed: HTTP {ex.code}", status_code=ex.code) from ex
        except urllib.error.URLError as ex:
            raise YakApiError(f"cannot reach yak-ops at {self.base_url}: {ex.reason}") from ex

        session_id = ""
        if payload:
            try:
                envelope = json.loads(payload)
                data = envelope.get("data") if isinstance(envelope, dict) else None
                if isinstance(data, dict):
                    session_id = str(data.get("sessionId") or data.get("session_id") or "")
            except json.JSONDecodeError:
                pass
        if not session_id:
            for raw in set_cookies:
                head = raw.split(";", 1)[0]
                name, _, value = head.partition("=")
                if name.strip() == "sessionId" and value.strip():
                    session_id = value.strip()
                    break
        if not session_id:
            raise YakApiError("login succeeded but no sessionId was returned")
        return session_id


def _request(
    auth: YakAuth, method: str, url: str, body: bytes | None = None, content_type: str | None = None
) -> str:
    headers = auth.headers()
    if content_type:
        headers["Content-Type"] = content_type
    request = urllib.request.Request(url, data=body, headers=headers, method=method)
    try:
        with urllib.request.urlopen(request, timeout=auth.timeout) as response:
            return response.read().decode("utf-8")
    except urllib.error.HTTPError as ex:
        detail = ex.read().decode("utf-8", errors="replace")[:500]
        raise YakApiError(
            f"HTTP {ex.code} on {urllib.parse.urlparse(url).path}: {detail}", status_code=ex.code
        ) from ex
    except urllib.error.URLError as ex:
        raise YakApiError(f"cannot reach yak-ops at {auth.base_url}: {ex.reason}") from ex


def yak_get(auth: YakAuth, path: str, params: dict[str, Any] | None = None) -> Any:
    """GET a yak-ops API path and return the envelope `data` field.

    `path` must start with "/" and already include the project segment when
    applicable, e.g. "/projects/123/data-sync/instances". On an expired
    session (401) the login is refreshed once and the request retried.
    Every outcome is recorded in the process-wide tool stats.
    """
    try:
        data = _get_envelope_data(auth, path, params)
    except YakApiError as ex:
        _record_error(f"{path}: {ex}")
        raise
    _record_success()
    return data


def _get_envelope_data(auth: YakAuth, path: str, params: dict[str, Any] | None) -> Any:
    url = f"{auth.base_url}{path}"
    query = _to_query(params)
    if query:
        url = f"{url}?{query}"
    try:
        body = _request(auth, "GET", url)
    except YakApiError as ex:
        if ex.status_code == 401 and not auth.token:
            auth.refresh_session()
            body = _request(auth, "GET", url)
        else:
            raise

    if not body:
        return None
    try:
        envelope = json.loads(body)
    except json.JSONDecodeError as ex:
        raise YakApiError(f"non-JSON response from {path}: {truncate(body, 300)}") from ex
    if not isinstance(envelope, dict) or "code" not in envelope:
        raise YakApiError(f"unexpected response shape from {path}: {truncate(body, 300)}")
    if envelope.get("code") != 0:
        raise YakApiError(
            f"yak-ops business error on {path}: code={envelope.get('code')} msg={envelope.get('msg')}"
        )
    return envelope.get("data")


def yak_write(
    auth: YakAuth,
    method: str,
    path: str,
    json_body: dict[str, Any] | None = None,
    params: dict[str, Any] | None = None,
) -> Any:
    """Send a mutating request (POST/PUT/DELETE) and return the envelope `data`.

    Same auth, envelope and accounting rules as `yak_get`.
    """
    try:
        data = _write_envelope_data(auth, method.upper(), path, json_body, params)
    except YakApiError as ex:
        _record_error(f"{method.upper()} {path}: {ex}")
        raise
    _record_success()
    return data


def yak_download(auth: YakAuth, path: str, params: dict[str, Any] | None = None) -> str:
    """GET a raw endpoint (e.g. /log/download-log) and return the body as text.

    No envelope parsing — download endpoints return bare file content.
    Accounted like any other call; the full body is returned to the caller
    (it lives in the script's scope, never in the model context).
    """
    try:
        url = f"{auth.base_url}{path}"
        query = _to_query(params)
        if query:
            url = f"{url}?{query}"
        try:
            body = _request(auth, "GET", url)
        except YakApiError as ex:
            if ex.status_code == 401 and not auth.token:
                auth.refresh_session()
                body = _request(auth, "GET", url)
            else:
                raise
    except YakApiError as ex:
        _record_error(f"{path}: {ex}")
        raise
    _record_success()
    return body


def _write_envelope_data(
    auth: YakAuth,
    method: str,
    path: str,
    json_body: dict[str, Any] | None,
    params: dict[str, Any] | None,
) -> Any:
    url = f"{auth.base_url}{path}"
    query = _to_query(params)
    if query:
        url = f"{url}?{query}"
    body = None
    content_type = None
    if json_body is not None:
        body = json.dumps(json_body, ensure_ascii=False).encode("utf-8")
        content_type = "application/json"
    try:
        raw = _request(auth, method, url, body=body, content_type=content_type)
    except YakApiError as ex:
        if ex.status_code == 401 and not auth.token:
            auth.refresh_session()
            raw = _request(auth, method, url, body=body, content_type=content_type)
        else:
            raise

    if not raw:
        return None
    try:
        envelope = json.loads(raw)
    except json.JSONDecodeError as ex:
        raise YakApiError(f"non-JSON response from {path}: {truncate(raw, 300)}") from ex
    if not isinstance(envelope, dict) or "code" not in envelope:
        raise YakApiError(f"unexpected response shape from {path}: {truncate(raw, 300)}")
    if envelope.get("code") != 0:
        raise YakApiError(
            f"yak-ops business error on {path}: code={envelope.get('code')} msg={envelope.get('msg')}"
        )
    return envelope.get("data")
