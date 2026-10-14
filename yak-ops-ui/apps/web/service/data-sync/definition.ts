import type {
  DataSyncTaskPageParams,
  DataSyncTaskRecord,
  DataSyncTaskSavePayload,
  DataSyncTaskUpdatePayload,
  DataSyncParameters,
  TaskDefinitionRecord,
} from "./types";
import { readSyncParameters, solutionSyncType } from "./parameters.ts";

/** Writes the single public product configuration; connector identifiers are resolved by the backend. */
export function buildSyncParameters(task: DataSyncTaskSavePayload): DataSyncParameters {
  return {
    solutionType: task.solutionType,
    steps: [...task.steps],
    source: { datasourceId: task.sourceDataSourceId },
    sink: { datasourceId: task.targetDataSourceId },
    tableSelection: { mode: "EXPLICIT" },
    tableMappings: [
      {
        source: {
          database: task.sourceDatabase,
          schema: task.sourceSchema,
          table: task.sourceTable,
        },
        target: {
          database: task.targetDatabase,
          schema: task.targetSchema,
          table: task.targetTable,
        },
        writeMode: task.writeMode,
      },
    ],
  };
}

export function createDefinitionRequest(task: DataSyncTaskSavePayload) {
  return {
    name: task.name,
    taskType: "DATA_SYNC",
    debug: task.debug,
    remark: task.remark,
    parameters: JSON.stringify(buildSyncParameters(task)),
  };
}

/** Omitting parameters edits metadata only and preserves the existing immutable parameter version. */
export function updateDefinitionRequest(task: DataSyncTaskUpdatePayload) {
  const request: { name: string; remark?: string; parameters?: string; debug?: boolean } = {
    name: task.name,
    remark: task.remark,
    debug: task.debug,
  };
  if ("solutionType" in task) request.parameters = JSON.stringify(buildSyncParameters(task));
  return request;
}

export function definitionPageRequest(params: DataSyncTaskPageParams) {
  const parameterFilters: Record<string, string> = {};
  if (params.solutionType) parameterFilters.solutionType = params.solutionType;
  if (params.syncType === "OFFLINE") parameterFilters.sourceType = "JDBC";
  if (params.syncType === "REALTIME") parameterFilters.sourceType = "MYSQL_CDC";
  if (params.sourceDataSourceId) parameterFilters.sourceDataSourceId = params.sourceDataSourceId;
  if (params.targetDataSourceId) parameterFilters.targetDataSourceId = params.targetDataSourceId;
  return {
    pageNo: params.pageNo,
    pageSize: params.pageSize,
    keyword: params.keyword,
    status: params.status,
    taskType: "DATA_SYNC",
    parameterFilters,
  };
}

/** Projects one immutable version into the unified task UI without rewriting stored version-two JSON. */
export function toSyncTaskRecord(definition: TaskDefinitionRecord): DataSyncTaskRecord {
  if (definition.taskType !== "DATA_SYNC") throw new Error("不支持的同步任务定义");
  const parameters = readSyncParameters(definition.parameterSchemaVersion, definition.parameters);
  const mapping = parameters.tableMappings[0];
  return {
    id: definition.id,
    name: definition.name,
    solutionType: parameters.solutionType,
    steps: parameters.steps,
    syncType: solutionSyncType(parameters.solutionType),
    status: definition.status,
    debug: definition.debug ?? false,
    writeMode: mapping.writeMode,
    sourceDataSourceId: parameters.source.datasourceId,
    sourceDatabase: mapping.source.database ?? undefined,
    sourceSchema: mapping.source.schema ?? undefined,
    sourceTable: mapping.source.table,
    targetDataSourceId: parameters.sink.datasourceId,
    targetDatabase: mapping.target.database ?? undefined,
    targetSchema: mapping.target.schema ?? undefined,
    targetTable: mapping.target.table,
    definitionVersion: definition.definitionVersion,
    remark: definition.remark,
    createTime: definition.createTime,
    updateTime: definition.updateTime,
    updateBy: definition.updateBy,
  };
}
