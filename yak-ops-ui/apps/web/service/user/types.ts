export type UserId = string;

export interface UserRecord {
  id: UserId;
  userName: string;
  email?: string | null;
  phone?: string | null;
  userType?: "ADMIN_USER" | "GENERAL_USER";
  tenantId?: number;
  state?: number;
  queue?: string | null;
  timeZone?: string | null;
  createTime?: string;
  updateTime?: string;
}

export interface UserBriefRecord {
  id: UserId;
  userName: string;
  email?: string | null;
  phone?: string | null;
}

export interface UserPageData {
  totalList: UserRecord[];
  total: number;
  currentPage: number;
  pageSize: number;
  totalPage: number;
  pageNo: number;
}

export interface UserPageResult {
  records: UserRecord[];
  total: number;
  pageNo: number;
  pageSize: number;
  totalPage: number;
}

export interface UserPageParams {
  pageNo: number;
  pageSize: number;
  searchVal?: string;
}

export interface UserSavePayload {
  userName: string;
  userPassword: string;
  tenantId: number;
  queue: string;
  email: string;
  phone?: string;
  state: number;
  timeZone?: string;
}

export interface TenantOption {
  id: number;
  tenantCode: string;
  description?: string | null;
  queueId: number;
  queueName?: string | null;
  queue?: string | null;
}

export interface QueueOption {
  id: number;
  queueName: string;
  queue: string;
}
