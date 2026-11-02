import HttpUtils from "@/service/http/HttpUtils";
import { BizError } from "@/service/http/request";
import { connectorChoices, type PipelineConnectorPair } from "./connectors";
import {
  cancelTaskInstance,
  createTaskRunIdempotencyKey,
  getTaskInstance,
  getTaskInstanceDefinition,
  listTaskInstances,
  submitTaskInstance,
  type TaskInstanceRecord,
  type TaskInstanceDefinitionRecord,
  type TaskInstanceStatus,
  type TaskSubmissionResult,
} from "@/service/task-execution";

import { toDataSyncInstanceRecord } from "./execution";

import {
  createDefinitionRequest,
  definitionPageRequest,
  projectSyncTaskPage,
  toSyncTaskRecord,
  updateDefinitionRequest,
} from "./definition";

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

const TASK_DEFINITION_API_PREFIX = "/api/v1/task-definitions";

export const isRejectedTaskWrite = (failure: unknown) => failure instanceof BizError;

export const listDataSyncSolutions = async (
  sourceType: string,
  targetType: string,
  signal?: AbortSignal,
): Promise<DataSyncSolution[]> => {
  const query = new URLSearchParams({ sourceType, targetType });
  return connectorChoices(
    await HttpUtils.getData<PipelineConnectorPair[]>(`/api/v1/data-sync/connectors?${query}`, {
      signal,
    }),
  );
};

export const listDataSyncTasks = async (
  params: DataSyncTaskPageParams,
  signal?: AbortSignal,
): Promise<DataSyncTaskPageResult> => {
  const page = await HttpUtils.postData<TaskDefinitionPageResult>(
    `${TASK_DEFINITION_API_PREFIX}/page`,
    definitionPageRequest(params),
    { signal },
  );
  return projectSyncTaskPage(page);
};

export const getDataSyncTask = async (
  id: string,
  signal?: AbortSignal,
): Promise<DataSyncTaskRecord> => {
  const definition = await HttpUtils.getData<TaskDefinitionRecord>(
    `${TASK_DEFINITION_API_PREFIX}/${id}`,
    { signal },
  );
  return toSyncTaskRecord(definition);
};

export const createDataSyncTask = async (
  payload: DataSyncTaskSavePayload,
  signal?: AbortSignal,
): Promise<DataSyncTaskRecord> => {
  const definition = await HttpUtils.postData<TaskDefinitionRecord>(
    TASK_DEFINITION_API_PREFIX,
    createDefinitionRequest(payload),
    { signal },
  );
  return toSyncTaskRecord(definition);
};

export const updateDataSyncTask = async (
  id: string,
  payload: DataSyncTaskUpdatePayload,
  expectedRevision: number,
  signal?: AbortSignal,
): Promise<DataSyncTaskRecord> => {
  const definition = await HttpUtils.putData<TaskDefinitionRecord>(
    `${TASK_DEFINITION_API_PREFIX}/${id}`,
    updateDefinitionRequest(payload, expectedRevision),
    { signal },
  );
  return toSyncTaskRecord(definition);
};

export const deleteDataSyncTask = async (id: string): Promise<void> => {
  await HttpUtils.deleteData<boolean>(`${TASK_DEFINITION_API_PREFIX}/${id}`);
};

export const publishDataSyncTask = async (
  id: string,
  payload: PublishTaskRequest,
  signal?: AbortSignal,
  locallyHandled = false,
): Promise<DataSyncTaskRecord> => {
  const definition = await HttpUtils.postData<TaskDefinitionRecord>(
    `${TASK_DEFINITION_API_PREFIX}/${id}/publish`,
    payload,
    { signal, skipErrorHandler: locallyHandled },
  );
  return toSyncTaskRecord(definition);
};

export const getTaskDefinition = (id: string, signal?: AbortSignal) =>
  HttpUtils.getData<TaskDefinitionRecord>(`${TASK_DEFINITION_API_PREFIX}/${id}`, { signal });

export const listTaskParameterVersions = (
  id: string,
  pageNo: number,
  pageSize: number,
  signal?: AbortSignal,
) =>
  HttpUtils.getData<TaskVersionPage<TaskParameterVersionSummary>>(
    `${TASK_DEFINITION_API_PREFIX}/${id}/versions?${new URLSearchParams({ pageNo: String(pageNo), pageSize: String(pageSize) })}`,
    { signal },
  );

export const getTaskParameterVersion = (id: string, version: number, signal?: AbortSignal) =>
  HttpUtils.getData<TaskParameterVersion>(
    `${TASK_DEFINITION_API_PREFIX}/${id}/versions/${version}`,
    {
      signal,
    },
  );

export const listTaskPublications = (
  id: string,
  pageNo: number,
  pageSize: number,
  signal?: AbortSignal,
) =>
  HttpUtils.getData<TaskVersionPage<TaskPublicationRecord>>(
    `${TASK_DEFINITION_API_PREFIX}/${id}/publications?${new URLSearchParams({ pageNo: String(pageNo), pageSize: String(pageSize) })}`,
    { signal },
  );

export const unpublishDataSyncTask = async (id: string): Promise<DataSyncTaskRecord> => {
  const definition = await HttpUtils.postData<TaskDefinitionRecord>(
    `${TASK_DEFINITION_API_PREFIX}/${id}/unpublish`,
  );
  return toSyncTaskRecord(definition);
};

export const runDataSyncTask = (
  id: string,
  idempotencyKey: string = createTaskRunIdempotencyKey(),
): Promise<TaskSubmissionResult> => submitTaskInstance(id, idempotencyKey);

async function projectDataSyncInstances(
  instances: TaskInstanceRecord[],
): Promise<DataSyncInstanceRecord[]> {
  const definitions = new Map<string, Promise<TaskInstanceDefinitionRecord>>();
  return Promise.all(
    instances.map(async (instance) => {
      if (instance.taskType !== "DATA_SYNC")
        throw new Error("Task Instance is not a DATA_SYNC task");
      const key = JSON.stringify([instance.definitionId, instance.definitionVersion]);
      let definition = definitions.get(key);
      if (!definition) {
        definition = getTaskInstanceDefinition(instance.id);
        definitions.set(key, definition);
      }
      return toDataSyncInstanceRecord(instance, await definition);
    }),
  );
}

export const getDataSyncInstance = async (id: string): Promise<DataSyncInstanceRecord> => {
  const records = await projectDataSyncInstances([await getTaskInstance(id)]);
  if (records.length !== 1) {
    throw new Error("Task Instance is not a DATA_SYNC task");
  }
  return records[0];
};

/** Page a single task's history using repository-side ordering and lifecycle filters. */
export const listDataSyncInstances = async (
  params: DataSyncInstancePageParams,
): Promise<DataSyncInstancePageResult> => {
  const page = await listTaskInstances({
    pageNo: params.pageNo,
    pageSize: params.pageSize,
    definitionId: params.taskId,
    taskType: "DATA_SYNC",
    status: params.status,
  });
  return { ...page, bizData: await projectDataSyncInstances(page.bizData) };
};

export const cancelDataSyncInstance = (id: string): Promise<TaskInstanceStatus> =>
  cancelTaskInstance(id);
