export interface AuthUserResponse {
  id: number;
  userName: string;
  email?: string | null;
  phone?: string | null;
  userType: "ADMIN_USER" | "GENERAL_USER";
  tenantId: number;
  state: number;
  timeZone?: string | null;
}

export interface AuthUser extends Omit<AuthUserResponse, "id"> {
  id: string;
  name: string;
  userid: string;
}

export interface LoginCredentials {
  userName: string;
  userPassword: string;
}

export interface GetCurrentUserOptions {
  skipErrorHandler?: boolean;
}
