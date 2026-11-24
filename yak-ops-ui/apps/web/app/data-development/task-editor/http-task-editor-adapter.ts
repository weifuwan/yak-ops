import {
  buildNodeResourceList,
  normalizeNodeLocalParams,
  normalizeNodeResourceList,
} from "./common/node-resource-params/adapter.ts";
import type { NodeLocalParam } from "./common/node-resource-params/types";

export const HTTP_REQUEST_METHODS = ["GET", "POST", "PUT", "DELETE"] as const;
export type HttpRequestMethod = (typeof HTTP_REQUEST_METHODS)[number];

export const HTTP_CHECK_CONDITIONS = [
  "STATUS_CODE_DEFAULT",
  "STATUS_CODE_CUSTOM",
  "BODY_CONTAINS",
  "BODY_NOT_CONTAINS",
] as const;
export type HttpCheckCondition = (typeof HTTP_CHECK_CONDITIONS)[number];

export type HttpParametersType = "HEADERS" | "PARAMETER";

export type HttpProperty = {
  prop: string;
  httpParametersType: HttpParametersType;
  value: string;
};

export type HttpTaskForm = {
  url: string;
  httpMethod: HttpRequestMethod;
  httpParams: HttpProperty[];
  httpBody: string;
  httpCheckCondition: HttpCheckCondition;
  condition: string;
  connectTimeout: string;
  resourceList: string[];
  localParams: NodeLocalParam[];
};

const DEFAULT_CONNECT_TIMEOUT = 10_000;

function readObject(value: unknown): Record<string, unknown> {
  if (typeof value === "string") {
    try {
      const parsed: unknown = JSON.parse(value);
      return parsed && typeof parsed === "object" && !Array.isArray(parsed)
        ? (parsed as Record<string, unknown>)
        : {};
    } catch {
      return {};
    }
  }

  return value && typeof value === "object" && !Array.isArray(value)
    ? (value as Record<string, unknown>)
    : {};
}

function readString(value: unknown): string {
  return value === null || value === undefined ? "" : String(value);
}

function isHttpRequestMethod(value: unknown): value is HttpRequestMethod {
  return HTTP_REQUEST_METHODS.some((method) => method === value);
}

function isHttpCheckCondition(value: unknown): value is HttpCheckCondition {
  return HTTP_CHECK_CONDITIONS.some((condition) => condition === value);
}

export function parseHttpTaskForm(value: unknown): HttpTaskForm {
  const raw = readObject(value);
  const httpParams = Array.isArray(raw.httpParams)
    ? raw.httpParams.flatMap((item): HttpProperty[] => {
        if (!item || typeof item !== "object" || Array.isArray(item)) return [];
        const property = item as Record<string, unknown>;
        return [{
          prop: readString(property.prop),
          httpParametersType: property.httpParametersType === "HEADERS" ? "HEADERS" : "PARAMETER",
          value: readString(property.value),
        }];
      })
    : [];

  return {
    url: readString(raw.url),
    httpMethod: isHttpRequestMethod(raw.httpMethod) ? raw.httpMethod : "GET",
    httpParams,
    httpBody: readString(raw.httpBody),
    httpCheckCondition: isHttpCheckCondition(raw.httpCheckCondition)
      ? raw.httpCheckCondition
      : "STATUS_CODE_DEFAULT",
    condition: readString(raw.condition),
    connectTimeout: raw.connectTimeout === null || raw.connectTimeout === undefined || raw.connectTimeout === ""
      ? String(DEFAULT_CONNECT_TIMEOUT)
      : String(raw.connectTimeout),
    resourceList: normalizeNodeResourceList(raw.resourceList),
    localParams: normalizeNodeLocalParams(raw.localParams),
  };
}

export function buildHttpTaskParams(
  form: HttpTaskForm,
  originalValue?: unknown,
): Record<string, unknown> {
  const original = readObject(originalValue);
  const timeout = Number(form.connectTimeout);

  return {
    ...original,
    url: form.url.trim(),
    httpMethod: form.httpMethod,
    httpParams: form.httpParams
      .filter((item) => item.prop.trim())
      .map((item) => ({
        prop: item.prop.trim(),
        httpParametersType: item.httpParametersType,
        value: item.value,
      })),
    httpBody: form.httpBody,
    httpCheckCondition: form.httpCheckCondition,
    condition: form.condition,
    connectTimeout: Number.isFinite(timeout) ? timeout : form.connectTimeout,
    resourceList: buildNodeResourceList(form.resourceList),
    localParams: form.localParams
      .filter((item) => item.prop.trim())
      .map((item) => ({
        ...item,
        prop: item.prop.trim(),
        value: item.value ?? "",
      })),
  };
}

export function validateHttpTaskForm(form: HttpTaskForm): string | undefined {
  if (!form.url.trim()) return "请输入请求 URL";

  const timeout = Number(form.connectTimeout);
  if (!Number.isSafeInteger(timeout) || timeout <= 0) {
    return "请求超时必须是大于 0 的整数，单位为毫秒";
  }

  const seenKeys = new Set<string>();
  for (const item of form.httpParams) {
    const key = item.prop.trim();
    if (!key) continue;
    const normalizedKey = item.httpParametersType === "HEADERS" ? key.toLowerCase() : key;
    const identity = item.httpParametersType + ":" + normalizedKey;
    if (seenKeys.has(identity)) {
      return (item.httpParametersType === "HEADERS" ? "请求头" : "查询参数") + "存在重复名称：" + key;
    }
    seenKeys.add(identity);
  }

  if (form.httpCheckCondition === "STATUS_CODE_CUSTOM") {
    const statusCode = Number(form.condition);
    if (!Number.isSafeInteger(statusCode) || statusCode <= 0) {
      return "请输入有效的 HTTP 状态码";
    }
  }

  if (
    (form.httpCheckCondition === "BODY_CONTAINS" || form.httpCheckCondition === "BODY_NOT_CONTAINS") &&
    !form.condition.trim()
  ) {
    return "请输入响应 Body 的匹配内容";
  }

  if ((form.httpMethod === "POST" || form.httpMethod === "PUT") && form.httpBody.trim()) {
    try {
      const body: unknown = JSON.parse(form.httpBody);
      if (!body || typeof body !== "object" || Array.isArray(body)) {
        return "请求 Body 必须是 JSON 对象";
      }
    } catch {
      return "请求 Body 不是有效的 JSON，请检查格式";
    }
  }

  return undefined;
}
