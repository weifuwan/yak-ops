import { YAK_OPS_API_PREFIX } from "@/config/api";
import HttpUtils from "@/service/http/HttpUtils";
import type { PageInfo } from "@/service/http/page-info";
import { requireActiveProjectCode } from "@/service/workspace/project-scope";

export interface WorkflowDefinitionRecord {
  id: number;
  code: number;
  name: string;
  version: number;
  releaseState: string;
  projectCode: number;
  description?: string | null;
  createTime?: string | null;
  updateTime?: string | null;
  userId: number;
  userName?: string | null;
  modifyBy?: string | null;
  executionType?: string | null;
}

export interface WorkflowTaskRecord {
  id: number;
  code: number;
  name: string;
  version: number;
  description?: string | null;
  projectCode: number;
  userId: number;
  taskType: string;
  createTime?: string | null;
  updateTime?: string | null;
  modifyBy?: string | null;
}

export interface WorkflowTaskRelationRecord {
  id: number;
  name?: string | null;
  workflowDefinitionVersion: number;
  projectCode: number;
  workflowDefinitionCode: number;
  preTaskCode: number;
  preTaskVersion: number;
  postTaskCode: number;
  postTaskVersion: number;
}

export interface WorkflowDagRecord {
  workflowDefinition: WorkflowDefinitionRecord;
  workflowTaskRelationList: WorkflowTaskRelationRecord[];
  taskDefinitionList: WorkflowTaskRecord[];
}

export interface WorkflowDefinitionPageParams {
  pageNo: number;
  pageSize: number;
  searchVal?: string;
  userId?: number;
}

const qp = (params: Record<string, string | number | undefined>) => {
  const search = new URLSearchParams();
  for (const [key, value] of Object.entries(params)) {
    if (value !== undefined && value !== "") search.set(key, String(value));
  }
  return search.toString();
};

const baseUrl = () =>
  YAK_OPS_API_PREFIX +
  "/projects/" +
  encodeURIComponent(requireActiveProjectCode()) +
  "/workflow-definition";

export const listWorkflowDefinitions = (
  params: WorkflowDefinitionPageParams,
  signal?: AbortSignal,
): Promise<PageInfo<WorkflowDefinitionRecord>> =>
  HttpUtils.getData<PageInfo<WorkflowDefinitionRecord>>(
    baseUrl() +
      "?" +
      qp({
        pageNo: params.pageNo,
        pageSize: params.pageSize,
        searchVal: params.searchVal,
        userId: params.userId,
      }),
    { signal },
  );

export const listWorkflowTasks = (
  workflowCode: number,
  signal?: AbortSignal,
): Promise<WorkflowTaskRecord[]> =>
  HttpUtils.getData<WorkflowTaskRecord[]>(
    baseUrl() + "/" + encodeURIComponent(String(workflowCode)) + "/tasks",
    { signal },
  );


export const getWorkflowDefinition = (
  workflowCode: number,
  signal?: AbortSignal,
): Promise<WorkflowDagRecord> =>
  HttpUtils.getData<WorkflowDagRecord>(
    baseUrl() + "/" + encodeURIComponent(String(workflowCode)),
    { signal },
  );
