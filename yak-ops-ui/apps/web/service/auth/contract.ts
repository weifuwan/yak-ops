import type { AuthUser, AuthUserResponse, LoginCredentials } from "./types";

// The Java login controller uses @RequestParam, not a JSON @RequestBody.
export const encodeLoginCredentials = (credentials: LoginCredentials): URLSearchParams =>
  new URLSearchParams({
    userName: credentials.userName,
    userPassword: credentials.userPassword,
  });

// Keep the backend's numeric user ID in the transport type and normalize it at the UI boundary.
export const toAuthUser = (user: AuthUserResponse): AuthUser => ({
  ...user,
  id: String(user.id),
  userid: String(user.id),
  name: user.userName,
  email: user.email ?? undefined,
  phone: user.phone ?? undefined,
});
