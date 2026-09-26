import { SECURITY_API_PREFIX } from "@/config/api";
import HttpUtils from "@/service/http/HttpUtils";

import type {
  UserBriefRecord,
  UserId,
  UserPageParams,
  UserPageResult,
  UserRecord,
  UserSavePayload,
} from "./types";

const USER_API_PREFIX = `${SECURITY_API_PREFIX}/user`;

const MANAGEMENT_REQUEST = {
  protocol: "security" as const,
  workspaceHeader: "omit" as const,
};

export const listUsers = (params: UserPageParams): Promise<UserPageResult> =>
  HttpUtils.postData<UserPageResult>(`${USER_API_PREFIX}/page`, params, MANAGEMENT_REQUEST);

export const getUsersByIds = (ids: readonly UserId[]): Promise<UserRecord[]> => {
  if (ids.length === 0) return Promise.resolve([]);
  const query = encodeURIComponent(JSON.stringify(ids));
  return HttpUtils.getData<UserRecord[]>(`${USER_API_PREFIX}?ids=${query}`, MANAGEMENT_REQUEST);
};

export const searchUsers = (keyword: string): Promise<UserBriefRecord[]> => {
  const normalized = keyword.trim();
  if (!normalized) return Promise.resolve([]);
  return HttpUtils.getData<UserBriefRecord[]>(
    `${USER_API_PREFIX}/list/${encodeURIComponent(normalized)}`,
    MANAGEMENT_REQUEST,
  );
};

export const createUser = async (payload: UserSavePayload): Promise<void> => {
  await HttpUtils.putData<void>(`${USER_API_PREFIX}/add`, payload, MANAGEMENT_REQUEST);
};

export const updateUser = async (payload: UserSavePayload): Promise<void> => {
  await HttpUtils.postData<void>(`${USER_API_PREFIX}/edit`, payload, MANAGEMENT_REQUEST);
};

export const deleteUser = async (id: UserId): Promise<void> => {
  await HttpUtils.deleteData<void>(`${USER_API_PREFIX}/${id}`, undefined, MANAGEMENT_REQUEST);
};

export type * from "./types";
