import { YAK_OPS_API_PREFIX } from "@/config/api";
import HttpUtils from "@/service/http/HttpUtils";
import type { PageInfo } from "@/service/http/page-info";

export interface ProjectParameterRecord {
  code: number;
  projectCode: number;
  paramName: string;
  paramValue?: string | null;
  paramDataType?: string | null;
}

export interface ProjectParameterQuery {
  pageNo?: number;
  pageSize?: number;
  searchVal?: string;
  projectParameterDataType?: string;
}

const baseUrl = (projectCode: number | string) =>
  YAK_OPS_API_PREFIX +
  "/projects/" +
  encodeURIComponent(String(projectCode)) +
  "/project-parameter";

const queryString = (params: Record<string, string | number | undefined>) => {
  const query = new URLSearchParams();
  Object.entries(params).forEach(([key, value]) => {
    if (value !== undefined && value !== "") query.set(key, String(value));
  });
  return query.toString();
};

export const queryProjectParameters = (
  projectCode: number | string,
  params: ProjectParameterQuery = {},
  signal?: AbortSignal,
): Promise<PageInfo<ProjectParameterRecord>> =>
  HttpUtils.getData<PageInfo<ProjectParameterRecord>>(
    baseUrl(projectCode) +
      "?" +
      queryString({
        pageNo: params.pageNo ?? 1,
        pageSize: params.pageSize ?? 20,
        searchVal: params.searchVal,
        projectParameterDataType: params.projectParameterDataType,
      }),
    { signal },
  );

/** Lightweight lookup retained for task editors that only need project variables. */
export const listProjectParameters = (
  projectCode: number,
  signal?: AbortSignal,
): Promise<PageInfo<ProjectParameterRecord>> =>
  queryProjectParameters(projectCode, { pageNo: 1, pageSize: 100 }, signal);

export interface ProjectParameterPayload {
  projectParameterName: string;
  projectParameterValue: string;
  projectParameterDataType: string;
}

function toFormBody(payload: ProjectParameterPayload) {
  return new URLSearchParams({
    projectParameterName: payload.projectParameterName,
    projectParameterValue: payload.projectParameterValue,
    projectParameterDataType: payload.projectParameterDataType,
  });
}

export function createProjectParameter(
  projectCode: number | string,
  payload: ProjectParameterPayload,
): Promise<ProjectParameterRecord> {
  return HttpUtils.postData<ProjectParameterRecord>(
    baseUrl(projectCode),
    undefined,
    { body: toFormBody(payload) },
  );
}

export function updateProjectParameter(
  projectCode: number | string,
  code: number,
  payload: ProjectParameterPayload,
): Promise<ProjectParameterRecord> {
  return HttpUtils.putData<ProjectParameterRecord>(
    baseUrl(projectCode) + "/" + encodeURIComponent(String(code)),
    undefined,
    { body: toFormBody(payload) },
  );
}

export function deleteProjectParameter(
  projectCode: number | string,
  code: number,
): Promise<boolean> {
  return HttpUtils.postData<boolean>(
    baseUrl(projectCode) + "/delete?" + queryString({ code }),
  );
}

export function batchDeleteProjectParameters(
  projectCode: number | string,
  codes: number[],
): Promise<boolean> {
  return HttpUtils.postData<boolean>(
    baseUrl(projectCode) +
      "/batch-delete?" +
      queryString({ codes: codes.join(",") }),
  );
}
