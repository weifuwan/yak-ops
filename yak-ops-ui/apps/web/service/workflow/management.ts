import { YAK_OPS_API_PREFIX } from "@/config/api";
import HttpUtils from "@/service/http/HttpUtils";
import type { PageInfo } from "@/service/http/page-info";
import { requireActiveProjectCode } from "@/service/workspace/project-scope";

export type WorkflowWarningType = "NONE" | "SUCCESS" | "FAILURE" | "ALL";
export type WorkflowFailureStrategy = "END" | "CONTINUE";
export type WorkflowPriority = "HIGHEST" | "HIGH" | "MEDIUM" | "LOW" | "LOWEST";
export type WorkflowMissedFirePolicy = "SKIP_MISSED" | "FIRE_ONCE_NOW" | "FIRE_ALL_MISSED";

export interface WorkflowScheduleParam {
  startTime: string;
  endTime: string;
  timezoneId: string;
  crontab: string;
  missedFirePolicy: WorkflowMissedFirePolicy;
}

export interface WorkflowScheduleOptions {
  warningType: WorkflowWarningType;
  warningGroupId: number;
  failureStrategy: WorkflowFailureStrategy;
  workflowInstancePriority: WorkflowPriority;
  workerGroup: string;
  tenantCode: string;
  environmentCode?: number;
}

export interface WorkflowScheduleRecord extends WorkflowScheduleParam, WorkflowScheduleOptions {
  id: number;
  workflowDefinitionCode: number;
  workflowDefinitionName?: string | null;
  projectName?: string | null;
  releaseState: "ONLINE" | "OFFLINE" | string;
  createTime?: string | null;
  updateTime?: string | null;
  userName?: string | null;
}

export interface WorkflowInstanceSummary {
  id: number;
  workflowDefinitionCode: number;
  workflowDefinitionVersion: number;
  state: string;
  startTime?: string | null;
  endTime?: string | null;
  duration?: string | null;
  name?: string | null;
  executorName?: string | null;
  commandType?: string | null;
  scheduleTime?: string | null;
  workerGroup?: string | null;
  host?: string | null;
}

export interface WorkflowInstanceDetail extends WorkflowInstanceSummary {
  projectCode?: number;
  failureStrategy?: string;
  warningType?: string;
  workflowInstancePriority?: string;
  tenantCode?: string;
  timeout?: number;
  commandStartTime?: string | null;
}

export interface WorkflowInstanceTask {
  id: number;
  name: string;
  taskType: string;
  taskCode: number;
  state: string;
  startTime?: string | null;
  endTime?: string | null;
  duration?: string | null;
  host?: string | null;
  executorName?: string | null;
  taskInstanceDependentResults?: unknown[];
}

export interface WorkflowInstanceTaskList {
  workflowInstanceState: string;
  taskList: WorkflowInstanceTask[] | null;
}

export interface WorkflowDefinitionVersionRecord {
  id?: number;
  code: number;
  name: string;
  version: number;
  releaseState: string;
  projectCode: number;
  description?: string | null;
  globalParams?: string | null;
  timeout?: number;
  executionType?: string | null;
  operator?: number;
  operateTime?: string | null;
  userName?: string | null;
  modifyBy?: string | null;
}

const projectBase = () =>
  YAK_OPS_API_PREFIX +
  "/projects/" +
  encodeURIComponent(requireActiveProjectCode());

const definitionBase = () => projectBase() + "/workflow-definition";
const scheduleBase = () => projectBase() + "/schedules";
const instanceBase = () => projectBase() + "/workflow-instances";

function scheduleQuery(
  schedule: WorkflowScheduleParam,
  options: WorkflowScheduleOptions,
  workflowDefinitionCode?: number,
): string {
  const params = new URLSearchParams({
    schedule: JSON.stringify(schedule),
    warningType: options.warningType,
    warningGroupId: String(options.warningGroupId),
    failureStrategy: options.failureStrategy,
    workflowInstancePriority: options.workflowInstancePriority,
    workerGroup: options.workerGroup || "default",
    tenantCode: options.tenantCode || "default",
    environmentCode: String(options.environmentCode ?? -1),
  });
  if (workflowDefinitionCode !== undefined) {
    params.set("workflowDefinitionCode", String(workflowDefinitionCode));
  }
  return params.toString();
}

/** Query the schedule attached to one workflow definition. */
export async function getWorkflowSchedule(
  workflowDefinitionCode: number,
  signal?: AbortSignal,
): Promise<WorkflowScheduleRecord | null> {
  const params = new URLSearchParams({
    workflowDefinitionCode: String(workflowDefinitionCode),
    pageNo: "1",
    pageSize: "1",
  });
  const page = await HttpUtils.getData<PageInfo<WorkflowScheduleRecord>>(
    scheduleBase() + "?" + params.toString(),
    { signal },
  );
  return page.totalList?.[0] ?? null;
}

/** Create a workflow-level schedule; this is intentionally separate from task schedules. */
export function createWorkflowSchedule(
  workflowDefinitionCode: number,
  schedule: WorkflowScheduleParam,
  options: WorkflowScheduleOptions,
): Promise<WorkflowScheduleRecord> {
  return HttpUtils.postData<WorkflowScheduleRecord>(
    scheduleBase() + "?" + scheduleQuery(schedule, options, workflowDefinitionCode),
  );
}

export function updateWorkflowSchedule(
  scheduleId: number,
  schedule: WorkflowScheduleParam,
  options: WorkflowScheduleOptions,
): Promise<WorkflowScheduleRecord> {
  return HttpUtils.putData<WorkflowScheduleRecord>(
    scheduleBase() +
      "/" +
      encodeURIComponent(String(scheduleId)) +
      "?" +
      scheduleQuery(schedule, options),
  );
}

export function publishWorkflowSchedule(scheduleId: number): Promise<boolean> {
  return HttpUtils.postData<boolean>(
    scheduleBase() + "/" + encodeURIComponent(String(scheduleId)) + "/online",
  );
}

export function offlineWorkflowSchedule(scheduleId: number): Promise<boolean> {
  return HttpUtils.postData<boolean>(
    scheduleBase() + "/" + encodeURIComponent(String(scheduleId)) + "/offline",
  );
}

export function previewWorkflowSchedule(
  schedule: WorkflowScheduleParam,
  signal?: AbortSignal,
): Promise<string[]> {
  const params = new URLSearchParams({ schedule: JSON.stringify(schedule) });
  return HttpUtils.postData<string[]>(
    scheduleBase() + "/preview?" + params.toString(),
    undefined,
    { signal },
  );
}

export async function listWorkflowDefinitionVersions(
  workflowDefinitionCode: number,
  pageNo = 1,
  pageSize = 10,
  signal?: AbortSignal,
): Promise<PageInfo<WorkflowDefinitionVersionRecord>> {
  const params = new URLSearchParams({
    pageNo: String(pageNo),
    pageSize: String(pageSize),
  });
  return HttpUtils.getData<PageInfo<WorkflowDefinitionVersionRecord>>(
    definitionBase() +
      "/" +
      encodeURIComponent(String(workflowDefinitionCode)) +
      "/versions?" +
      params.toString(),
    { signal },
  );
}

/** The backend exposes version switching on this GET route. */
export function switchWorkflowDefinitionVersion(
  workflowDefinitionCode: number,
  version: number,
): Promise<void> {
  return HttpUtils.getData<void>(
    definitionBase() +
      "/" +
      encodeURIComponent(String(workflowDefinitionCode)) +
      "/versions/" +
      encodeURIComponent(String(version)),
  );
}

export async function listWorkflowInstances(
  workflowDefinitionCode: number,
  pageNo = 1,
  pageSize = 10,
  signal?: AbortSignal,
): Promise<PageInfo<WorkflowInstanceSummary>> {
  const params = new URLSearchParams({
    workflowDefinitionCode: String(workflowDefinitionCode),
    pageNo: String(pageNo),
    pageSize: String(pageSize),
  });
  return HttpUtils.getData<PageInfo<WorkflowInstanceSummary>>(
    instanceBase() + "?" + params.toString(),
    { signal },
  );
}

export function getWorkflowInstanceDetail(
  instanceId: number,
  signal?: AbortSignal,
): Promise<WorkflowInstanceDetail> {
  return HttpUtils.getData<WorkflowInstanceDetail>(
    instanceBase() + "/" + encodeURIComponent(String(instanceId)),
    { signal },
  );
}

export function getWorkflowInstanceTasks(
  instanceId: number,
  signal?: AbortSignal,
): Promise<WorkflowInstanceTaskList> {
  return HttpUtils.getData<WorkflowInstanceTaskList>(
    instanceBase() +
      "/" +
      encodeURIComponent(String(instanceId)) +
      "/tasks",
    { signal },
  );
}
