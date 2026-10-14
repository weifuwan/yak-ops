import type {
  TaskInstanceDefinitionRecord,
  TaskInstanceRecord,
  TaskInstanceStatus,
} from "../task-execution/types";
import type { DataSyncInstancePageParams, DataSyncInstanceRecord } from "./types";

import { readSyncParameters, solutionSyncType } from "./parameters.ts";

export const isTaskInstanceActive = (status: TaskInstanceStatus): boolean =>
  status === "SUBMITTED" ||
  status === "LAUNCHING" ||
  status === "RUNNING" ||
  status === "CANCELLING" ||
  status === "RECONCILING";

export const canCancelTaskInstance = (status: TaskInstanceStatus): boolean =>
  status === "SUBMITTED" || status === "LAUNCHING" || status === "RUNNING";

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

  const parameters = readSyncParameters(version.parameterSchemaVersion, version.parameters);
  const source = { ...parameters.source, ...parameters.tableMappings[0].source };
  const sink = {
    ...parameters.sink,
    ...parameters.tableMappings[0].target,
    writeMode: parameters.tableMappings[0].writeMode,
  };
  const syncType = solutionSyncType(parameters.solutionType);
  const taskName = version.name;

  return {
    id: instance.id,
    solutionType: parameters.solutionType,
    steps: parameters.steps,
    taskId: instance.definitionId,
    taskName,
    taskVersion: instance.definitionVersion,
    syncType,
    triggerType: instance.triggerType,
    scheduleId: instance.scheduleId,
    scheduleRevision: instance.scheduleRevision,
    scheduledTime: instance.scheduledTime,
    status: instance.status,
    progressPercent: instance.progressPercent ?? undefined,
    runtimeEngine: instance.runtimeEngine ?? undefined,
    runtimeId: instance.runtimeId ?? undefined,
    startTime: instance.startTime ?? undefined,
    finishTime: instance.endTime ?? undefined,
    errorMessage: instance.failureCode ?? undefined,
    createTime: instance.createTime ?? undefined,
    definitionSnapshot: {
      solutionType: parameters.solutionType,
      steps: parameters.steps,
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
