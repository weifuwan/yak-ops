import { YAK_OPS_API_PREFIX } from "@/config/api";
import HttpUtils from "@/service/http/HttpUtils";

export interface TenantRecord {
  id: number;
  tenantCode: string;
  description?: string | null;
  queueId: number;
  queueName?: string | null;
  queue?: string | null;
  createTime?: string | null;
  updateTime?: string | null;
}

export interface TenantPageParams {
  pageNo: number;
  pageSize: number;
  searchVal?: string;
}

export interface TenantPageResult {
  records: TenantRecord[];
  total: number;
  pageNo: number;
  pageSize: number;
  totalPage: number;
}

export interface TenantSavePayload {
  tenantCode: string;
  queueId: number;
  description?: string;
}

const TENANT_API = YAK_OPS_API_PREFIX + "/tenants";

const queryString = (values: Record<string, string | number | undefined>) => {
  const query = new URLSearchParams();
  Object.entries(values).forEach(([key, value]) => {
    if (value !== undefined) query.set(key, String(value));
  });
  return query.toString();
};

export const listTenantsPage = async (params: TenantPageParams): Promise<TenantPageResult> => {
  const query = queryString({ ...params });
  const page = await HttpUtils.getData<{
    totalList: Array<Omit<TenantRecord, "id"> & { id: string | number }>;
    total: number;
    currentPage: number;
    pageSize: number;
    totalPage: number;
  }>(TENANT_API + "?" + query);

  return {
    records: page.totalList.map((tenant) => ({ ...tenant, id: Number(tenant.id) })),
    total: page.total,
    pageNo: page.currentPage,
    pageSize: page.pageSize,
    totalPage: page.totalPage,
  };
};

export const verifyTenantCode = (tenantCode: string): Promise<boolean> =>
  HttpUtils.getData<boolean>(TENANT_API + "/verify-code?" + queryString({ tenantCode }));

export const createTenant = (payload: TenantSavePayload): Promise<TenantRecord> =>
  HttpUtils.postData<TenantRecord>(
    TENANT_API +
      "?" +
      queryString({
        tenantCode: payload.tenantCode,
        queueId: payload.queueId,
        description: payload.description,
      }),
  );

export const updateTenant = (id: number, payload: TenantSavePayload): Promise<boolean> =>
  HttpUtils.putData<boolean>(
    TENANT_API +
      "/" +
      id +
      "?" +
      queryString({
        tenantCode: payload.tenantCode,
        queueId: payload.queueId,
        description: payload.description,
      }),
  );

export const deleteTenant = (id: number): Promise<boolean> =>
  HttpUtils.deleteData<boolean>(TENANT_API + "/" + id);
