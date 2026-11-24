import { YAK_OPS_API_PREFIX } from "@/config/api";
import HttpUtils from "@/service/http/HttpUtils";
import type { PageInfo } from "@/service/http/page-info";
import { requireActiveProjectCode } from "@/service/workspace/project-scope";

export type TaskInstanceExecuteType = "BATCH" | "STREAM";

export interface TaskInstanceRecord {
  id: number;
  name: string;
  taskType: string;
  workflowInstanceId: number;
  workflowInstanceName?: string | null;
  projectCode?: number;
  taskCode: number;
  taskDefinitionVersion?: number;
  state: string;
  firstSubmitTime?: string | null;
  submitTime?: string | null;
  startTime?: string | null;
  endTime?: string | null;
  host?: string | null;
  retryTimes?: number;
  appLink?: string | null;
  workerGroup?: string | null;
  environmentCode?: number | null;
  executorId?: number;
  executorName?: string | null;
  duration?: string | null;
  taskExecuteType?: TaskInstanceExecuteType | null;
  taskParams?: string | null;
}

export interface TaskInstanceQuery {
  pageNo: number;
  pageSize: number;
  workflowInstanceId?: number;
  workflowInstanceName?: string;
  workflowDefinitionName?: string;
  searchVal?: string;
  taskName?: string;
  taskCode?: number;
  executorName?: string;
  stateType?: string;
  host?: string;
  startDate?: string;
  endDate?: string;
  taskExecuteType?: TaskInstanceExecuteType;
}

const queryString = (params: Record<string, string | number | undefined>) => {
  const query = new URLSearchParams();
  Object.entries(params).forEach(([key, value]) => {
    if (value !== undefined && value !== "") query.set(key, String(value));
  });
  return query.toString();
};

const baseUrl = () =>
  YAK_OPS_API_PREFIX +
  "/projects/" +
  encodeURIComponent(requireActiveProjectCode()) +
  "/task-instances";

export function listTaskInstances(
  params: TaskInstanceQuery,
  signal?: AbortSignal,
): Promise<PageInfo<TaskInstanceRecord>> {
  return HttpUtils.getData<PageInfo<TaskInstanceRecord>>(
    baseUrl() +
      "?" +
      queryString({
        ...params,
        workflowInstanceId: params.workflowInstanceId ?? 0,
        taskExecuteType: params.taskExecuteType ?? "BATCH",
      }),
    { signal },
  );
}

export function getTaskInstanceDetail(
  taskInstanceId: number,
  signal?: AbortSignal,
): Promise<TaskInstanceRecord> {
  return HttpUtils.getData<TaskInstanceRecord>(
    baseUrl() + "/" + encodeURIComponent(String(taskInstanceId)),
    { signal },
  );
}
