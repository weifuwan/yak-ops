import HttpUtils from "@/service/http/HttpUtils";

import type {
  TaskInstanceDefinitionRecord,
  TaskInstancePageParams,
  TaskInstancePageResult,
  TaskInstanceRecord,
  TaskLogPageRecord,
  TaskInstanceStatus,
  TaskSubmissionResult,
} from "./types";

export { createTaskRunIdempotencyKey } from "./keys";
export type * from "./types";

const TASK_DEFINITIONS_API = "/api/v1/task-definitions";
const TASK_INSTANCES_API = "/api/v1/task-instances";

export const submitTaskInstance = (
  definitionId: string,
  idempotencyKey: string,
): Promise<TaskSubmissionResult> =>
  HttpUtils.postData<TaskSubmissionResult>(
    `${TASK_DEFINITIONS_API}/${definitionId}/instances`,
    undefined,
    { headers: { "Idempotency-Key": idempotencyKey } },
  );

export const listTaskInstances = (
  params: TaskInstancePageParams,
): Promise<TaskInstancePageResult> =>
  HttpUtils.postData<TaskInstancePageResult>(`${TASK_INSTANCES_API}/page`, params);

/** Returns newest Workspace-owned instances for the visible Definition page. */
export const listLatestTaskInstances = (
  definitionIds: string[],
  activeOnly = false,
  signal?: AbortSignal,
): Promise<TaskInstanceRecord[]> =>
  HttpUtils.postData<TaskInstanceRecord[]>(
    `${TASK_INSTANCES_API}/latest`,
    {
      definitionIds,
      activeOnly,
    },
    { signal },
  );

export const getTaskInstance = (id: string, signal?: AbortSignal): Promise<TaskInstanceRecord> =>
  HttpUtils.getData<TaskInstanceRecord>(`${TASK_INSTANCES_API}/${id}`, { signal });

export const getTaskInstanceDefinition = (id: string): Promise<TaskInstanceDefinitionRecord> =>
  HttpUtils.getData<TaskInstanceDefinitionRecord>(`${TASK_INSTANCES_API}/${id}/definition`);

export const cancelTaskInstance = (id: string): Promise<TaskInstanceStatus> =>
  HttpUtils.postData<TaskInstanceStatus>(`${TASK_INSTANCES_API}/${id}/cancel`);

export const getTaskInstanceLogs = (
  id: string,
  cursor?: string,
  limit = 200,
): Promise<TaskLogPageRecord> => {
  const params = new URLSearchParams({ limit: String(limit) });
  if (cursor) params.set("cursor", cursor);
  return HttpUtils.getData<TaskLogPageRecord>(`${TASK_INSTANCES_API}/${id}/logs?${params}`);
};

export const downloadTaskInstanceLogs = (id: string): Promise<Blob> =>
  HttpUtils.download(`${TASK_INSTANCES_API}/${id}/logs/download`);
