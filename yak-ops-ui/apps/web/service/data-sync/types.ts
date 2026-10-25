import type { TaskInstanceStatus } from "../task-execution/types";

export type DataSyncWriteMode = "APPEND" | "UPSERT" | "RETRACT";

export interface PaginationInfo {
  pageNo: number;
  pageSize: number;
  total: number;
  pages?: number;
}

export type DataSyncType = "OFFLINE" | "REALTIME";

export type DataSyncSolutionType =
  | "SINGLE_TABLE_OFFLINE"
  | "SINGLE_TABLE_REALTIME"
  | "DATABASE_OFFLINE"
  | "DATABASE_REALTIME";
export type DataSyncStep = "SCHEMA" | "FULL" | "INCREMENTAL";

export interface DataSyncSolution {
  solutionType: DataSyncSolutionType;
  displayName: string;
  steps: DataSyncStep[];
  defaultSteps: DataSyncStep[];
  allowedCombinations: Array<{
    steps: DataSyncStep[];
    supportsScheduling: boolean;
    writeModes: DataSyncWriteMode[];
  }>;
}

export interface DataSyncDraft {
  sourceType: string;
  targetType: string;
  solutionType: DataSyncSolutionType;
  steps: DataSyncStep[];
  name: string;
  form?: DataSyncTaskSavePayload;
}

export interface DataSyncTablePath {
  database?: string;
  schema?: string;
  table: string;
}

export interface DataSyncIncrementalConfig {
  mode: "TIMESTAMP" | "INCREASING";
  column: string;
  initialValue?: string;
  delaySeconds?: number;
  lookbackSeconds?: number;
}

export interface DataSyncTableMapping {
  source: DataSyncTablePath & { keyless?: boolean; chunkKeyColumn?: string };
  target: DataSyncTablePath;
  writeMode: DataSyncWriteMode;
  incremental?: DataSyncIncrementalConfig;
}

export interface DataSyncTableResult {
  mappingIndex: number;
  step: "FULL" | "INCREMENTAL";
  status: "WAITING" | "RUNNING" | "FINISHED" | "FAILED" | "CANCELED";
  progressPercent: number | null;
  progressUnit: "SPLITS";
  readRows: number;
  committedRows: number;
  plannedSplits: number | null;
  completedSplits: number;
  failureCode: string | null;
  plannedTables?: number | null;
  completedTables?: number | null;
  action?: "PREPARED" | "VERIFIED" | null;
}

export interface DataSyncBatchReport {
  version: 1;
  observedAt: string;
  tables: DataSyncTableResult[];
}

export type DataSyncStageStatus =
  | "WAITING"
  | "RUNNING"
  | "FINISHED"
  | "FAILED"
  | "CANCELED"
  | "UNKNOWN";

export interface DataSyncStageResult {
  step: DataSyncStep;
  status: DataSyncStageStatus;
  progressPercent: number | null;
  progressUnit: "SPLITS" | "RECORDS" | "TABLES";
  readRows: number | null;
  committedRows: number | null;
  plannedSplits: number | null;
  completedSplits: number | null;
  failureCode: string | null;
  plannedTables?: number | null;
  completedTables?: number | null;
  action?: "PREPARED" | "VERIFIED" | null;
}

export interface DataSyncExecutionSummary {
  version: 2 | 3;
  observedAt: string;
  generation: number;
  restored: boolean;
  counterScope: "GENERATION" | "INSTANCE";
  phase: "SCHEMA" | "FULL" | "INCREMENTAL" | "INITIALIZING" | "SNAPSHOT" | "HANDOFF" | "STREAMING";
  checkpoints: { handoffId: number | null; completedId: number | null };
  stages: DataSyncStageResult[];
}

export interface DataSyncStageReport extends DataSyncExecutionSummary {
  tables: Array<{ mappingIndex: number; stages: DataSyncStageResult[] }>;
}

export type DataSyncExecutionReport = DataSyncBatchReport | DataSyncStageReport;

export interface DataSyncParameters {
  /** Optional for definitions created before runtime evolution was configurable. */
  schemaChangesEnabled?: boolean;
  allowDropColumns?: boolean;
  solutionType: DataSyncSolutionType;
  steps: DataSyncStep[];
  source: { datasourceId: string };
  sink: { datasourceId: string };
  tableSelection: { mode: "EXPLICIT" };
  tableMappings: DataSyncTableMapping[];
}

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
  keylessEnabled?: boolean;
  chunkKeyColumn?: string;
  schemaChangesEnabled?: boolean;
  allowDropColumns?: boolean;
  tableMappings: DataSyncTableMapping[];
  incremental?: DataSyncIncrementalConfig;
  solutionType: DataSyncSolutionType;
  steps: DataSyncStep[];
  name: string;
  syncType: DataSyncType | string;
  status: DataSyncTaskStatus | string;
  debug: boolean;
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
  solutionType?: DataSyncSolutionType;
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
  debug: boolean;
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
  debug: boolean;
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

export interface DataSyncTaskSavePayload extends DataSyncTaskSaveBase {
  keylessEnabled?: boolean;
  chunkKeyColumn?: string;
  schemaChangesEnabled?: boolean;
  allowDropColumns?: boolean;
  tableMappings?: DataSyncTableMapping[];
  solutionType: DataSyncSolutionType;
  steps: DataSyncStep[];
  writeMode: DataSyncWriteMode;
  incremental?: DataSyncIncrementalConfig;
}

export type DataSyncTaskUpdatePayload =
  | DataSyncTaskSavePayload
  | Pick<DataSyncTaskSaveBase, "name" | "remark" | "debug">;

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
  tableMappings?: DataSyncTableMapping[];
  taskId: string;
  solutionType?: DataSyncSolutionType;
  steps?: DataSyncStep[];
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
  executionReport?: DataSyncExecutionReport;
  id: string;
  solutionType: DataSyncSolutionType;
  steps: DataSyncStep[];
  taskId: string;
  taskName: string;
  taskVersion: number;
  syncType: DataSyncType | string;
  triggerType?: "MANUAL" | "SCHEDULE" | "WORKFLOW";
  scheduleId?: string | null;
  scheduleRevision?: number | null;
  scheduledTime?: string | null;
  status: DataSyncInstanceStatus;
  progressPercent?: number;
  runtimeEngine?: string;
  runtimeId?: string;
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

export interface DataSyncSchemaPreview {
  mappingIndex: number;
  target: { catalog: string; schema: string | null; table: string };
  action: "CREATE" | "ALTER" | "VERIFY" | "BLOCKED";
  statements: string[];
  differences: Array<{
    column: string | null;
    action:
      | "COMPATIBLE"
      | "CREATE_TABLE"
      | "ADD_COLUMN"
      | "ALTER_COLUMN_TYPE"
      | "METADATA_DIFFERENCE"
      | "INCOMPATIBLE";
    reason: string;
    sourceType: string | null;
    targetType: string | null;
  }>;
}
