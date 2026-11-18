import { YAK_OPS_API_PREFIX } from "@/config/api";
import HttpUtils from "@/service/http/HttpUtils";
import type { PageInfo } from "@/service/http/page-info";

const API = YAK_OPS_API_PREFIX + "/projects/audit";

export interface AuditTypeOption {
  name: string;
  child?: AuditTypeOption[] | null;
}

export interface AuditRecord {
  userName?: string | null;
  modelType?: string | null;
  modelName?: string | null;
  operation?: string | null;
  createTime?: string | null;
  description?: string | null;
  detail?: string | null;
  latency?: string | null;
}

export interface AuditQuery {
  pageNo: number;
  pageSize: number;
  modelTypes?: string;
  operationTypes?: string;
  startDate?: string;
  endDate?: string;
  userName?: string;
  modelName?: string;
}

const qs = (params: object) => {
  const search = new URLSearchParams();
  Object.entries(params as Record<string, string | number | undefined>).forEach(([key, value]) => {
    if (value !== undefined && value !== "") search.set(key, String(value));
  });
  return search.toString();
};

export const listAuditLogs = (
  params: AuditQuery,
  signal?: AbortSignal,
): Promise<PageInfo<AuditRecord>> =>
  HttpUtils.getData<PageInfo<AuditRecord>>(
    API + "/audit-log-list?" + qs(params),
    { signal },
  );

export const listAuditModelTypes = (
  signal?: AbortSignal,
): Promise<AuditTypeOption[]> =>
  HttpUtils.getData<AuditTypeOption[]>(
    API + "/audit-log-model-type",
    { signal },
  );

export const listAuditOperationTypes = (
  signal?: AbortSignal,
): Promise<AuditTypeOption[]> =>
  HttpUtils.getData<AuditTypeOption[]>(
    API + "/audit-log-operation-type",
    { signal },
  );
