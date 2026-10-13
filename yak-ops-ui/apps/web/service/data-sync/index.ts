import HttpUtils from "@/service/http/HttpUtils";
import { BizError } from "@/service/http/request";
import {
  cancelTaskInstance,
  createTaskRunIdempotencyKey,
  getTaskInstance,
  listTaskAttempts,
  listTaskInstances,
  submitTaskInstance,
  type TaskDefinitionVersionRecord,
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
  DataSyncExecutionEventRecord,
  DataSyncInstancePageParams,
  DataSyncInstancePageResult,
  DataSyncInstanceRecord,
  DataSyncOperationsDashboard,
  DataSyncOperationsDashboardPayload,
  DataSyncSchedulePreview,
  DataSyncScheduleRecord,
  DataSyncScheduleSavePayload,
  DataSyncSinkTraceRecord,
  DataSyncSourceTraceRecord,
  DataSyncTaskOperationPageResult,
  DataSyncTaskPageParams,
  DataSyncTaskPageResult,
  DataSyncTaskRecord,
  DataSyncTaskSavePayload,
  DataSyncTaskUpdatePayload,
  DataSyncTracePage,
  DataSyncTraceQuery,
  DataSyncTraceSummary,
  TaskDefinitionPageResult,
  TaskDefinitionRecord,
} from "./types";

export type * from "./types";

const DATA_SYNC_API_PREFIX = "/api/v1/data-sync";
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

export const listDataSyncOperationTasks = (
  params: DataSyncTaskPageParams,
): Promise<DataSyncTaskOperationPageResult> =>
  HttpUtils.postData<DataSyncTaskOperationPageResult>(
    `${DATA_SYNC_API_PREFIX}/operations/tasks/page`,
    params,
  );

export const getDataSyncOperationsDashboard = (
  payload: DataSyncOperationsDashboardPayload,
): Promise<DataSyncOperationsDashboard> =>
  HttpUtils.postData<DataSyncOperationsDashboard>(
    `${DATA_SYNC_API_PREFIX}/operations/dashboard`,
    payload,
  );

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

// InstanceVO is engine-neutral; the pinned Definition version determines DATA_SYNC's source kind.
async function projectDataSyncInstances(
  instances: TaskInstanceRecord[],
): Promise<DataSyncInstanceRecord[]> {
  const definitions = new Map<
    string,
    Promise<{ name: string; versions: TaskDefinitionVersionRecord[] }>
  >();

  return Promise.all(
    instances
      .filter((instance) => instance.taskType === "DATA_SYNC")
      .map(async (instance) => {
        let definition = definitions.get(instance.definitionId);
        if (!definition) {
          definition = Promise.all([
            HttpUtils.getData<TaskDefinitionRecord>(
              `${TASK_DEFINITION_API_PREFIX}/${instance.definitionId}`,
            ),
            HttpUtils.getData<TaskDefinitionVersionRecord[]>(
              `${TASK_DEFINITION_API_PREFIX}/${instance.definitionId}/versions`,
            ),
          ]).then(([current, versions]) => ({ name: current.name, versions }));
          definitions.set(instance.definitionId, definition);
        }

        const { name, versions } = await definition;
        const version = versions.find((item) => item.version === instance.definitionVersion);
        if (!version) {
          throw new Error("Task Instance Definition version is missing");
        }
        return toDataSyncInstanceRecord(instance, version, name);
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

// The Stage 1 /task-instances/page contract is workspace-paged only.
// Filter after reading all workspace pages; do not send unsupported filters to Boot.
export const listDataSyncInstances = async (
  params: DataSyncInstancePageParams,
): Promise<DataSyncInstancePageResult> => {
  if (params.triggerType) {
    throw new Error("Task Instance API does not expose trigger-type filtering");
  }

  const instances: TaskInstanceRecord[] = [];
  let pageNo = 1;
  let pages = 1;
  do {
    const result = await listTaskInstances({ pageNo, pageSize: TASK_INSTANCE_BATCH_SIZE });
    instances.push(...result.bizData);
    pages = result.pagination.pages;
    pageNo += 1;
  } while (pageNo <= pages);

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

export const listDataSyncExecutionEvents = (id: string): Promise<DataSyncExecutionEventRecord[]> =>
  HttpUtils.getData<DataSyncExecutionEventRecord[]>(`${DATA_SYNC_API_PREFIX}/instances/${id}/logs`);

const traceQuery = (query?: DataSyncTraceQuery) => {
  const params = new URLSearchParams();
  if (query?.attemptNo) params.set("attemptNo", String(query.attemptNo));
  if (query?.pageSize) params.set("pageSize", String(query.pageSize));
  if (query?.cursor) params.set("cursor", query.cursor);
  if (query?.status) params.set("status", query.status);
  const value = params.toString();
  return value ? `?${value}` : "";
};

export const getDataSyncTraceSummary = (
  id: string,
  attemptNo?: number,
): Promise<DataSyncTraceSummary> =>
  HttpUtils.getData<DataSyncTraceSummary>(
    `${DATA_SYNC_API_PREFIX}/instances/${id}/trace/summary${traceQuery({ attemptNo })}`,
  );

export const listDataSyncSourceTrace = (
  id: string,
  query?: DataSyncTraceQuery,
): Promise<DataSyncTracePage<DataSyncSourceTraceRecord>> =>
  HttpUtils.getData<DataSyncTracePage<DataSyncSourceTraceRecord>>(
    `${DATA_SYNC_API_PREFIX}/instances/${id}/trace/source${traceQuery(query)}`,
  );

export const listDataSyncSinkTrace = (
  id: string,
  query?: DataSyncTraceQuery,
): Promise<DataSyncTracePage<DataSyncSinkTraceRecord>> =>
  HttpUtils.getData<DataSyncTracePage<DataSyncSinkTraceRecord>>(
    `${DATA_SYNC_API_PREFIX}/instances/${id}/trace/sink${traceQuery(query)}`,
  );

const DATA_SYNC_SCHEDULE_NOT_FOUND_CODE = 42015;

export const getDataSyncSchedule = async (
  id: string,
): Promise<DataSyncScheduleRecord | undefined> => {
  try {
    const schedule = await HttpUtils.getData<DataSyncScheduleRecord | null>(
      `${DATA_SYNC_API_PREFIX}/tasks/${id}/schedule`,
      { skipErrorHandler: true },
    );
    return schedule || undefined;
  } catch (error) {
    if (error instanceof BizError && error.code === DATA_SYNC_SCHEDULE_NOT_FOUND_CODE) {
      return undefined;
    }
    throw error;
  }
};

export const saveDataSyncSchedule = (
  id: string,
  payload: DataSyncScheduleSavePayload,
): Promise<DataSyncScheduleRecord> =>
  HttpUtils.putData<DataSyncScheduleRecord>(
    `${DATA_SYNC_API_PREFIX}/tasks/${id}/schedule`,
    payload,
  );

export const previewDataSyncSchedule = (
  payload: DataSyncScheduleSavePayload,
): Promise<DataSyncSchedulePreview> =>
  HttpUtils.postData<DataSyncSchedulePreview>(
    `${DATA_SYNC_API_PREFIX}/schedules/preview`,
    payload,
    { skipErrorHandler: true },
  );

export const enableDataSyncSchedule = (id: string): Promise<DataSyncScheduleRecord> =>
  HttpUtils.postData<DataSyncScheduleRecord>(`${DATA_SYNC_API_PREFIX}/tasks/${id}/schedule/enable`);

export const disableDataSyncSchedule = (id: string): Promise<DataSyncScheduleRecord> =>
  HttpUtils.postData<DataSyncScheduleRecord>(
    `${DATA_SYNC_API_PREFIX}/tasks/${id}/schedule/disable`,
  );
