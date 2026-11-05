export const API_SUCCESS_CODE = 0;

export interface ApiResponse<T = unknown> {
  code: number;
  data: T;
  msg?: string;
  message?: string;
}

const unauthenticatedMessages = new Set([
  "NOT_LOGIN",
  "UNAUTHENTICATED",
  "SESSION_EXPIRED",
  "SESSION_INVALID",
  "TOKEN_EXPIRED",
  "TOKEN_INVALID",
  "LOGIN_EXPIRED",
]);

const MAX_ERROR_MESSAGE_LENGTH = 240;
const htmlDocumentPattern = /<(?:!doctype\s+html|html|head|body|title|style|script)\b/i;

const normalizeDisplayErrorText = (value: unknown): string | undefined => {
  if (typeof value !== "string") return undefined;

  const text = value.trim().replace(/\s+/g, " ");
  if (!text || htmlDocumentPattern.test(text)) return undefined;
  if (text.length <= MAX_ERROR_MESSAGE_LENGTH) return text;

  return `${text.slice(0, MAX_ERROR_MESSAGE_LENGTH - 1).trimEnd()}…`;
};

const normalizeUnauthenticatedMessage = (message: string) =>
  message
    .trim()
    .toUpperCase()
    .replace(/[\s-]+/g, "_");

export const isApiResponse = (value: unknown): value is ApiResponse => {
  if (!value || typeof value !== "object") return false;
  return typeof (value as Partial<ApiResponse>).code === "number";
};

export function isSuccessfulResponse(response: Partial<ApiResponse> | null | undefined): boolean {
  return response?.code === API_SUCCESS_CODE;
}

export const extractErrorMessage = (
  response: Partial<ApiResponse> | null | undefined,
  fallback = "操作失败",
): string =>
  normalizeDisplayErrorText(response?.msg) ||
  normalizeDisplayErrorText(response?.message) ||
  fallback;

/**
 * Extract a user-facing server error from both the standard API envelope and
 * common HTTP error payloads. HTML error pages and oversized diagnostics are
 * transport details, not UI copy, so they are filtered or shortened here.
 */
export const extractUnknownErrorMessage = (payload: unknown, fallback = "请求失败"): string => {
  if (isApiResponse(payload)) {
    return extractErrorMessage(payload, fallback);
  }

  const directMessage = normalizeDisplayErrorText(payload);
  if (directMessage) return directMessage;

  if (payload && typeof payload === "object") {
    const source = payload as Record<string, unknown>;
    for (const key of ["msg", "message", "error", "detail"]) {
      const message = normalizeDisplayErrorText(source[key]);
      if (message) return message;
    }
  }

  return fallback;
};

export function isUnauthenticatedResponse(
  response: Partial<ApiResponse> | null | undefined,
): boolean {
  if (response?.code === 401 || response?.code === 2001) {
    return true;
  }
  const message = response?.msg || response?.message;
  return (
    typeof message === "string" &&
    unauthenticatedMessages.has(normalizeUnauthenticatedMessage(message))
  );
}
