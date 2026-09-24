export type ApiProtocol = 'yak-ops' | 'security';

export const API_SUCCESS_CODE = 200;

export interface ApiResponse<T = unknown> {
  code: number;
  data: T;
  msg?: string;
  message?: string;
}

const protocolRules: Record<
  ApiProtocol,
  { success: readonly number[]; unauthenticated: readonly number[] }
> = {
  'yak-ops': {
    success: [API_SUCCESS_CODE],
    // Yak Security protects application endpoints too, so /api/v1/** may return
    // the framework USER_NOT_LOGIN business code even though the URL is not under
    // /yak-security/**.
    unauthenticated: [401, 2001],
  },
  security: {
    success: [API_SUCCESS_CODE],
    unauthenticated: [401, 2001],
  },
};

const unauthenticatedMessages = new Set([
  'NOT_LOGIN',
  'UNAUTHENTICATED',
  'SESSION_EXPIRED',
  'SESSION_INVALID',
  'TOKEN_EXPIRED',
  'TOKEN_INVALID',
  'LOGIN_EXPIRED',
]);

const MAX_ERROR_MESSAGE_LENGTH = 240;
const htmlDocumentPattern = /<(?:!doctype\s+html|html|head|body|title|style|script)\b/i;

const normalizeDisplayErrorText = (value: unknown): string | undefined => {
  if (typeof value !== 'string') return undefined;

  const text = value.trim().replace(/\s+/g, ' ');
  if (!text || htmlDocumentPattern.test(text)) return undefined;
  if (text.length <= MAX_ERROR_MESSAGE_LENGTH) return text;

  return `${text.slice(0, MAX_ERROR_MESSAGE_LENGTH - 1).trimEnd()}…`;
};

const normalizeUnauthenticatedMessage = (message: string) =>
  message.trim().toUpperCase().replace(/[\s-]+/g, '_');

export const protocolForUrl = (url?: string): ApiProtocol =>
  url?.includes('/yak-security/') ? 'security' : 'yak-ops';

export const isApiResponse = (value: unknown): value is ApiResponse => {
  if (!value || typeof value !== 'object') return false;
  return typeof (value as Partial<ApiResponse>).code === 'number';
};

export const isSuccessfulResponse = (
  response: Partial<ApiResponse> | null | undefined,
  protocol: ApiProtocol,
): boolean =>
  typeof response?.code === 'number' &&
  protocolRules[protocol].success.includes(response.code);

export const extractErrorMessage = (
  response: Partial<ApiResponse> | null | undefined,
  fallback = '操作失败',
): string =>
  normalizeDisplayErrorText(response?.msg) ||
  normalizeDisplayErrorText(response?.message) ||
  fallback;

/**
 * Extract a user-facing server error from both the standard API envelope and
 * common HTTP error payloads. HTML error pages and oversized diagnostics are
 * transport details, not UI copy, so they are filtered or shortened here.
 */
export const extractUnknownErrorMessage = (
  payload: unknown,
  fallback = '请求失败',
): string => {
  if (isApiResponse(payload)) {
    return extractErrorMessage(payload, fallback);
  }

  const directMessage = normalizeDisplayErrorText(payload);
  if (directMessage) return directMessage;

  if (payload && typeof payload === 'object') {
    const source = payload as Record<string, unknown>;
    for (const key of ['msg', 'message', 'error', 'detail']) {
      const message = normalizeDisplayErrorText(source[key]);
      if (message) return message;
    }
  }

  return fallback;
};

export const isUnauthenticatedResponse = (
  response: Partial<ApiResponse> | null | undefined,
  protocol: ApiProtocol,
): boolean => {
  const message = response?.msg || response?.message;
  return (
    (typeof response?.code === 'number' &&
      protocolRules[protocol].unauthenticated.includes(response.code)) ||
    (typeof message === 'string' &&
      unauthenticatedMessages.has(normalizeUnauthenticatedMessage(message)))
  );
};
