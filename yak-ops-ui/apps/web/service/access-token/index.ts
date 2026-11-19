import { YAK_OPS_API_PREFIX } from "@/config/api";
import HttpUtils from "@/service/http/HttpUtils";

import type {
  AccessTokenPageParams,
  AccessTokenPageResult,
  AccessTokenRecord,
  AccessTokenSavePayload,
} from "./types";

const ACCESS_TOKEN_API = YAK_OPS_API_PREFIX + "/access-tokens";

const queryString = (values: Record<string, string | number | undefined>) => {
  const query = new URLSearchParams();
  Object.entries(values).forEach(([key, value]) => {
    if (value !== undefined) query.set(key, String(value));
  });
  return query.toString();
};

const normalizeAccessToken = (
  record: Omit<AccessTokenRecord, "id" | "userId"> & {
    id: string | number;
    userId: string | number;
  },
): AccessTokenRecord => ({
  ...record,
  id: String(record.id),
  userId: String(record.userId),
});

export const listAccessTokens = async (
  params: AccessTokenPageParams,
): Promise<AccessTokenPageResult> => {
  const page = await HttpUtils.getData<{
    totalList: Array<Omit<AccessTokenRecord, "id" | "userId"> & {
      id: string | number;
      userId: string | number;
    }>;
    total: number;
    currentPage: number;
    pageSize: number;
    totalPage: number;
  }>(
    ACCESS_TOKEN_API +
      "?" +
      queryString({
        pageNo: params.pageNo,
        pageSize: params.pageSize,
        searchVal: params.searchVal,
      }),
  );

  return {
    records: page.totalList.map(normalizeAccessToken),
    total: page.total,
    pageNo: page.currentPage,
    pageSize: page.pageSize,
    totalPage: page.totalPage,
  };
};

export const createAccessToken = async (
  payload: AccessTokenSavePayload,
): Promise<AccessTokenRecord> => {
  // Omitting token asks the existing backend endpoint to generate and persist it.
  const record = await HttpUtils.postData<
    Omit<AccessTokenRecord, "id" | "userId"> & {
      id: string | number;
      userId: string | number;
    }
  >(ACCESS_TOKEN_API + "?" + queryString(payload));
  return normalizeAccessToken(record);
};

export const updateAccessToken = async (
  id: string,
  payload: AccessTokenSavePayload,
): Promise<AccessTokenRecord> => {
  // The backend generates a replacement token when the optional token parameter is omitted.
  const record = await HttpUtils.putData<
    Omit<AccessTokenRecord, "id" | "userId"> & {
      id: string | number;
      userId: string | number;
    }
  >(ACCESS_TOKEN_API + "/" + encodeURIComponent(id) + "?" + queryString(payload));
  return normalizeAccessToken(record);
};

export const deleteAccessToken = (id: string): Promise<boolean> =>
  HttpUtils.deleteData<boolean>(ACCESS_TOKEN_API + "/" + encodeURIComponent(id));

export type * from "./types";
