import type {
  TaskAttemptRecord,
  TaskInstanceDefinitionRecord,
  TaskInstanceRecord,
  TaskInstanceStatus,
} from "../task-execution/types";
import type {
  DataSyncAttemptRecord,
  DataSyncInstancePageParams,
  DataSyncInstanceRecord,
  DataSyncType,
  SyncPipelineParameters,
} from "./types";

export const isTaskInstanceActive = (status: TaskInstanceStatus): boolean =>
  status === "SUBMITTED" ||
  status === "RUNNING" ||
  status === "CANCELLING" ||
  status === "RECONCILING";

export const canCancelTaskInstance = (status: TaskInstanceStatus): boolean =>
  status === "SUBMITTED" || status === "RUNNING";

export function toDataSyncInstanceRecord(
  instance: TaskInstanceRecord,
  version: TaskInstanceDefinitionRecord,
): DataSyncInstanceRecord {
  if (
    instance.taskType !== "DATA_SYNC" ||
    version.taskType !== instance.taskType ||
    version.definitionId !== instance.definitionId ||
    version.version !== instance.definitionVersion
  ) {
    throw new Error("Task Instance Definition version mismatch");
  }

  const parameters = JSON.parse(version.parameters) as SyncPipelineParameters;
  const { source, sink } = parameters;
  const syncType: DataSyncType = source.type === "MYSQL_CDC" ? "REALTIME" : "OFFLINE";
  const taskName = version.name;

  return {
    id: instance.id,
    taskId: instance.definitionId,
    taskName,
    taskVersion: instance.definitionVersion,
    syncType,
    triggerType: instance.triggerType,
    status: instance.status,
    startTime: instance.startTime ?? undefined,
    finishTime: instance.endTime ?? undefined,
    errorMessage: instance.failureCode ?? undefined,
    createTime: instance.createTime ?? undefined,
    definitionSnapshot: {
      taskId: instance.definitionId,
      taskName,
      taskVersion: instance.definitionVersion,
      syncType,
      writeMode: sink.writeMode,
      source: {
        dataSourceId: source.datasourceId,
        database: source.database,
        schema: source.schema,
        table: source.table,
      },
      target: {
        dataSourceId: sink.datasourceId,
        database: sink.database,
        schema: sink.schema,
        table: sink.table,
      },
    },
  };
}

export function toDataSyncAttemptRecord(
  attempt: TaskAttemptRecord,
  instanceId: string,
): DataSyncAttemptRecord {
  return {
    id: attempt.id,
    executionId: instanceId,
    attemptNo: attempt.attemptNumber,
    status: attempt.status,
    runtimeEngine: attempt.runtimeEngine ?? undefined,
    runtimeId: attempt.runtimeId ?? undefined,
    logReference: attempt.logReference ?? undefined,
    failureCode: attempt.failureCode ?? undefined,
    errorMessage: attempt.failureCode ?? undefined,
    startTime: attempt.startTime ?? undefined,
    finishTime: attempt.endTime ?? undefined,
  };
}

export function matchesDataSyncInstance(
  instance: DataSyncInstanceRecord,
  params: DataSyncInstancePageParams,
): boolean {
  if (params.taskId && instance.taskId !== params.taskId) return false;
  if (params.syncType && instance.syncType !== params.syncType) return false;
  if (params.status && instance.status !== params.status) return false;
  if (params.activeOnly && !isTaskInstanceActive(instance.status)) return false;
  if (params.triggerType && instance.triggerType !== params.triggerType) return false;
  if (params.keyword) {
    const keyword = params.keyword.trim().toLowerCase();
    if (
      !instance.taskName.toLowerCase().includes(keyword) &&
      !instance.taskId.toLowerCase().includes(keyword)
    ) {
      return false;
    }
  }
  const startTime = instance.startTime ?? instance.createTime;
  if (params.startTimeStart && (!startTime || startTime < params.startTimeStart)) return false;
  if (params.startTimeEnd && (!startTime || startTime > params.startTimeEnd)) return false;
  return true;
}
