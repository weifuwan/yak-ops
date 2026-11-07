import HttpUtils from "@/service/http/HttpUtils";
import type { PageInfo } from "@/service/http/page-info";
import { BizError } from "@/service/http/request";
import type {
  TaskInstanceRecord,
  TaskSubmissionResult,
  TaskInstanceDefinitionRecord,
} from "@/service/task-execution/types";

import {
  projectSyncTaskPage,
  toSyncTaskRecord,
} from "./definition";
import { toDataSyncInstanceRecord } from "./execution";
import { YAK_OPS_API_PREFIX } from "@/config/api";
import { requireActiveProjectCode } from "@/service/workspace/project-scope";
import type {
  DataSyncInstancePageParams,
  DataSyncInstanceRecord,
  DataSyncInstanceSummary,
  DataSyncTaskPageParams,
  DataSyncTaskListRecord,
  DataSyncTaskRecord,
  TaskDefinitionRecord,
  PublishTaskRequest,
} from "./types";

export type * from "./types";

const qp = (params: Record<string, string | number | boolean | undefined>) => {
  const search = new URLSearchParams();
  for (const [key, value] of Object.entries(params)) {
    if (value !== undefined && value !== "") search.set(key, String(value));
  }
  return search.toString();
};

const url = (path: string) =>
  YAK_OPS_API_PREFIX + "/projects/" + encodeURIComponent(requireActiveProjectCode()) + "/data-sync" + path;

export const isRejectedTaskWrite = (failure: unknown) => failure instanceof BizError;

export const listDataSyncTasks = async (
  params: DataSyncTaskPageParams,
  signal?: AbortSignal,
): Promise<PageInfo<DataSyncTaskListRecord>> => {
  const page = await HttpUtils.getData<PageInfo<TaskDefinitionRecord>>(
    url(
      "/tasks?" +
        qp({
          pageNo: params.pageNo,
          pageSize: params.pageSize,
          keyword: params.keyword,
          status: params.status,
        }),
    ),
    { signal },
  );
  return projectSyncTaskPage(page);
};

export const getDataSyncTask = async (
  id: string,
  signal?: AbortSignal,
): Promise<DataSyncTaskRecord> =>
  toSyncTaskRecord(
    await HttpUtils.getData<TaskDefinitionRecord>(url("/tasks/" + encodeURIComponent(id)), {
      signal,
    }),
  );

export const deleteDataSyncTask = async (id: string) => {
  await HttpUtils.deleteData<void>(url("/tasks/" + encodeURIComponent(id)));
};

export const publishDataSyncTask = async (
  id: string,
  payload: PublishTaskRequest,
  signal?: AbortSignal,
  locallyHandled = false,
): Promise<DataSyncTaskRecord> =>
  toSyncTaskRecord(
    await HttpUtils.postData<TaskDefinitionRecord>(
      url("/tasks/" + encodeURIComponent(id) + "/publish"),
      payload,
      { signal, skipErrorHandler: locallyHandled },
    ),
  );

export const unpublishDataSyncTask = async (id: string): Promise<DataSyncTaskRecord> =>
  toSyncTaskRecord(
    await HttpUtils.postData<TaskDefinitionRecord>(
      url("/tasks/" + encodeURIComponent(id) + "/unpublish"),
    ),
  );

export const getTaskDefinition = async (id: string, signal?: AbortSignal) =>
  HttpUtils.getData<TaskDefinitionRecord>(url("/tasks/" + encodeURIComponent(id)), {
    signal,
  });

export const runDataSyncTask = async (
  workflowCode: string,
  options?: { workerGroup?: string; startParams?: Record<string, string> },
): Promise<{ workflowInstanceId: number }> => {
  const ids = await HttpUtils.postData<number[]>(
    YAK_OPS_API_PREFIX + "/projects/" + encodeURIComponent(requireActiveProjectCode()) +
      "/executors/start-workflow-instance?" +
      qp({
        workflowDefinitionCode: workflowCode,
        execType: "START_PROCESS",
        failureStrategy: "END",
        taskDependType: "TASK_POST",
        warningType: "NONE",
        workflowInstancePriority: "MEDIUM",
        workerGroup: options?.workerGroup || "default",
        tenantCode: "default",
        dryRun: 0,
        startParams: options?.startParams ? JSON.stringify(options.startParams) : undefined,
      }),
    undefined,
  );
  if (!ids?.length) throw new Error("工作流启动未返回实例编号");
  return { workflowInstanceId: ids[0] };
};

const instanceUrl = (id: string, base = url("")) => base + "/instances/" + encodeURIComponent(id);

const instanceDefinition = async (id: string, base: string): Promise<TaskInstanceDefinitionRecord> =>
  HttpUtils.getData<TaskInstanceDefinitionRecord>(instanceUrl(id, base) + "/definition");

export const getDataSyncInstance = async (id: string): Promise<DataSyncInstanceRecord> => {
  const base = url("");
  const [instance, definition] = await Promise.all([
    HttpUtils.getData<TaskInstanceRecord>(instanceUrl(id, base)),
    instanceDefinition(id, base),
  ]);
  return toDataSyncInstanceRecord(instance, definition);
};

export const listDataSyncInstances = async (
  params: DataSyncInstancePageParams,
): Promise<PageInfo<DataSyncInstanceSummary>> => {
  const page = await HttpUtils.getData<PageInfo<TaskInstanceRecord & { taskName: string }>>(
    url(
      "/instances?" +
        qp({
          pageNo: params.pageNo,
          pageSize: params.pageSize,
          taskCode: params.taskId,
          status: params.status,
        }),
    ),
  );
  return {
    ...page,
    totalList: page.totalList.map((instance) => ({
      id: instance.id,
      taskId: instance.definitionId,
      taskName: instance.taskName,
      taskVersion: instance.definitionVersion,
      workflowInstanceId: instance.workflowInstanceId,
      syncType: "REALTIME" as const,
      triggerType: instance.triggerType,
      status: instance.status,
      startTime: instance.startTime ?? undefined,
      finishTime: instance.endTime ?? undefined,
      createTime: instance.createTime ?? undefined,
    })),
  };
};

export const listLatestDataSyncTaskInstances = async (
  taskIds: string[],
  activeOnly = false,
  signal?: AbortSignal,
): Promise<TaskInstanceRecord[]> => {
  if (taskIds.length === 0) return [];
  return HttpUtils.getData<TaskInstanceRecord[]>(
    url("/instances/latest?" + qp({ taskCodes: taskIds.join(","), activeOnly })),
    { signal },
  );
};

export const cancelDataSyncInstance = async (workflowInstanceId: number): Promise<void> => {
  await HttpUtils.postData<void>(
    YAK_OPS_API_PREFIX + "/projects/" + encodeURIComponent(requireActiveProjectCode()) +
      "/executors/execute?" + qp({ workflowInstanceId, executeType: "STOP" }),
    undefined,
  );
};

export const getDataSyncInstanceLogs = async (
  id: string,
  skipLineNum = 0,
  limit = 200,
): Promise<{ message: string; lineNum: number }> => {
  try {
    return await HttpUtils.getData<{ message: string; lineNum: number }>(
      YAK_OPS_API_PREFIX + "/log/detail?" + qp({ taskInstanceId: id, skipLineNum, limit }),
      { skipErrorHandler: true },
    );
  } catch (error) {
    if (error instanceof BizError && error.code === 10191) {
      return { message: "", lineNum: 0 };
    }
    throw error;
  }
};

export const downloadDataSyncInstanceLogs = (id: string): Promise<Blob> =>
  HttpUtils.download(
    YAK_OPS_API_PREFIX + "/log/download-log?" + qp({ taskInstanceId: id }),
  );
