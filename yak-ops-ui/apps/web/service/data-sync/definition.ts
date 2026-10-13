import type {
  DataSyncTaskPageParams,
  DataSyncTaskRecord,
  DataSyncTaskSavePayload,
  DataSyncTaskUpdatePayload,
  SyncPipelineParameters,
  TaskDefinitionRecord,
} from "./types";

/** Converts the editor's single-table selection into DATA_SYNC schema v2. */
export function buildSyncParameters(task: DataSyncTaskSavePayload): SyncPipelineParameters {
  const source: SyncPipelineParameters["source"] = {
    type: task.syncType === "REALTIME" ? "MYSQL_CDC" : "JDBC",
    datasourceId: task.sourceDataSourceId,
    table: task.sourceTable,
  };
  if (task.sourceDatabase) source.database = task.sourceDatabase;
  if (task.sourceSchema) source.schema = task.sourceSchema;

  const sink: SyncPipelineParameters["sink"] = {
    type: "JDBC",
    datasourceId: task.targetDataSourceId,
    table: task.targetTable,
    writeMode: task.syncType === "REALTIME" ? "UPSERT" : task.writeMode || "APPEND",
  };
  if (task.targetDatabase) sink.database = task.targetDatabase;
  if (task.targetSchema) sink.schema = task.targetSchema;

  return { source, transform: [], sink };
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

/** Omitting parameters edits only definition metadata and does not create a new version. */
export function updateDefinitionRequest(task: DataSyncTaskUpdatePayload) {
  const request: { name: string; remark?: string; parameters?: string; debug?: boolean } = {
    name: task.name,
    remark: task.remark,
    debug: task.debug,
  };
  if ("syncType" in task) {
    request.parameters = JSON.stringify(buildSyncParameters(task));
  }
  return request;
}

export function definitionPageRequest(params: DataSyncTaskPageParams) {
  const parameterFilters: Record<string, string> = {};
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

/** Projects a generic Definition response onto fields already used by sync task pages. */
export function toSyncTaskRecord(definition: TaskDefinitionRecord): DataSyncTaskRecord {
  if (definition.taskType !== "DATA_SYNC" || definition.parameterSchemaVersion !== 2) {
    throw new Error("不支持的同步任务定义");
  }
  const parameters = JSON.parse(definition.parameters) as SyncPipelineParameters;
  const { source, sink } = parameters;
  const syncType = source.type === "MYSQL_CDC" ? "REALTIME" : "OFFLINE";

  return {
    id: definition.id,
    name: definition.name,
    syncType,
    status: definition.status,
    debug: definition.debug ?? false,
    writeMode: sink.writeMode,
    sourceDataSourceId: source.datasourceId,
    sourceDatabase: source.database ?? undefined,
    sourceSchema: source.schema ?? undefined,
    sourceTable: source.table,
    targetDataSourceId: sink.datasourceId,
    targetDatabase: sink.database ?? undefined,
    targetSchema: sink.schema ?? undefined,
    targetTable: sink.table,
    definitionVersion: definition.definitionVersion,
    remark: definition.remark,
    createTime: definition.createTime,
    updateTime: definition.updateTime,
    updateBy: definition.updateBy,
  };
}
