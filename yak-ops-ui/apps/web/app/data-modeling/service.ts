import { YAK_OPS_API_PREFIX } from "@/config/api";
import HttpUtils from "@/service/http/HttpUtils";

export interface ModelingSection {
  code: string;
  name: string;
}

export interface ModelingBootstrap {
  projectCode: number;
  moduleCode: string;
  moduleName: string;
  sections: ModelingSection[];
}

export interface ModelingPageResult<T> {
  bizData: T[];
  pagination: { total: number; current: number; pageSize: number };
}

export type ModelingResourceKey =
  | "businessType"
  | "dwLayer"
  | "dataDomain"
  | "businessProcess"
  | "dataMart"
  | "subjectDomain";

export interface ModelingRecord {
  id?: string;
  createTime?: string;
  updateTime?: string;
  [key: string]: unknown;
}

const RESOURCE_PATH: Record<ModelingResourceKey, string> = {
  businessType: "business-type",
  dwLayer: "dw-layer",
  dataDomain: "data-domain",
  businessProcess: "business-process",
  dataMart: "data-mart",
  subjectDomain: "subject-domain",
};

function modelingPath(projectCode: string, resource?: ModelingResourceKey): string {
  const base = `${YAK_OPS_API_PREFIX}/projects/${encodeURIComponent(projectCode)}/modeling`;
  return resource ? `${base}/${RESOURCE_PATH[resource]}` : base;
}

export function getModelingBootstrap(projectCode: string): Promise<ModelingBootstrap> {
  return HttpUtils.getData<ModelingBootstrap>(`${modelingPath(projectCode)}/bootstrap`);
}

export const modelingEntityApi = {
  page<T extends ModelingRecord>(
    projectCode: string,
    resource: ModelingResourceKey,
    params: Record<string, unknown>,
  ): Promise<ModelingPageResult<T>> {
    return HttpUtils.postData<ModelingPageResult<T>>(
      `${modelingPath(projectCode, resource)}/page`,
      params,
    );
  },

  all<T extends ModelingRecord>(
    projectCode: string,
    resource: ModelingResourceKey,
  ): Promise<T[]> {
    const url = `${modelingPath(projectCode, resource)}/all`;
    return resource === "subjectDomain"
      ? HttpUtils.postData<T[]>(url, {})
      : HttpUtils.getData<T[]>(url);
  },

  getById<T extends ModelingRecord>(
    projectCode: string,
    resource: ModelingResourceKey,
    id: string,
  ): Promise<T> {
    return HttpUtils.getData<T>(
      `${modelingPath(projectCode, resource)}/${encodeURIComponent(id)}`,
    );
  },

  create(
    projectCode: string,
    resource: ModelingResourceKey,
    payload: Record<string, unknown>,
  ): Promise<string> {
    return HttpUtils.postData<string>(modelingPath(projectCode, resource), payload);
  },

  update<T extends ModelingRecord>(
    projectCode: string,
    resource: ModelingResourceKey,
    payload: Record<string, unknown>,
  ): Promise<T> {
    return HttpUtils.putData<T>(modelingPath(projectCode, resource), payload);
  },

  remove(projectCode: string, resource: ModelingResourceKey, id: string): Promise<string> {
    return HttpUtils.deleteData<string>(
      `${modelingPath(projectCode, resource)}/${encodeURIComponent(id)}`,
    );
  },
};
