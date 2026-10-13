import HttpUtils from "@/service/http/HttpUtils";

import type {
  TaskAttemptRecord,
  TaskInstanceDefinitionRecord,
  TaskInstancePageParams,
  TaskInstancePageResult,
  TaskInstanceRecord,
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

export const getTaskInstance = (id: string): Promise<TaskInstanceRecord> =>
  HttpUtils.getData<TaskInstanceRecord>(`${TASK_INSTANCES_API}/${id}`);

export const getTaskInstanceDefinition = (id: string): Promise<TaskInstanceDefinitionRecord> =>
  HttpUtils.getData<TaskInstanceDefinitionRecord>(`${TASK_INSTANCES_API}/${id}/definition`);

export const listTaskAttempts = (id: string): Promise<TaskAttemptRecord[]> =>
  HttpUtils.getData<TaskAttemptRecord[]>(`${TASK_INSTANCES_API}/${id}/attempts`);

export const cancelTaskInstance = (id: string): Promise<TaskInstanceStatus> =>
  HttpUtils.postData<TaskInstanceStatus>(`${TASK_INSTANCES_API}/${id}/cancel`);
