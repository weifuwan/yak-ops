import type { TaskAttemptStatus, TaskInstanceStatus } from "../task-execution/types";

export type DataSyncWriteMode = "APPEND" | "OVERWRITE" | "UPSERT";

export interface PaginationInfo {
  pageNo: number;
  pageSize: number;
  total: number;
  pages?: number;
}

export type DataSyncType = "OFFLINE" | "REALTIME";

export type DataSyncTaskStatus = "UNPUBLISHED" | "PUBLISHED";

export type DataSyncRuntimePolicy = "AUTO" | "FIXED";

export interface DataSyncRuntimeConfig {
  policy?: DataSyncRuntimePolicy;
  fetchSize: number;
  readBatchSize: number;
  writeBatchSize: number;
  splitSize?: number;
  sourceParallelism: number;
  timeoutSeconds: number;
}

export interface DataSyncRealtimeConfig {
  checkpointIntervalSeconds: number;
  queueCapacity: number;
  pollBatchSize: number;
  writeBatchSize: number;
  timeoutSeconds: number;
}

export type DataSyncRetryPolicyMode = "SMART" | "FIXED";

export interface DataSyncRetryPolicy {
  mode?: DataSyncRetryPolicyMode;
  maxAttempts: number;
  backoffSeconds: number;
}

export interface DataSyncColumnMapping {
  source: string;
  target: string;
}

export interface DataSyncMappingConfig {
  columns: DataSyncColumnMapping[];
}

export interface DataSyncTaskRecord {
  id: string;
  name: string;
  syncType: DataSyncType | string;
  status: DataSyncTaskStatus | string;
  writeMode: DataSyncWriteMode | string;
  sourceDataSourceId: string;
  sourceDatabase?: string;
  sourceSchema?: string;
  sourceTable: string;
  targetDataSourceId: string;
  targetDatabase?: string;
  targetSchema?: string;
  targetTable: string;
  runtimeConfig?: DataSyncRuntimeConfig;
  offlineRuntimePlan?: DataSyncOfflineRuntimePlan;
  realtimeConfig?: DataSyncRealtimeConfig;
  retryPolicy?: DataSyncRetryPolicy;
  definitionVersion: number;
  remark?: string;
  updateBy?: string;
  createTime?: string;
  updateTime?: string;
}

export interface DataSyncTaskPageParams {
  pageNo: number;
  pageSize: number;
  keyword?: string;
  syncType?: DataSyncType;
  status?: DataSyncTaskStatus;
  sourceDataSourceId?: string;
  targetDataSourceId?: string;
}

export interface DataSyncTaskPageResult {
  bizData: DataSyncTaskRecord[];
  pagination: PaginationInfo;
}

export interface TaskDefinitionRecord {
  id: string;
  name: string;
  taskType: string;
  status: DataSyncTaskStatus | string;
  definitionVersion: number;
  parameterSchemaVersion: number;
  parameters: string;
  remark?: string;
  createTime?: string;
  updateTime?: string;
  updateBy?: string;
}

export interface TaskDefinitionPageResult {
  bizData: TaskDefinitionRecord[];
  pagination: PaginationInfo;
}

interface DataSyncTaskSaveBase {
  name: string;
  sourceDataSourceId: string;
  sourceDatabase?: string;
  sourceSchema?: string;
  sourceTable: string;
  targetDataSourceId: string;
  targetDatabase?: string;
  targetSchema?: string;
  targetTable: string;
  remark?: string;
}

export interface OfflineDataSyncTaskSavePayload extends DataSyncTaskSaveBase {
  syncType: "OFFLINE";
  writeMode?: DataSyncWriteMode;
}

export interface RealtimeDataSyncTaskSavePayload extends DataSyncTaskSaveBase {
  syncType: "REALTIME";
}

export type DataSyncTaskSavePayload =
  | OfflineDataSyncTaskSavePayload
  | RealtimeDataSyncTaskSavePayload;

export type DataSyncTaskUpdatePayload =
  | DataSyncTaskSavePayload
  | Pick<DataSyncTaskSaveBase, "name" | "remark">;

export interface SyncPipelineParameters {
  source: {
    type: "JDBC" | "MYSQL_CDC";
    datasourceId: string;
    database?: string;
    schema?: string;
    table: string;
  };
  transform: [];
  sink: {
    type: "JDBC";
    datasourceId: string;
    database?: string;
    schema?: string;
    table: string;
    writeMode: DataSyncWriteMode;
  };
}

export type DataSyncInstanceStatus = TaskInstanceStatus;

export interface DataSyncEndpointSnapshot {
  dataSourceId: string;
  dataSourceName?: string;
  dataSourceType?: string;
  database?: string;
  schema?: string;
  table: string;
}

export interface DataSyncOfflineRuntimePlan {
  policy?: DataSyncRuntimePolicy;
  sourceRowCount?: number;
  estimatedRowBytes?: number;
  splitColumn?: string;
  splitCount?: number;
  statisticsAvailable?: boolean;
}

export interface DataSyncDefinitionSnapshot {
  taskId: string;
  taskName: string;
  taskVersion: number;
  syncType?: DataSyncType | string;
  writeMode?: DataSyncWriteMode | string;
  autoCreateTable?: boolean;
  mapping?: DataSyncMappingConfig;
  source: DataSyncEndpointSnapshot;
  target: DataSyncEndpointSnapshot;
  runtimeConfig?: DataSyncRuntimeConfig;
  offlineRuntimePlan?: DataSyncOfflineRuntimePlan;
  realtimeConfig?: DataSyncRealtimeConfig;
  retryPolicy?: DataSyncRetryPolicy;
}

export interface DataSyncInstanceRecord {
  id: string;
  taskId: string;
  taskName: string;
  taskVersion: number;
  syncType: DataSyncType | string;
  triggerType?: "MANUAL" | "SCHEDULE" | "WORKFLOW";
  status: DataSyncInstanceStatus;
  maxAttempts?: number;
  backoffSeconds?: number;
  currentAttempt?: number;
  nextRetryTime?: string;
  readRows?: number;
  writeRows?: number;
  startTime?: string;
  finishTime?: string;
  errorCode?: number;
  errorMessage?: string;
  definitionSnapshot?: DataSyncDefinitionSnapshot;
  createTime?: string;
  updateTime?: string;
}

export interface DataSyncInstancePageParams {
  pageNo: number;
  pageSize: number;
  taskId?: string;
  syncType?: DataSyncType;
  keyword?: string;
  status?: DataSyncInstanceStatus;
  activeOnly?: boolean;
  triggerType?: string;
  startTimeStart?: string;
  startTimeEnd?: string;
}

export interface DataSyncInstancePageResult {
  bizData: DataSyncInstanceRecord[];
  pagination: PaginationInfo;
}

export interface DataSyncAttemptRecord {
  id: string;
  executionId: string;
  attemptNo: number;
  status: TaskAttemptStatus;
  runtimeEngine?: string;
  runtimeId?: string;
  logReference?: string;
  failureCode?: string;
  readRows?: number;
  writeRows?: number;
  startTime?: string;
  finishTime?: string;
  errorCode?: number;
  errorMessage?: string;
  createTime?: string;
  updateTime?: string;
}
