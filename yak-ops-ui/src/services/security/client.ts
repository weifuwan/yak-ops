import type { ApiResponse } from "@/services/http/response";
import request from "@/utils/request";

const SECURITY_NAMESPACE = "/yak-security";

export type SecurityRequestOptions = RequestInit & {
  skipErrorHandler?: boolean;
  data?: unknown;
};

export const securityRequest = <T>(
  path: string,
  options: SecurityRequestOptions = {},
): Promise<ApiResponse<T>> =>
  request<ApiResponse<T>>(`${SECURITY_NAMESPACE}${path}`, {
    ...options,
    credentials: "include",
    protocol: "security",
  });

export const securityGetData = async <T>(
  path: string,
  options?: SecurityRequestOptions,
): Promise<T> =>
  (await securityRequest<T>(path, { ...options, method: "GET" })).data;

export const securityPostData = async <T>(
  path: string,
  data?: unknown,
  options?: SecurityRequestOptions,
): Promise<T> =>
  (
    await securityRequest<T>(path, {
      ...options,
      method: "POST",
      data,
    })
  ).data;
