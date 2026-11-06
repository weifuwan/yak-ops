import HttpUtils from "@/service/http/HttpUtils";
import type { PageInfo } from "@/service/http/page-info";
import { BizError } from "@/service/http/request";
import type {
  TaskInstanceRecord,
  TaskSubmissionResult,
  TaskInstanceDefinitionRecord,
  TaskLogPageRecord,
} from "@/service/task-execution/types";

import { connectorChoices, type PipelineConnectorPair } from "./connectors";
import {
  createDefinitionRequest,
  projectSyncTaskPage,
  toSyncTaskRecord,
  updateDefinitionRequest,
} from "./definition";
import { toDataSyncInstanceRecord } from "./execution";
import { YAK_OPS_API_PREFIX } from "@/config/api";
import { requireActiveProjectCode } from "@/service/workspace/project-scope";
import type {
  DataSyncSolution,
  DataSyncInstancePageParams,
  DataSyncInstanceRecord,
  DataSyncInstanceSummary,
  DataSyncTaskPageParams,
  DataSyncTaskListRecord,
  DataSyncTaskRecord,
  DataSyncTaskSavePayload,
  DataSyncTaskUpdatePayload,
  TaskDefinitionRecord,
  TaskParameterVersion,
  TaskParameterVersionSummary,
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

export const listDataSyncSolutions = async (
  sourceType: string,
  targetType: string,
  _signal?: AbortSignal,
): Promise<DataSyncSolution[]> => {
  if (sourceType.toUpperCase() !== "MYSQL" || targetType.toUpperCase() !== "MYSQL") return [];
  const pair: PipelineConnectorPair = {
    sourceConnector: "mysql-cdc",
    sinkConnector: "jdbc-mysql",
    producesChangelog: true,
    supportsIncrementalBatch: false,
    supportsSchemaChanges: true,
    supportsRetract: false,
    boundedness: "CONTINUOUS_UNBOUNDED",
    capabilities: {
      maxTables: 1,
      sourceRead: false,
      explicitSplitKey: false,
      dataPreview: true,
      sqlHooks: false,
      regexSelection: false,
      snapshotFilter: false,
      snapshotSkip: false,
      dmlEvents: false,
      typeMapping: false,
      targetStructureEditor: false,
      partitionFields: false,
      incrementalStream: false,
      writeModes: ["UPSERT"],
      sourceEngineKeys: [],
      sinkEngineKeys: [],
      typeMappingRules: [],
    },
  };
  return connectorChoices([pair]).map((solution) => ({
    ...solution,
    displayName: "MySQL 实时同步",
    allowedCombinations: solution.allowedCombinations.filter(
      (option) => option.steps.includes("FULL") && option.steps.includes("INCREMENTAL"),
    ),
    defaultSteps: ["FULL", "INCREMENTAL"],
  }));
};

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

export const createDataSyncTask = async (
  payload: DataSyncTaskSavePayload,
  signal?: AbortSignal,
): Promise<DataSyncTaskRecord> =>
  toSyncTaskRecord(
    await HttpUtils.postData<TaskDefinitionRecord>(
      url("/tasks"),
      createDefinitionRequest(payload),
      { signal },
    ),
  );

export const updateDataSyncTask = async (
  id: string,
  payload: DataSyncTaskUpdatePayload,
  expectedRevision: number,
  signal?: AbortSignal,
): Promise<DataSyncTaskRecord> =>
  toSyncTaskRecord(
    await HttpUtils.putData<TaskDefinitionRecord>(
      url("/tasks/" + encodeURIComponent(id)),
      updateDefinitionRequest(payload, expectedRevision),
      { signal },
    ),
  );

export const deleteDataSyncTask = async (id: string) => {
  await HttpUtils.deleteData<void>(url("/tasks/" + encodeURIComponent(id)));
};

export const publishDataSyncTask = async (
  id: string,
  _payload?: PublishTaskRequest,
  signal?: AbortSignal,
  locallyHandled = false,
): Promise<DataSyncTaskRecord> =>
  toSyncTaskRecord(
    await HttpUtils.postData<TaskDefinitionRecord>(
      url("/tasks/" + encodeURIComponent(id) + "/publish"),
      undefined,
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

export const listTaskParameterVersions = async (
  id: string,
  pageNo: number,
  pageSize: number,
  signal?: AbortSignal,
) =>
  HttpUtils.getData<PageInfo<TaskParameterVersionSummary>>(
    url("/tasks/" + encodeURIComponent(id) + "/versions?" + qp({ pageNo, pageSize })),
    { signal },
  );

export const getTaskParameterVersion = async (id: string, version: number, signal?: AbortSignal) =>
  HttpUtils.getData<TaskParameterVersion>(
    url("/tasks/" + encodeURIComponent(id) + "/versions/" + version),
    { signal },
  );

export const runDataSyncTask = async (id: string): Promise<{ workflowInstanceId: number }> =>
  HttpUtils.postData<{ workflowInstanceId: number }>(
    url("/tasks/" + encodeURIComponent(id) + "/start"),
  );

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

export const cancelDataSyncInstance = async (id: string): Promise<void> => {
  await HttpUtils.postData<void>(instanceUrl(id) + "/stop");
};

export const getDataSyncInstanceLogs = async (
  id: string,
  cursor?: string,
  limit = 200,
): Promise<TaskLogPageRecord> => {
  const response = await HttpUtils.getData<{
    content: string;
    nextOffset: number;
    hasMore: boolean;
  }>((instanceUrl(id)) + "/logs?" + qp({ offset: cursor ? Number(cursor) : 0, limit }));
  return {
    content: response.content,
    nextCursor: String(response.nextOffset),
    hasMore: response.hasMore,
  };
};
