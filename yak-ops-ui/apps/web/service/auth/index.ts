import { SECURITY_API_PREFIX } from "@/config/api";
import HttpUtils from "@/service/http/HttpUtils";

import type {
  AuthUser,
  AuthUserResponse,
  GetCurrentUserOptions,
  LoginCredentials,
} from "./types";

const ACCOUNT_API = `${SECURITY_API_PREFIX}/account`;

const toAuthUser = (user: AuthUserResponse): AuthUser => ({
  ...user,
  name: user.realName?.trim() || user.userName,
  userid: String(user.id),
  email: user.email ?? undefined,
  phone: user.phone ?? undefined,
  deptId: user.deptId ?? null,
});

export const login = async (credentials: LoginCredentials): Promise<void> => {
  await HttpUtils.postData<void>(`${ACCOUNT_API}/login`, credentials, {
    protocol: "security",
  });
};

export const getCurrentUser = async (
  options?: GetCurrentUserOptions,
): Promise<AuthUser> => {
  const user = await HttpUtils.getData<AuthUserResponse>(
    `${ACCOUNT_API}/current`,
    {
      protocol: "security",
      skipErrorHandler: options?.skipErrorHandler,
    },
  );
  return toAuthUser(user);
};

export const logout = async (): Promise<void> => {
  await HttpUtils.postData<void>(`${ACCOUNT_API}/logout`, undefined, {
    protocol: "security",
  });
};

export type {
  AuthUser,
  AuthUserResponse,
  GetCurrentUserOptions,
  LoginCredentials,
} from "./types";
