import HttpUtils from "@/service/http/HttpUtils";
import { BizError } from "@/service/http/request";
import type {
  TaskInstanceRecord,
  TaskInstanceStatus,
  TaskSubmissionResult,
  TaskInstanceDefinitionRecord,
  TaskInstancePageResult,
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
import { dataSyncApi } from "./project";
import type {
  DataSyncSolution,
  DataSyncInstancePageParams,
  DataSyncInstancePageResult,
  DataSyncInstanceRecord,
  DataSyncTaskPageParams,
  DataSyncTaskPageResult,
  DataSyncTaskRecord,
  DataSyncTaskSavePayload,
  DataSyncTaskUpdatePayload,
  TaskDefinitionPageResult,
  TaskDefinitionRecord,
  TaskParameterVersion,
  TaskParameterVersionSummary,
  TaskPublicationRecord,
  TaskVersionPage,
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

const url = async (path: string) => (await dataSyncApi()) + path;

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
): Promise<DataSyncTaskPageResult> => {
  const page = await HttpUtils.getData<TaskDefinitionPageResult>(
    await url("/tasks?" + qp({
      pageNo: params.pageNo,
      pageSize: params.pageSize,
      keyword: params.keyword,
      status: params.status,
    })),
    { signal },
  );
  return projectSyncTaskPage(page);
};

export const getDataSyncTask = async (
  id: string, signal?: AbortSignal,
): Promise<DataSyncTaskRecord> =>
  toSyncTaskRecord(
    await HttpUtils.getData<TaskDefinitionRecord>(await url("/tasks/" + encodeURIComponent(id)), { signal }),
  );

export const createDataSyncTask = async (
  payload: DataSyncTaskSavePayload, signal?: AbortSignal,
): Promise<DataSyncTaskRecord> =>
  toSyncTaskRecord(
    await HttpUtils.postData<TaskDefinitionRecord>(
      await url("/tasks"), createDefinitionRequest(payload), { signal },
    ),
  );

export const updateDataSyncTask = async (
  id: string, payload: DataSyncTaskUpdatePayload, expectedRevision: number, signal?: AbortSignal,
): Promise<DataSyncTaskRecord> =>
  toSyncTaskRecord(
    await HttpUtils.putData<TaskDefinitionRecord>(
      await url("/tasks/" + encodeURIComponent(id)),
      updateDefinitionRequest(payload, expectedRevision),
      { signal },
    ),
  );

export const deleteDataSyncTask = async (id: string) => {
  await HttpUtils.deleteData<void>(await url("/tasks/" + encodeURIComponent(id)));
};

export const publishDataSyncTask = async (
  id: string, _payload?: PublishTaskRequest, signal?: AbortSignal,
  locallyHandled = false,
): Promise<DataSyncTaskRecord> =>
  toSyncTaskRecord(
    await HttpUtils.postData<TaskDefinitionRecord>(
      await url("/tasks/" + encodeURIComponent(id) + "/publish"),
      undefined,
      { signal, skipErrorHandler: locallyHandled },
    ),
  );

export const unpublishDataSyncTask = async (id: string): Promise<DataSyncTaskRecord> =>
  toSyncTaskRecord(await HttpUtils.postData<TaskDefinitionRecord>(
    await url("/tasks/" + encodeURIComponent(id) + "/unpublish"),
  ));

export const getTaskDefinition = async (id: string, signal?: AbortSignal) =>
  HttpUtils.getData<TaskDefinitionRecord>(await url("/tasks/" + encodeURIComponent(id)), { signal });

export const listTaskParameterVersions = async (
  id: string, pageNo: number, pageSize: number, signal?: AbortSignal,
) =>
  HttpUtils.getData<TaskVersionPage<TaskParameterVersionSummary>>(
    await url("/tasks/" + encodeURIComponent(id) + "/versions?" + qp({ pageNo, pageSize })),
    { signal },
  );

export const getTaskParameterVersion = async (
  id: string, version: number, signal?: AbortSignal,
) =>
  HttpUtils.getData<TaskParameterVersion>(
    await url("/tasks/" + encodeURIComponent(id) + "/versions/" + version),
    { signal },
  );

export const listTaskPublications = async (
  id: string, pageNo: number, pageSize: number, signal?: AbortSignal,
) =>
  HttpUtils.getData<TaskVersionPage<TaskPublicationRecord>>(
    await url("/tasks/" + encodeURIComponent(id) + "/publications?" + qp({ pageNo, pageSize })),
    { signal },
  );

export const runDataSyncTask = async (id: string): Promise<{ workflowInstanceId: number }> =>
  HttpUtils.postData<{ workflowInstanceId: number }>(
    await url("/tasks/" + encodeURIComponent(id) + "/start"),
  );

const instanceUrl = async (id: string) => url("/instances/" + encodeURIComponent(id));

const instanceDefinition = async (id: string): Promise<TaskInstanceDefinitionRecord> =>
  HttpUtils.getData<TaskInstanceDefinitionRecord>(await instanceUrl(id) + "/definition");

const projectInstances = async (
  instances: TaskInstanceRecord[],
): Promise<DataSyncInstanceRecord[]> => {
  const cache = new Map<string, Promise<TaskInstanceDefinitionRecord>>();
  return Promise.all(instances.map(async (instance) => {
    const key = instance.definitionId + ":" + instance.definitionVersion;
    if (!cache.has(key)) cache.set(key, instanceDefinition(instance.id));
    return toDataSyncInstanceRecord(instance, await cache.get(key)!);
  }));
};

export const getDataSyncInstance = async (id: string): Promise<DataSyncInstanceRecord> => {
  const record = await HttpUtils.getData<TaskInstanceRecord>(await instanceUrl(id));
  return (await projectInstances([record]))[0];
};

export const listDataSyncInstances = async (
  params: DataSyncInstancePageParams,
): Promise<DataSyncInstancePageResult> => {
  const page = await HttpUtils.getData<TaskInstancePageResult>(
    await url("/instances?" + qp({
      pageNo: params.pageNo,
      pageSize: params.pageSize,
      taskCode: params.taskId,
      status: params.status,
    })),
  );
  return { ...page, bizData: await projectInstances(page.bizData) };
};

export const listLatestDataSyncTaskInstances = async (
  taskIds: string[], activeOnly = false, signal?: AbortSignal,
): Promise<TaskInstanceRecord[]> => {
  if (taskIds.length === 0) return [];
  return HttpUtils.getData<TaskInstanceRecord[]>(
    await url("/instances/latest?" + qp({ taskCodes: taskIds.join(","), activeOnly })),
    { signal },
  );
};

export const cancelDataSyncInstance = async (id: string): Promise<TaskInstanceStatus> => {
  await HttpUtils.postData<void>(await instanceUrl(id) + "/stop");
  return "CANCELLING";
};

export const getDataSyncInstanceLogs = async (
  id: string, cursor?: string, limit = 200,
): Promise<TaskLogPageRecord> => {
  const response = await HttpUtils.getData<{ content: string; nextOffset: number; hasMore: boolean }>(
    await instanceUrl(id) + "/logs?" + qp({ offset: cursor ? Number(cursor) : 0, limit }),
  );
  return { content: response.content, nextCursor: String(response.nextOffset), hasMore: response.hasMore };
};
