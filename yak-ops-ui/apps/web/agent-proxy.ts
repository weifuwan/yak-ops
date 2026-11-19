import type { ProxyOptions } from "vite";

export const DATA_AGENT_PROXY_PREFIX = "/yak-ops/agent-api";

export function readYakSessionId(cookieHeader?: string): string | null {
  if (!cookieHeader) return null;

  for (const cookie of cookieHeader.split(";")) {
    const separator = cookie.indexOf("=");
    if (separator < 0 || cookie.slice(0, separator).trim() !== "sessionId") continue;

    const value = cookie.slice(separator + 1).trim();
    if (!value) return null;

    try {
      return decodeURIComponent(value);
    } catch {
      return value;
    }
  }

  return null;
}

function readForwardedHeader(value: string | string[] | undefined): string | null {
  if (typeof value !== "string") return null;
  const normalized = value.trim();
  return normalized || null;
}

export function createDataAgentProxy(agentUrl: string): ProxyOptions {
  return {
    target: agentUrl,
    changeOrigin: true,
    rewrite: (path) => path.replace(/^\/yak-ops\/agent-api/, "") || "/",
    configure: (proxy) => {
      proxy.on("proxyReq", (proxyRequest, request) => {
        const sessionId = readYakSessionId(request.headers.cookie);
        const userId = readForwardedHeader(request.headers["x-yak-user-id"]);
        const workspaceId = readForwardedHeader(request.headers["x-yak-workspace-id"]);

        // sessionId is HttpOnly. Forward it server-to-server instead of exposing it to browser JavaScript.
        proxyRequest.setHeader("X-Yak-Session-Id", sessionId || "__missing_session__");

        // User/workspace values come from the authenticated frontend context.
        // The Agent backend independently verifies both against the platform session.
        if (userId) proxyRequest.setHeader("X-Yak-User-Id", userId);
        else proxyRequest.removeHeader("X-Yak-User-Id");

        if (workspaceId) proxyRequest.setHeader("X-Yak-Workspace-Id", workspaceId);
        else proxyRequest.removeHeader("X-Yak-Workspace-Id");

        proxyRequest.removeHeader("cookie");
      });
    },
  };
}
