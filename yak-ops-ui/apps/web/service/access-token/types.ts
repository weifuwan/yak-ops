export interface AccessTokenRecord {
  id: string;
  userId: string;
  userName?: string | null;
  token: string;
  expireTime: string;
  createTime?: string | null;
  updateTime?: string | null;
}

export interface AccessTokenPageParams {
  pageNo: number;
  pageSize: number;
  searchVal?: string;
}

export interface AccessTokenPageResult {
  records: AccessTokenRecord[];
  total: number;
  pageNo: number;
  pageSize: number;
  totalPage: number;
}

export interface AccessTokenSavePayload {
  userId: string;
  expireTime: string;
}
