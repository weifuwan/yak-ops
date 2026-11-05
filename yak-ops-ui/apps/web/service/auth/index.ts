import { YAK_OPS_API_PREFIX } from "@/config/api";
import HttpUtils from "@/service/http/HttpUtils";

import { encodeLoginCredentials, toAuthUser } from "./contract";
import type { AuthUser, AuthUserResponse, GetCurrentUserOptions, LoginCredentials } from "./types";

export const login = async (credentials: LoginCredentials): Promise<void> => {
  await HttpUtils.postData<void>(`${YAK_OPS_API_PREFIX}/login`, undefined, {
    body: encodeLoginCredentials(credentials),
  });
};

export const getCurrentUser = async (options?: GetCurrentUserOptions): Promise<AuthUser> => {
  const user = await HttpUtils.getData<AuthUserResponse>(`${YAK_OPS_API_PREFIX}/users/get-user-info`, {
    skipErrorHandler: options?.skipErrorHandler,
  });
  return toAuthUser(user);
};

export const logout = async (): Promise<void> => {
  await HttpUtils.postData<void>(`${YAK_OPS_API_PREFIX}/signOut`, undefined, {
  });
  // The server invalidates the DB session first. Cookie cleanup is best-effort.
  try {
    await HttpUtils.delete<void>(`${YAK_OPS_API_PREFIX}/cookies`, undefined, {
      skipErrorHandler: true,
    });
  } catch {
    // The authenticated session was already revoked by /signOut.
  }
};

export type { AuthUser, AuthUserResponse, GetCurrentUserOptions, LoginCredentials } from "./types";
