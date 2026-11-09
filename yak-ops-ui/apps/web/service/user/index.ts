import { YAK_OPS_API_PREFIX } from "@/config/api";
import HttpUtils from "@/service/http/HttpUtils";

import type {
  QueueOption,
  TenantOption,
  UserId,
  UserPageParams,
  UserPageResult,
  UserRecord,
  UserSavePayload,
} from "./types";

const USER_API = `${YAK_OPS_API_PREFIX}/users`;
const TENANT_API = `${YAK_OPS_API_PREFIX}/tenants`;
const QUEUE_API = `${YAK_OPS_API_PREFIX}/queues`;

const queryString = (values: Record<string, string | number | undefined>) => {
  const query = new URLSearchParams();
  Object.entries(values).forEach(([key, value]) => {
    if (value !== undefined) query.set(key, String(value));
  });
  return query.toString();
};

export const listUsers = async (params: UserPageParams): Promise<UserPageResult> => {
  const query = queryString(params);
  const page = await HttpUtils.getData<{
    totalList: Array<Omit<UserRecord, "id"> & { id: string | number }>;
    total: number;
    currentPage: number;
    pageSize: number;
    totalPage: number;
  }>(`${USER_API}/list-paging?${query}`);

  return {
    records: page.totalList.map((user) => ({ ...user, id: String(user.id) })),
    total: page.total,
    pageNo: page.currentPage,
    pageSize: page.pageSize,
    totalPage: page.totalPage,
  };
};

export const getUsersByIds = async (ids: readonly UserId[]): Promise<UserRecord[]> => {
  if (ids.length === 0) return [];
  const users = await HttpUtils.getData<Array<Omit<UserRecord, "id"> & { id: string | number }>>(
    `${USER_API}/by-ids?${queryString({ ids: ids.join(",") })}`,
  );
  return users.map((user) => ({ ...user, id: String(user.id) }));
};

export const listTenants = (): Promise<TenantOption[]> =>
  HttpUtils.getData<TenantOption[]>(`${TENANT_API}/list`);

export const listQueues = (): Promise<QueueOption[]> =>
  HttpUtils.getData<QueueOption[]>(`${QUEUE_API}/list`);

export const createUser = async (payload: UserSavePayload): Promise<UserRecord> => {
  const { userName, userPassword, tenantId, queue, email, phone, state } = payload;
  return HttpUtils.postData<UserRecord>(
    `${USER_API}/create?${queryString({ userName, userPassword, tenantId, queue, email, phone, state })}`,
  );
};

export const updateUser = async (id: UserId, payload: UserSavePayload): Promise<UserRecord> => {
  const { userName, userPassword, tenantId, queue, email, phone, state, timeZone } = payload;
  return HttpUtils.postData<UserRecord>(
    `${USER_API}/update?${queryString({ id, userName, userPassword, tenantId, queue, email, phone, state, timeZone })}`,
  );
};

export const deleteUser = async (id: UserId): Promise<void> => {
  await HttpUtils.postData<void>(`${USER_API}/delete?${queryString({ id })}`);
};

export type * from "./types";
