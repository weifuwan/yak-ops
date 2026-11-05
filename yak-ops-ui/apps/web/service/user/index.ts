import { API_PREFIX } from "@/config/api";
import HttpUtils from "@/service/http/HttpUtils";

import type {
  UserBriefRecord,
  UserId,
  UserPageParams,
  UserPageResult,
  UserRecord,
  UserSavePayload,
} from "./types";

const USER_API_PREFIX = `${API_PREFIX}/user`;

export const listUsers = (params: UserPageParams): Promise<UserPageResult> =>
  HttpUtils.postData<UserPageResult>(`${USER_API_PREFIX}/page`, params);

export const getUsersByIds = (ids: readonly UserId[]): Promise<UserRecord[]> => {
  if (ids.length === 0) return Promise.resolve([]);
  const query = encodeURIComponent(JSON.stringify(ids));
  return HttpUtils.getData<UserRecord[]>(`${USER_API_PREFIX}?ids=${query}`);
};

export const searchUsers = (keyword: string): Promise<UserBriefRecord[]> => {
  const normalized = keyword.trim();
  if (!normalized) return Promise.resolve([]);
  return HttpUtils.getData<UserBriefRecord[]>(
    `${USER_API_PREFIX}/list/${encodeURIComponent(normalized)}`,
  );
};

export const createUser = async (payload: UserSavePayload): Promise<void> => {
  await HttpUtils.putData<void>(`${USER_API_PREFIX}/add`, payload);
};

export const updateUser = async (payload: UserSavePayload): Promise<void> => {
  await HttpUtils.postData<void>(`${USER_API_PREFIX}/edit`, payload);
};

export const deleteUser = async (id: UserId): Promise<void> => {
  await HttpUtils.deleteData<void>(`${USER_API_PREFIX}/${id}`, undefined);
};

export type * from "./types";
