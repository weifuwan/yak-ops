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
  | "codeDetail"
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
  standardDirectory: "assets/standard-directory",
  fieldStandard: "assets/field-standard",
  codeStandard: "assets/code-standard",
  codeDetail: "assets/code-detail",
  unitStandard: "assets/unit-standard",
  dictionaryStandard: "assets/dictionary-standard",
  atomIndicator: "assets/atom-indicator",
  derivativeIndicator: "assets/derivative-indicator",
  composeIndicator: "assets/compose-indicator",
  modifier: "assets/modifier",
  timePeriodCategory: "assets/time-period-category",
  timePeriod: "assets/time-period",
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

export function createModelingResourceApi(
  resource: ModelingResourceKey,
  options: { allMethod?: "GET" | "POST" } = {},
): ModelingResourceApi {
  const path = (projectCode: string) => modelingResourcePath(projectCode, resource);

  return {
    page<T extends ModelingRecord>(projectCode: string, params: Record<string, unknown>) {
      return HttpUtils.postData<PageInfo<T>>(`${path(projectCode)}/page`, params);
    },

    all<T extends ModelingRecord>(projectCode: string, params: Record<string, string> = {}) {
      const query = new URLSearchParams(params).toString();
      const url = `${path(projectCode)}/all${query ? `?${query}` : ""}`;
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
      return HttpUtils.putData<T>(path(projectCode), payload);
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
      return HttpUtils.postData<ModelingRecord>(
        `${path(projectCode)}/${encodeURIComponent(id)}/rollback/${versionNumber}`,
        {},
      );
    },
  };
}
