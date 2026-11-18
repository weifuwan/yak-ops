import { YAK_OPS_API_PREFIX } from "@/config/api";
import HttpUtils from "@/service/http/HttpUtils";
import type { PageInfo } from "@/service/http/page-info";

const WORKER_GROUP_API = YAK_OPS_API_PREFIX + "/worker-groups";

export type WorkerGroupSource = "CONFIG" | "UI" | number | string;

export interface WorkerGroupRecord {
  id: number;
  name: string;
  addrList?: string | null;
  description?: string | null;
  createTime?: string;
  updateTime?: string;
  source?: WorkerGroupSource;
  systemDefault?: boolean;
}

export interface WorkerGroupPageParams {
  pageNo: number;
  pageSize: number;
  searchVal?: string;
}

export interface WorkerGroupSavePayload {
  name: string;
  addrList: string;
  description: string;
}

const toQuery = (values: object) => {
  const params = new URLSearchParams();
  Object.entries(values as Record<string, string | number | undefined>).forEach(([key, value]) => {
    if (value !== undefined) params.set(key, String(value));
  });
  return params.toString();
};

const postParams = (values: Record<string, string | number>) =>
  new URLSearchParams(
    Object.entries(values).map(([key, value]) => [key, String(value)]),
  );

export const listWorkerGroups = (
  params: WorkerGroupPageParams,
  signal?: AbortSignal,
): Promise<PageInfo<WorkerGroupRecord>> =>
  HttpUtils.getData<PageInfo<WorkerGroupRecord>>(
    WORKER_GROUP_API + "?" + toQuery(params),
    { signal },
  );

export const listWorkerGroupNames = (signal?: AbortSignal): Promise<string[]> =>
  HttpUtils.getData<string[]>(WORKER_GROUP_API + "/all", { signal });

export const listWorkerAddresses = (signal?: AbortSignal): Promise<string[]> =>
  HttpUtils.getData<string[]>(WORKER_GROUP_API + "/worker-address-list", { signal });

export const saveWorkerGroup = (
  id: number,
  payload: WorkerGroupSavePayload,
): Promise<WorkerGroupRecord> =>
  HttpUtils.postData<WorkerGroupRecord>(WORKER_GROUP_API, undefined, {
    body: postParams({
      id,
      name: payload.name,
      addrList: payload.addrList,
      description: payload.description,
    }),
  });

export const deleteWorkerGroup = (id: number): Promise<void> =>
  HttpUtils.deleteData<void>(WORKER_GROUP_API + "/" + id);
