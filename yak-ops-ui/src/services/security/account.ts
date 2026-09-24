import {
  securityGetData,
  securityPostData,
  type SecurityRequestOptions,
} from "./client";

const ACCOUNT_API = "/api/v1/account";

export interface CurrentUserVO {
  id: number;
  userName: string;
  realName?: string | null;
  deptId?: number | null;
  phone?: string | null;
  email?: string | null;
}

export interface CurrentUser extends CurrentUserVO {
  name: string;
  userid: string;
}

export type AccountLoginDTO = {
  userName: string;
  pw: string;
};

export const login = (body: AccountLoginDTO): Promise<void> =>
  securityPostData<void>(`${ACCOUNT_API}/login`, body);

export const getCurrentUser = (
  options?: SecurityRequestOptions,
): Promise<CurrentUserVO> =>
  securityGetData<CurrentUserVO>(`${ACCOUNT_API}/current`, options);

export const logout = (): Promise<void> =>
  securityPostData<void>(`${ACCOUNT_API}/logout`);
