"""Minimal yak-ops REST client.

Security model (mirrors the OpenAPI agent-integration plan):
- the token travels from the host process to the API server and is never
  exposed to the model;
- responses are envelope-checked (`{code, msg, data}`, code==0 is success)
  and hard-truncated before they can reach the model context.
"""

from __future__ import annotations

import json
import urllib.parse
import urllib.request
from typing import Any

MAX_PAYLOAD_CHARS = 16_000
DEFAULT_TIMEOUT = 30


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


def _to_query(params: dict[str, Any]) -> str:
    clean = {k: str(v) for k, v in params.items() if v is not None}
    return urllib.parse.urlencode(clean)


def yak_get(base_url: str, token: str, path: str, params: dict[str, Any] | None = None) -> Any:
    """GET a yak-ops API path and return the envelope `data` field.

    `path` must start with "/" and already include the project segment when
    applicable, e.g. "/projects/123/data-sync/instances".
    """
    url = f"{base_url}{path}"
    if params:
        url = f"{url}?{_to_query(params)}"
    request = urllib.request.Request(
        url,
        headers={"token": token, "Accept": "application/json"},
        method="GET",
    )
    try:
        with urllib.request.urlopen(request, timeout=DEFAULT_TIMEOUT) as response:
            body = response.read().decode("utf-8")
    except urllib.error.HTTPError as ex:  # 401 expired token, 403 rate limit, ...
        detail = ex.read().decode("utf-8", errors="replace")[:500]
        raise YakApiError(f"HTTP {ex.code} on {path}: {detail}", status_code=ex.code) from ex
    except urllib.error.URLError as ex:
        raise YakApiError(f"cannot reach yak-ops at {base_url}: {ex.reason}") from ex

    if not body:
        return None
    try:
        envelope = json.loads(body)
    except json.JSONDecodeError as ex:
        raise YakApiError(f"non-JSON response from {path}: {truncate(body, 300)}") from ex
    if not isinstance(envelope, dict) or "code" not in envelope:
        raise YakApiError(f"unexpected response shape from {path}: {truncate(body, 300)}")
    if envelope.get("code") != 0:
        raise YakApiError(f"yak-ops business error on {path}: code={envelope.get('code')} msg={envelope.get('msg')}")
    return envelope.get("data")
