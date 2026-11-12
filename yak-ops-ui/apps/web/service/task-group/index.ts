import { YAK_OPS_API_PREFIX } from "@/config/api";
import HttpUtils from "@/service/http/HttpUtils";
import type { PageInfo } from "@/service/http/page-info";

const TASK_GROUP_API = YAK_OPS_API_PREFIX + "/task-group";

export type TaskGroupStatus = "YES" | "NO" | "1" | "0" | number;

export interface TaskGroupRecord {
  id: number;
  name: string;
  projectCode: number | string;
  description?: string | null;
  groupSize: number;
  useSize: number;
  status: TaskGroupStatus;
  createTime?: string;
  updateTime?: string;
}

export interface TaskGroupQueueRecord {
  id: number;
  taskId?: number;
  taskName?: string;
  projectName?: string;
  projectCode?: number | string;
  workflowInstanceName?: string;
  groupId: number;
  workflowInstanceId?: number;
  priority: number;
  forceStart: number | string;
  inQueue: number | string;
  status: "WAIT_QUEUE" | "ACQUIRE_SUCCESS" | "RELEASE" | number | string;
  createTime?: string;
  updateTime?: string;
}

export interface TaskGroupPageParams {
  pageNo: number;
  pageSize: number;
  name?: string;
  status?: number;
}

export interface TaskGroupQueuePageParams {
  pageNo: number;
  pageSize: number;
  groupId?: number;
  taskInstanceName?: string;
  workflowInstanceName?: string;
}

export interface TaskGroupSavePayload {
  name: string;
  description: string;
  groupSize: number;
  projectCode?: number | string;
}

const toQuery = (values: Record<string, string | number | undefined>) => {
  const params = new URLSearchParams();
  Object.entries(values).forEach(([key, value]) => {
    if (value !== undefined) params.set(key, String(value));
  });
  return params.toString();
};

const postParams = (values: Record<string, string | number>) =>
  new URLSearchParams(
    Object.entries(values).map(([key, value]) => [key, String(value)]),
  );

export const listTaskGroups = (
  params: TaskGroupPageParams,
  signal?: AbortSignal,
): Promise<PageInfo<TaskGroupRecord>> =>
  HttpUtils.getData<PageInfo<TaskGroupRecord>>(
    TASK_GROUP_API + "/list-paging?" + toQuery(params),
    { signal },
  );

export const listTaskGroupsByProjectCode = (
  projectCode: number | string,
  params: Pick<TaskGroupPageParams, "pageNo" | "pageSize"> = {
    pageNo: 1,
    pageSize: 1000,
  },
  signal?: AbortSignal,
): Promise<PageInfo<TaskGroupRecord>> =>
  HttpUtils.getData<PageInfo<TaskGroupRecord>>(
    TASK_GROUP_API +
      "/query-list-by-projectCode?" +
      toQuery({
        projectCode,
        pageNo: params.pageNo,
        pageSize: params.pageSize,
      }),
    { signal },
  );

export const listTaskGroupQueue = (
  params: TaskGroupQueuePageParams,
  signal?: AbortSignal,
): Promise<PageInfo<TaskGroupQueueRecord>> =>
  HttpUtils.getData<PageInfo<TaskGroupQueueRecord>>(
    TASK_GROUP_API +
      "/query-list-by-group-id?" +
      toQuery({
        groupId: params.groupId ?? -1,
        taskInstanceName: params.taskInstanceName,
        workflowInstanceName: params.workflowInstanceName,
        pageNo: params.pageNo,
        pageSize: params.pageSize,
      }),
    { signal },
  );

export const createTaskGroup = (payload: TaskGroupSavePayload): Promise<TaskGroupRecord> =>
  HttpUtils.postData<TaskGroupRecord>(TASK_GROUP_API + "/create", undefined, {
    body: postParams({
      name: payload.name,
      projectCode: payload.projectCode ?? 0,
      description: payload.description,
      groupSize: payload.groupSize,
    }),
  });

export const updateTaskGroup = (
  id: number,
  payload: Omit<TaskGroupSavePayload, "projectCode">,
): Promise<TaskGroupRecord> =>
  HttpUtils.postData<TaskGroupRecord>(TASK_GROUP_API + "/update", undefined, {
    body: postParams({
      id,
      name: payload.name,
      description: payload.description,
      groupSize: payload.groupSize,
    }),
  });

export const startTaskGroup = (id: number): Promise<void> =>
  HttpUtils.postData<void>(TASK_GROUP_API + "/start-task-group", undefined, {
    body: postParams({ id }),
  });

export const closeTaskGroup = (id: number): Promise<void> =>
  HttpUtils.postData<void>(TASK_GROUP_API + "/close-task-group", undefined, {
    body: postParams({ id }),
  });

export const forceStartTaskGroupQueue = (queueId: number): Promise<void> =>
  HttpUtils.postData<void>(TASK_GROUP_API + "/forceStart", undefined, {
    body: postParams({ queueId }),
  });

export const modifyTaskGroupQueuePriority = (
  queueId: number,
  priority: number,
): Promise<void> =>
  HttpUtils.postData<void>(TASK_GROUP_API + "/modifyPriority", undefined, {
    body: postParams({ queueId, priority }),
  });

export function isTaskGroupEnabled(status: TaskGroupStatus): boolean {
  return status === "YES" || status === 1 || status === "1";
}
