export type UserId = string;

export interface UserRecord {
  id: UserId;
  userName: string;
  realName?: string | null;
  phone?: string | null;
  email?: string | null;
  createTime?: string;
  updateTime?: string;
}

export interface UserPageResult {
  bizData: UserRecord[];
  pagination: {
    pageNo: number;
    pageSize: number;
    total: number;
    pages?: number;
  };
}

export interface UserPageParams {
  pageNo: number;
  pageSize: number;
  userName?: string;
  realName?: string;
}

export interface UserSavePayload {
  userName: string;
  pw?: string;
  realName?: string;
  phone?: string;
  email?: string;
}
