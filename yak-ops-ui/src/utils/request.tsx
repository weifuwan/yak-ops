/* eslint-disable @typescript-eslint/dot-notation */
import { extractErrorMessage, extractUnknownErrorMessage, isApiResponse, isSuccessfulResponse, isUnauthenticatedResponse, protocolForUrl, type ApiProtocol, type ApiResponse } from "@/services/http/response";
import { notifyOnce } from "@/utils/notifyOnce";
import { dispatchAuthenticationInvalidated } from "@/utils/security/authentication";
import { applyCurrentProjectHeader } from "@/utils/security/projectContext";
import { history } from "umi";
import { extend } from "umi-request";

export type { ApiProtocol, ApiResponse } from "@/services/http/response";

export type BusinessErrorMode = "reject" | "resolve";

const codeMessage: Record<number, string> = {
  10000: "系统未知错误，请反馈给管理员",
  200: "服务器成功返回请求的数据。",
  201: "新建或修改数据成功。",
  202: "一个请求已经进入后台排队（异步任务）。",
  204: "删除数据成功。",
  400: "发出的请求有错误，服务器没有进行新建或修改数据的操作。",
  401: "当前请求未通过身份认证，请重新登录。",
  403: "当前用户已登录，但没有访问该资源的权限。",
  404: "发出的请求针对的是不存在的记录，服务器没有进行操作。",
  405: "请求方法不被当前接口允许，请检查请求方式。",
  406: "请求的格式不可得。",
  410: "请求的资源被永久删除，且不会再得到的。",
  422: "当创建一个对象时，发生一个验证错误。",
  500: "服务器发生错误，请检查服务器。",
  502: "网关错误。",
  503: "服务不可用，服务器暂时过载或维护。",
  504: "网关超时。",
};

const getHttpErrorPresentation = (status: number) => {
  if (status >= 500) {
    return {
      title: "服务暂时不可用",
      meta: `HTTP ${status} · 请稍后重试`,
    };
  }

  switch (status) {
    case 400:
      return { title: "请求参数有误", meta: "请检查输入内容" };
    case 403:
      return { title: "无权访问", meta: "权限不足" };
    case 404:
      return { title: "资源不存在", meta: "请确认资源仍然存在" };
    case 405:
      return { title: "请求方式不支持", meta: "请刷新页面后重试" };
    case 422:
      return { title: "数据校验未通过", meta: "请检查输入内容" };
    default:
      return { title: "请求失败", meta: `HTTP ${status}` };
  }
};

export class BizError extends Error {
  code?: number;
  response?: ApiResponse<any>;
  skipErrorHandler?: boolean;
  protocol: ApiProtocol;

  constructor(
    message: string,
    code?: number,
    response?: ApiResponse<any>,
    skipErrorHandler = false,
    protocol: ApiProtocol = "yak-ops"
  ) {
    super(message);
    this.name = "BizError";
    this.code = code;
    this.response = response;
    this.skipErrorHandler = skipErrorHandler;
    this.protocol = protocol;
  }
}

export const goLogin = () => {
  if (!window.location.pathname.toLowerCase().startsWith("/login")) {
    const returnTo = `${window.location.pathname}${window.location.search}${window.location.hash}`;
    history.replace(`/login?returnTo=${encodeURIComponent(returnTo)}`);
  }
};

let authenticationFailureHandled = false;

/** Re-arm expiry handling only after authentication has been established. */
export const resetAuthenticationFailure = () => {
  authenticationFailureHandled = false;
};

/** HTTP 401 与业务未认证码的唯一处理出口。 */
export const handleAuthenticationFailure = (
  reason = "当前登录信息已过期，请重新登录后继续操作。"
) => {
  dispatchAuthenticationInvalidated();
  if (window.location.pathname.toLowerCase().startsWith("/login")) {
    notifyOnce("login-authentication", {
      type: "error",
      title: "登录失败",
      description: reason,
      meta: "请检查账号密码或稍后重试",
    });
    return;
  }
  if (authenticationFailureHandled) return;

  authenticationFailureHandled = true;
  notifyOnce("authentication", {
    type: "warning",
    title: "登录状态失效",
    description: reason,
    meta: "即将跳转登录页",
  });
  goLogin();
};

/** 业务异常的唯一展示出口。 */
const handleBusinessError = (error: BizError) => {
  if (error.skipErrorHandler) return;

  if (isUnauthenticatedResponse(error.response, error.protocol)) {
    handleAuthenticationFailure(error.message);
    return;
  }

  notifyOnce(`business:${error.protocol}:${error.code}:${error.message}`, {
    type: "error",
    title: "操作失败",
    description: error.message || "未知错误",
    meta: "请稍后重试",
  });
};

const shouldSkipErrorHandler = (error: any): boolean => {
  const requestOptions =
    error?.request?.options ??
    error?.requestOptions ??
    error?.options ??
    error?.request;
  return Boolean(requestOptions?.skipErrorHandler);
};

/** 唯一错误出口。展示完成后仍必须拒绝 Promise，避免失败请求继续执行成功链路。 */
const errorHandler = (error: any): Response | undefined => {
  const { response } = error;

  // 业务异常
  if (error instanceof BizError) {
    handleBusinessError(error);
    throw error;
  }

  const skipErrorHandler = shouldSkipErrorHandler(error);

  // HTTP 异常。umi-request 会把 JSON 错误体放在 error.data 中，优先展示
  // 后端真实 msg/message；HTML 错误页等 transport diagnostics 会在
  // services/http/response 边界被过滤，避免技术细节直接进入用户通知。
  if (response?.status) {
    const { status, url } = response;
    const protocol = protocolForUrl(url);
    const payload: unknown = error?.data;
    const fallback = codeMessage[status] || response.statusText || "请求失败";
    const errorText = extractUnknownErrorMessage(payload, fallback);

    // 401 明确代表未认证；另外兼容网关把未登录业务响应包装成
    // 其他 HTTP 状态的情况。403/405 本身不等于登录失效。
    const payloadUnauthenticated =
      isApiResponse(payload) && isUnauthenticatedResponse(payload, protocol);
    if (status === 401 || payloadUnauthenticated) {
      if (!skipErrorHandler) handleAuthenticationFailure(errorText);
      throw error;
    }

    if (!skipErrorHandler) {
      const presentation = getHttpErrorPresentation(status);
      notifyOnce(`http:${status}:${url || ""}:${errorText}`, {
        type: "error",
        title: presentation.title,
        description: errorText,
        meta: presentation.meta,
        duration: 3.5,
      });
    }

    throw error;
  }

  // Abort is caller-controlled (navigation, timeout, stale request), not a network fault.
  if (error?.name === "AbortError" || error?.type === "aborted") throw error;

  // 网络异常
  if (!skipErrorHandler) {
    notifyOnce("network", {
      type: "warning",
      title: "网络异常",
      description: "当前无法连接到服务器，请检查网络或稍后再试。",
      meta: "连接中断",
    });
  }

  throw error;
};

function createClient() {
  return extend({
    errorHandler,
    credentials: "include",
  });
}

const request = createClient();

request.interceptors.request.use((url: string, options: any) => {
  const headers = applyCurrentProjectHeader(url, options.headers || {});

  return {
    url,
    options: {
      ...options,
      headers,
    },
  };
});

/** 识别统一响应中的业务状态，并按调用方需要选择返回响应或拒绝 Promise。 */
request.interceptors.response.use(async (response: Response, options: any) => {
  if (response.status === 204 || options?.responseType === "blob") return response;
  const contentType = response.headers.get("content-type") || "";

  if (!contentType.includes("application/json")) {
    return response;
  }

  const clonedResponse = response.clone();
  const res: unknown = await clonedResponse.json();
  if (!isApiResponse(res)) return response;
  // Explicit client protocol wins because a reverse proxy may rewrite the URL.
  const protocol: ApiProtocol = options?.protocol || protocolForUrl(response.url);

  if (isUnauthenticatedResponse(res, protocol)) {
    throw new BizError(
      extractErrorMessage(res, "登录状态失效"),
      res.code,
      res,
      options?.skipErrorHandler,
      protocol
    );
  }

  if (
    typeof res?.code !== "undefined" &&
    !isSuccessfulResponse(res, protocol)
  ) {
    const businessError = new BizError(
      extractErrorMessage(res),
      res.code,
      res,
      options?.skipErrorHandler,
      protocol
    );

    if (options?.businessErrorMode === "resolve") {
      handleBusinessError(businessError);
      return response;
    }

    throw businessError;
  }

  return response;
});

export default request;
