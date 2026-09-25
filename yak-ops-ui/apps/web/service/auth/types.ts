export interface AuthUserResponse {
  id: string;
  userName: string;
  realName?: string | null;
  deptId?: number | null;
  phone?: string | null;
  email?: string | null;
}

export interface AuthUser extends AuthUserResponse {
  name: string;
  userid: string;
}

export interface LoginCredentials {
  userName: string;
  pw: string;
}

export interface GetCurrentUserOptions {
  skipErrorHandler?: boolean;
}
