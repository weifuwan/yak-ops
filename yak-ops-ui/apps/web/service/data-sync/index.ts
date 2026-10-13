import HttpUtils from "@/service/http/HttpUtils";
import {
  cancelTaskInstance,
  createTaskRunIdempotencyKey,
  getTaskInstance,
  getTaskInstanceDefinition,
  listTaskAttempts,
  listTaskInstances,
  submitTaskInstance,
  type TaskInstanceRecord,
  type TaskInstanceStatus,
  type TaskSubmissionResult,
} from "@/service/task-execution";

import {
  matchesDataSyncInstance,
  toDataSyncAttemptRecord,
  toDataSyncInstanceRecord,
} from "./execution";

import {
  createDefinitionRequest,
  definitionPageRequest,
  toSyncTaskRecord,
  updateDefinitionRequest,
} from "./definition";

import type {
  DataSyncAttemptRecord,
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
} from "./types";

export type * from "./types";

const TASK_DEFINITION_API_PREFIX = "/api/v1/task-definitions";

export const listDataSyncTasks = async (
  params: DataSyncTaskPageParams,
): Promise<DataSyncTaskPageResult> => {
  const page = await HttpUtils.postData<TaskDefinitionPageResult>(
    `${TASK_DEFINITION_API_PREFIX}/page`,
    definitionPageRequest(params),
  );
  return { ...page, bizData: page.bizData.map(toSyncTaskRecord) };
};

export const getDataSyncTask = async (id: string): Promise<DataSyncTaskRecord> => {
  const definition = await HttpUtils.getData<TaskDefinitionRecord>(
    `${TASK_DEFINITION_API_PREFIX}/${id}`,
  );
  return toSyncTaskRecord(definition);
};

export const createDataSyncTask = async (
  payload: DataSyncTaskSavePayload,
): Promise<DataSyncTaskRecord> => {
  const definition = await HttpUtils.postData<TaskDefinitionRecord>(
    TASK_DEFINITION_API_PREFIX,
    createDefinitionRequest(payload),
  );
  return toSyncTaskRecord(definition);
};

export const updateDataSyncTask = async (
  id: string,
  payload: DataSyncTaskUpdatePayload,
): Promise<DataSyncTaskRecord> => {
  const definition = await HttpUtils.putData<TaskDefinitionRecord>(
    `${TASK_DEFINITION_API_PREFIX}/${id}`,
    updateDefinitionRequest(payload),
  );
  return toSyncTaskRecord(definition);
};

export const deleteDataSyncTask = async (id: string): Promise<void> => {
  await HttpUtils.deleteData<boolean>(`${TASK_DEFINITION_API_PREFIX}/${id}`);
};

export const publishDataSyncTask = async (id: string): Promise<DataSyncTaskRecord> => {
  const definition = await HttpUtils.postData<TaskDefinitionRecord>(
    `${TASK_DEFINITION_API_PREFIX}/${id}/publish`,
  );
  return toSyncTaskRecord(definition);
};

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

const TASK_INSTANCE_BATCH_SIZE = 200;

async function projectDataSyncInstances(
  instances: TaskInstanceRecord[],
): Promise<DataSyncInstanceRecord[]> {
  return Promise.all(
    instances
      .filter((instance) => instance.taskType === "DATA_SYNC")
      .map(async (instance) =>
        toDataSyncInstanceRecord(instance, await getTaskInstanceDefinition(instance.id)),
      ),
  );
}

export const getDataSyncInstance = async (id: string): Promise<DataSyncInstanceRecord> => {
  const records = await projectDataSyncInstances([await getTaskInstance(id)]);
  if (records.length !== 1) {
    throw new Error("Task Instance is not a DATA_SYNC task");
  }
  return records[0];
};

// Only the source kind and optional task-name filter are resolved by the frontend.
// Definition, Task type, and lifecycle filters are applied by the Workspace-scoped repository.
export const listDataSyncInstances = async (
  params: DataSyncInstancePageParams,
): Promise<DataSyncInstancePageResult> => {
  // Active views query only nonterminal states instead of scanning all execution history.
  const statuses: Array<TaskInstanceStatus | undefined> =
    params.activeOnly && !params.status
      ? ["SUBMITTED", "RUNNING", "CANCELLING", "RECONCILING"]
      : [params.status];

  const batches = await Promise.all(
    statuses.map(async (status) => {
      const items: TaskInstanceRecord[] = [];
      let pageNo = 1;
      let pages = 1;
      do {
        const result = await listTaskInstances({
          pageNo,
          pageSize: TASK_INSTANCE_BATCH_SIZE,
          definitionId: params.taskId,
          taskType: "DATA_SYNC",
          status,
        });
        items.push(...result.bizData);
        pages = result.pagination.pages;
        pageNo += 1;
      } while (pageNo <= pages);
      return items;
    }),
  );
  const instances = batches.flat();
  if (statuses.length > 1) {
    instances.sort((first, second) =>
      (second.createTime ?? "").localeCompare(first.createTime ?? ""),
    );
  }
  const records = (await projectDataSyncInstances(instances)).filter((instance) =>
    matchesDataSyncInstance(instance, params),
  );
  const offset = (params.pageNo - 1) * params.pageSize;
  return {
    bizData: records.slice(offset, offset + params.pageSize),
    pagination: {
      pageNo: params.pageNo,
      pageSize: params.pageSize,
      total: records.length,
      pages: Math.ceil(records.length / params.pageSize),
    },
  };
};

export const cancelDataSyncInstance = (id: string): Promise<TaskInstanceStatus> =>
  cancelTaskInstance(id);

export const listDataSyncAttempts = async (id: string): Promise<DataSyncAttemptRecord[]> =>
  (await listTaskAttempts(id)).map((attempt) => toDataSyncAttemptRecord(attempt, id));
