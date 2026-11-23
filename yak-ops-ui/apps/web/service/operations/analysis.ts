import { YAK_OPS_API_PREFIX } from "@/config/api";
import HttpUtils from "@/service/http/HttpUtils";
import type { PageInfo } from "@/service/http/page-info";
import { requireActiveProjectCode } from "@/service/workspace/project-scope";

const ANALYSIS_API = YAK_OPS_API_PREFIX + "/projects/analysis";

const qp = (params: Record<string, string | number | undefined>) => {
  const search = new URLSearchParams();
  for (const [key, value] of Object.entries(params)) {
    if (value !== undefined && value !== "") search.set(key, String(value));
  }
  return search.toString();
};

export interface StatusCountRecord {
  state: string;
  count: number;
}

export interface TaskInstanceCount {
  totalCount: number;
  taskInstanceStatusCounts: StatusCountRecord[];
}

export interface WorkflowInstanceCount {
  totalCount: number;
  workflowInstanceStatusCounts: StatusCountRecord[];
}

export interface WorkflowDefinitionUserCount {
  userName: string;
  userId: number;
  count: number;
}

export interface WorkflowDefinitionCount {
  count: number;
  userList: WorkflowDefinitionUserCount[];
}

export interface CommandStateCount {
  errorCount: number;
  normalCount: number;
  commandState: string;
}

export interface CommandRecord {
  id: number;
  commandType?: string | null;
  workflowDefinitionCode: number;
  workflowDefinitionVersion: number;
  workflowInstanceId: number;
  workflowInstancePriority?: string | null;
  warningType?: string | null;
  warningGroupId?: number | null;
  scheduleTime?: string | null;
  startTime?: string | null;
  updateTime?: string | null;
  workerGroup?: string | null;
}

export interface ErrorCommandRecord extends CommandRecord {
  message?: string | null;
}

function projectCode() {
  return requireActiveProjectCode();
}

export function getTaskInstanceStateCount(
  startDate?: string,
  endDate?: string,
  signal?: AbortSignal,
): Promise<TaskInstanceCount> {
  return HttpUtils.getData<TaskInstanceCount>(
    ANALYSIS_API +
      "/task-state-count?" +
      qp({ startDate, endDate, projectCode: projectCode() }),
    { signal },
  );
}

export function getWorkflowInstanceStateCount(
  startDate?: string,
  endDate?: string,
  signal?: AbortSignal,
): Promise<WorkflowInstanceCount> {
  return HttpUtils.getData<WorkflowInstanceCount>(
    ANALYSIS_API +
      "/workflow-state-count?" +
      qp({ startDate, endDate, projectCode: projectCode() }),
    { signal },
  );
}

export function getWorkflowDefinitionCount(signal?: AbortSignal): Promise<WorkflowDefinitionCount> {
  return HttpUtils.getData<WorkflowDefinitionCount>(
    ANALYSIS_API + "/define-user-count?" + qp({ projectCode: projectCode() }),
    { signal },
  );
}

export function getCommandStateCount(signal?: AbortSignal): Promise<CommandStateCount[]> {
  return HttpUtils.getData<CommandStateCount[]>(ANALYSIS_API + "/command-state-count", { signal });
}

export function getQueueCount(signal?: AbortSignal): Promise<Record<string, number>> {
  return HttpUtils.getData<Record<string, number>>(ANALYSIS_API + "/queue-count", { signal });
}

export function listPendingCommands(
  pageNo = 1,
  pageSize = 20,
  signal?: AbortSignal,
): Promise<PageInfo<CommandRecord>> {
  return HttpUtils.getData<PageInfo<CommandRecord>>(
    ANALYSIS_API +
      "/listCommand?" +
      qp({ projectCode: projectCode(), pageNo, pageSize }),
    { signal },
  );
}

export function listErrorCommands(
  pageNo = 1,
  pageSize = 20,
  signal?: AbortSignal,
): Promise<PageInfo<ErrorCommandRecord>> {
  return HttpUtils.getData<PageInfo<ErrorCommandRecord>>(
    ANALYSIS_API +
      "/listErrorCommand?" +
      qp({ projectCode: projectCode(), pageNo, pageSize }),
    { signal },
  );
}
