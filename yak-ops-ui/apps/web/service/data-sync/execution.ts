import type {
  TaskInstanceDefinitionRecord, TaskInstanceRecord, TaskInstanceStatus,
} from "../task-execution/types";
import type { DataSyncInstancePageParams, DataSyncInstanceRecord } from "./types";
import {
  pipelineDatabaseRoute,
  pipelineScope,
  pipelineSteps,
  pipelineSyncType,
  pipelineTableMappings,
  readPipelineDefinition,
} from "./native-pipeline";
import { readExecutionReport } from "./report";

export const isTaskInstanceActive = (status: TaskInstanceStatus): boolean =>
  status === "SUBMITTED_SUCCESS" ||
  status === "DISPATCH" ||
  status === "RUNNING_EXECUTION" ||
  status === "DELAY_EXECUTION" ||
  status === "NEED_FAULT_TOLERANCE";

export const canManageTaskDefinition = (
  ready: boolean,
  instance?: Pick<TaskInstanceRecord, "status">,
): boolean => ready && (!instance || !isTaskInstanceActive(instance.status));

export const canCancelTaskInstance = (status: TaskInstanceStatus): boolean =>
  status === "SUBMITTED_SUCCESS" ||
  status === "DISPATCH" ||
  status === "RUNNING_EXECUTION" ||
  status === "DELAY_EXECUTION" ||
  status === "NEED_FAULT_TOLERANCE";

export function toDataSyncInstanceRecord(
  instance: TaskInstanceRecord,
  version: TaskInstanceDefinitionRecord,
): DataSyncInstanceRecord {
  if (instance.taskType !== "DATA_SYNC" ||
      version.taskType !== instance.taskType ||
      version.definitionId !== instance.definitionId ||
      version.version !== instance.definitionVersion) {
    throw new Error("Task Instance Definition version mismatch");
  }
  const definition = readPipelineDefinition(version.parameters);
  const mappings = pipelineTableMappings(definition);
  const databaseRoute = pipelineDatabaseRoute(definition);
  const firstMapping = mappings[0];
  if (!databaseRoute && !firstMapping) throw new Error("缺少有效的同步路由");
  const source = databaseRoute
    ? { database: databaseRoute.sourceDatabase, table: "*" }
    : firstMapping!.source;
  const target = databaseRoute
    ? { database: databaseRoute.targetDatabase, table: "*" }
    : firstMapping!.target;
  const displayMappings = databaseRoute ? [{ source, target }] : mappings;
  const scope = pipelineScope(definition);
  const steps = pipelineSteps(definition);
  const syncType = pipelineSyncType(definition);
  const tableMappings = displayMappings.map((mapping) => ({
    ...mapping,
    writeMode: "UPSERT" as const,
  }));
  return {
    id: instance.id,
    executionReport: readExecutionReport(instance.executionReport, tableMappings.length, steps),
    scope,
    steps,
    taskId: instance.definitionId,
    taskName: version.name,
    taskVersion: instance.definitionVersion,
    workflowInstanceId: instance.workflowInstanceId,
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
      tableMappings, scope, steps,
      taskId: instance.definitionId, taskName: version.name,
      taskVersion: instance.definitionVersion, syncType,
      writeMode: "UPSERT",
      source: {
        dataSourceId: definition.source["datasource-id"],
        database: source.database, table: source.table,
      },
      target: {
        dataSourceId: definition.sink["datasource-id"],
        database: target.database, table: target.table,
      },
    },
  };
}

export function matchesDataSyncInstance(
  instance: DataSyncInstanceRecord, params: DataSyncInstancePageParams,
): boolean {
  if (params.taskId && instance.taskId !== params.taskId) return false;
  if (params.syncType && instance.syncType !== params.syncType) return false;
  if (params.status && instance.status !== params.status) return false;
  if (params.activeOnly && !isTaskInstanceActive(instance.status)) return false;
  if (params.triggerType && instance.triggerType !== params.triggerType) return false;
  if (params.keyword) {
    const keyword = params.keyword.trim().toLowerCase();
    if (!instance.taskName.toLowerCase().includes(keyword) &&
        !instance.taskId.toLowerCase().includes(keyword)) return false;
  }
  const start = instance.startTime ?? instance.createTime;
  if (params.startTimeStart && (!start || start < params.startTimeStart)) return false;
  if (params.startTimeEnd && (!start || start > params.startTimeEnd)) return false;
  return true;
}
