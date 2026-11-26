import { YAK_OPS_API_PREFIX } from "@/config/api";
import HttpUtils from "@/service/http/HttpUtils";
import type { PageInfo } from "@/service/http/page-info";

const ENVIRONMENT_API = YAK_OPS_API_PREFIX + "/environment";

export interface EnvironmentRecord {
  id: number;
  code: number | string;
  name: string;
  config: string;
  description?: string | null;
  workerGroups: string[];
  operator?: number;
  createTime?: string;
  updateTime?: string;
}

export interface EnvironmentPageParams {
  pageNo: number;
  pageSize: number;
  searchVal?: string;
}

export interface EnvironmentSavePayload {
  name: string;
  config: string;
  description: string;
  workerGroups: string[];
}

const toQuery = (values: object) => {
  const params = new URLSearchParams();
  Object.entries(values as Record<string, string | number | undefined>).forEach(([key, value]) => {
    if (value !== undefined) params.set(key, String(value));
  });
  return params.toString();
};

const postParams = (values: Record<string, string>) =>
  new URLSearchParams(Object.entries(values));

const workerGroupsJson = (workerGroups: string[]) => JSON.stringify(workerGroups);

export const listEnvironments = (
  params: EnvironmentPageParams,
  signal?: AbortSignal,
): Promise<PageInfo<EnvironmentRecord>> =>
  HttpUtils.getData<PageInfo<EnvironmentRecord>>(
    ENVIRONMENT_API + "/list-paging?" + toQuery(params),
    { signal },
  );

export const listAllEnvironments = (
  signal?: AbortSignal,
): Promise<EnvironmentRecord[]> =>
  HttpUtils.getData<EnvironmentRecord[]>(
    ENVIRONMENT_API + "/query-environment-list",
    { signal },
  );

export const queryEnvironmentByCode = (
  code: number | string,
  signal?: AbortSignal,
): Promise<EnvironmentRecord> =>
  HttpUtils.getData<EnvironmentRecord>(
    ENVIRONMENT_API + "/query-by-code?environmentCode=" + encodeURIComponent(String(code)),
    { signal },
  );

export const createEnvironment = (
  payload: EnvironmentSavePayload,
): Promise<number | string> =>
  HttpUtils.postData<number | string>(ENVIRONMENT_API + "/create", undefined, {
    body: postParams({
      name: payload.name,
      config: payload.config,
      description: payload.description,
      workerGroups: workerGroupsJson(payload.workerGroups),
    }),
  });

export const updateEnvironment = (
  code: number | string,
  payload: EnvironmentSavePayload,
): Promise<EnvironmentRecord> =>
  HttpUtils.postData<EnvironmentRecord>(ENVIRONMENT_API + "/update", undefined, {
    body: postParams({
      code: String(code),
      name: payload.name,
      config: payload.config,
      description: payload.description,
      workerGroups: workerGroupsJson(payload.workerGroups),
    }),
  });

export const deleteEnvironment = (code: number | string): Promise<void> =>
  HttpUtils.postData<void>(ENVIRONMENT_API + "/delete", undefined, {
    body: postParams({ environmentCode: String(code) }),
  });
