import {
  extractErrorMessage,
  extractUnknownErrorMessage,
  isApiResponse,
  isSuccessfulResponse,
  isUnauthenticatedResponse,
  protocolForUrl,
  type ApiProtocol,
  type ApiResponse,
} from "@/service/http/response";
import { notifyOnce } from "@/utils/notification";

export type { ApiProtocol, ApiResponse } from "@/service/http/response";

export type BusinessErrorMode = "reject" | "resolve";

export type RequestOptions = RequestInit & {
  data?: unknown;
  protocol?: ApiProtocol;
  businessErrorMode?: BusinessErrorMode;
  skipErrorHandler?: boolean;
  responseType?: "blob";
  getResponse?: boolean;
};

export class BizError extends Error {
  constructor(
    message: string,
    readonly code?: number,
    readonly response?: ApiResponse,
    readonly protocol: ApiProtocol = "yak-ops",
  ) {
    super(message);
    this.name = "BizError";
  }
}

const HTTP_MESSAGES: Record<number, string> = {
  400: "请求参数有误",
  401: "当前请求未通过身份认证，请重新登录。",
  403: "当前用户已登录，但没有访问该资源的权限。",
  404: "资源不存在",
  405: "请求方式不支持",
  422: "数据校验未通过",
  500: "服务器发生错误，请检查服务器。",
  502: "网关错误",
  503: "服务不可用",
  504: "网关超时",
};

const isLoginPath = () =>
  window.location.pathname.toLowerCase().startsWith("/login");

export const goLogin = () => {
  if (isLoginPath()) return;
  const returnTo =
    `${window.location.pathname}${window.location.search}${window.location.hash}`;
  window.location.replace(
    `/login?returnTo=${encodeURIComponent(returnTo)}`,
  );
};

export const handleAuthenticationFailure = (
  reason = "当前登录信息已过期，请重新登录后继续操作。",
) => {
  if (isLoginPath()) {
    notifyOnce("login-authentication", {
      type: "error",
      title: "登录失败",
      description: reason,
      meta: "请检查账号密码或稍后重试",
    });
    return;
  }

  notifyOnce("authentication", {
    type: "warning",
    title: "登录状态失效",
    description: reason,
    meta: "即将跳转登录页",
  });
  goLogin();
};

const handleBusinessError = (
  error: BizError,
  skipErrorHandler: boolean,
) => {
  if (skipErrorHandler) return;

  if (isUnauthenticatedResponse(error.response, error.protocol)) {
    handleAuthenticationFailure(error.message);
    return;
  }

  notifyOnce(
    `business:${error.protocol}:${error.code}:${error.message}`,
    {
      type: "error",
      title: "操作失败",
      description: error.message || "未知错误",
      meta: "请稍后重试",
    },
  );
};

const parseResponse = async (response: Response) => {
  const contentType = response.headers.get("content-type") ?? "";
  if (!contentType.includes("application/json")) return undefined;
  return response.json() as Promise<unknown>;
};

export default async function request<T>(
  url: string,
  options: RequestOptions = {},
): Promise<T> {
  const {
    data,
    protocol: explicitProtocol,
    businessErrorMode = "reject",
    skipErrorHandler = false,
    responseType,
    getResponse,
    ...requestInit
  } = options;

  const headers = new Headers(requestInit.headers);
  let body = requestInit.body;

  if (body === undefined && data !== undefined) {
    if (data instanceof FormData) {
      body = data;
    } else {
      if (!headers.has("Content-Type")) {
        headers.set("Content-Type", "application/json");
      }
      body = JSON.stringify(data);
    }
  }

  let response: Response;
  try {
    response = await fetch(url, {
      ...requestInit,
      headers,
      body,
      credentials: requestInit.credentials ?? "include",
    });
  } catch (error) {
    if (!skipErrorHandler) {
      notifyOnce("network", {
        type: "warning",
        title: "网络异常",
        description: "当前无法连接到服务器，请检查网络或稍后再试。",
        meta: "连接中断",
      });
    }
    throw error;
  }

  if (responseType === "blob") {
    if (!response.ok) {
      throw new Error(HTTP_MESSAGES[response.status] ?? `HTTP ${response.status}`);
    }
    const blob = await response.blob();
    return (getResponse ? { data: blob, response } : blob) as T;
  }

  const payload = await parseResponse(response);
  const protocol = explicitProtocol ?? protocolForUrl(response.url || url);

  if (!response.ok) {
    const message = extractUnknownErrorMessage(
      payload,
      HTTP_MESSAGES[response.status] ?? response.statusText ?? "请求失败",
    );

    if (response.status === 401) {
      if (!skipErrorHandler) handleAuthenticationFailure(message);
    } else if (!skipErrorHandler) {
      notifyOnce(`http:${response.status}:${url}:${message}`, {
        type: "error",
        title: HTTP_MESSAGES[response.status] ?? "请求失败",
        description: message,
        meta: `HTTP ${response.status}`,
      });
    }

    throw new Error(message);
  }

  if (isApiResponse(payload)) {
    if (isUnauthenticatedResponse(payload, protocol)) {
      const error = new BizError(
        extractErrorMessage(payload, "登录状态失效"),
        payload.code,
        payload,
        protocol,
      );
      handleBusinessError(error, skipErrorHandler);
      throw error;
    }

    if (!isSuccessfulResponse(payload, protocol)) {
      const error = new BizError(
        extractErrorMessage(payload),
        payload.code,
        payload,
        protocol,
      );
      handleBusinessError(error, skipErrorHandler);
      if (businessErrorMode === "reject") throw error;
    }
  }

  return payload as T;
}
