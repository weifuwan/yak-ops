import { YAK_OPS_API_PREFIX } from "@/config/api";
import HttpUtils from "@/service/http/HttpUtils";
import type { PageInfo } from "@/service/http/page-info";

import type { ModelingRecord, ModelingVersionRecord } from "./types";

type ModelingResourceKey =
  | "businessType"
  | "dwLayer"
  | "dataDomain"
  | "businessProcess"
  | "dataMart"
  | "subjectDomain"
  | "standardDirectory"
  | "fieldStandard"
  | "codeStandard"
  | "unitStandard"
  | "dictionaryStandard"
  | "atomIndicator"
  | "derivativeIndicator"
  | "composeIndicator"
  | "modifier"
  | "timePeriodCategory"
  | "timePeriod";

const RESOURCE_PATH: Record<ModelingResourceKey, string> = {
  businessType: "business-type",
  dwLayer: "dw-layer",
  dataDomain: "data-domain",
  businessProcess: "business-process",
  dataMart: "data-mart",
  subjectDomain: "subject-domain",
  standardDirectory: "standard-directories",
  fieldStandard: "field-standards",
  codeStandard: "code-standards",
  unitStandard: "measurement-units",
  dictionaryStandard: "naming-dictionaries",
  atomIndicator: "atom-indicators",
  derivativeIndicator: "derived-indicators",
  composeIndicator: "composite-indicators",
  modifier: "modifiers",
  timePeriodCategory: "time-periods/categories",
  timePeriod: "time-periods",
};

export function modelingBasePath(projectCode: string): string {
  return `${YAK_OPS_API_PREFIX}/projects/${encodeURIComponent(projectCode)}/modeling`;
}

export function modelingResourcePath(projectCode: string, resource: ModelingResourceKey): string {
  return `${modelingBasePath(projectCode)}/${RESOURCE_PATH[resource]}`;
}

export interface ModelingResourceApi {
  page<T extends ModelingRecord>(projectCode: string, params: Record<string, unknown>): Promise<PageInfo<T>>;
  all<T extends ModelingRecord>(projectCode: string, params?: Record<string, string>): Promise<T[]>;
  getById<T extends ModelingRecord>(projectCode: string, id: string): Promise<T>;
  create(projectCode: string, payload: Record<string, unknown>): Promise<string>;
  update<T extends ModelingRecord>(projectCode: string, payload: Record<string, unknown>): Promise<T>;
  remove(projectCode: string, id: string): Promise<string>;
  submit(projectCode: string, id: string): Promise<ModelingRecord>;
  versions(projectCode: string, id: string): Promise<ModelingVersionRecord[]>;
  rollback(projectCode: string, id: string, versionNumber: number): Promise<ModelingRecord>;
}

type ModelingResourceApiOptions = {
  allMethod?: "GET" | "POST";
  listEndpoint?: "all" | "list";
  updateWithId?: boolean;
  rollbackWithQuery?: boolean;
};

export function createModelingResourceApi(
  resource: ModelingResourceKey,
  options: ModelingResourceApiOptions = {},
): ModelingResourceApi {
  const path = (projectCode: string) => modelingResourcePath(projectCode, resource);

  return {
    page<T extends ModelingRecord>(projectCode: string, params: Record<string, unknown>) {
      return HttpUtils.postData<PageInfo<T>>(`${path(projectCode)}/page`, params);
    },

    all<T extends ModelingRecord>(projectCode: string, params: Record<string, string> = {}) {
      const query = new URLSearchParams(params).toString();
      const endpoint = options.listEndpoint || "all";
      const url = `${path(projectCode)}/${endpoint}${query ? `?${query}` : ""}`;
      return options.allMethod === "POST"
        ? HttpUtils.postData<T[]>(url, {})
        : HttpUtils.getData<T[]>(url);
    },

    getById<T extends ModelingRecord>(projectCode: string, id: string) {
      return HttpUtils.getData<T>(`${path(projectCode)}/${encodeURIComponent(id)}`);
    },

    create(projectCode: string, payload: Record<string, unknown>) {
      return HttpUtils.postData<string>(path(projectCode), payload);
    },

    update<T extends ModelingRecord>(projectCode: string, payload: Record<string, unknown>) {
      const id = payload.id;
      if (options.updateWithId && (id === undefined || id === null || !String(id).trim())) {
        throw new Error("更新建模资源时必须提供 id");
      }
      const url = options.updateWithId
        ? `${path(projectCode)}/${encodeURIComponent(String(id))}`
        : path(projectCode);
      return HttpUtils.putData<T>(url, payload);
    },

    remove(projectCode: string, id: string) {
      return HttpUtils.deleteData<string>(`${path(projectCode)}/${encodeURIComponent(id)}`);
    },

    submit(projectCode: string, id: string) {
      return HttpUtils.postData<ModelingRecord>(`${path(projectCode)}/${encodeURIComponent(id)}/submit`, {});
    },

    versions(projectCode: string, id: string) {
      return HttpUtils.getData<ModelingVersionRecord[]>(`${path(projectCode)}/${encodeURIComponent(id)}/versions`);
    },

    rollback(projectCode: string, id: string, versionNumber: number) {
      const resourcePath = `${path(projectCode)}/${encodeURIComponent(id)}/rollback`;
      const url = options.rollbackWithQuery
        ? `${resourcePath}?versionNumber=${encodeURIComponent(String(versionNumber))}`
        : `${resourcePath}/${versionNumber}`;
      return HttpUtils.postData<ModelingRecord>(url, {});
    },
  };
}
