import type { TaskInstanceStatus } from "../task-execution/types";

export type DataSyncWriteMode = "UPSERT";

export type DataSyncType = "OFFLINE" | "REALTIME";

export type DataSyncScope = "SINGLE_TABLE" | "DATABASE";
export type DataSyncStep = "SCHEMA" | "FULL" | "INCREMENTAL";

export interface DataSyncTablePath { database?: string; schema?: string; table: string; }

export interface DataSyncTableMapping { source: DataSyncTablePath; target: DataSyncTablePath; writeMode: "UPSERT"; }

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

export type DataSyncTaskStatus = "OFFLINE" | "ONLINE";

/** A display-only projection. The persisted configuration is parameters.definition. */
export interface DataSyncTaskRecord {
  id: string;
  workflowCode: string;
  name: string;
  remark?: string;
  status: DataSyncTaskStatus | string;
  archived?: boolean;
  configurationError?: string;
  definitionVersion: number;
  revision: number;
  publishedVersion: number | null;
  updateBy?: string;
  createTime?: string;
  updateTime?: string;
  sourceConnector: string;
  sinkConnector: string;
  sourceProducesChangelog: boolean;
  sourceDataSourceId: string;
  targetDataSourceId: string;
  sourceDatabase?: string;
  sourceTable: string;
  targetDatabase?: string;
  targetTable: string;
  scope: DataSyncScope;
  steps: DataSyncStep[];
  syncType: DataSyncType | string;
  writeMode: DataSyncWriteMode;
  tableMappings: DataSyncTableMapping[];
}

export interface DataSyncTaskPageParams {
  pageNo: number;
  pageSize: number;
  keyword?: string;
  syncType?: DataSyncType;
  scope?: DataSyncScope;
  status?: DataSyncTaskStatus;
  sourceDataSourceId?: string;
  targetDataSourceId?: string;
}

/** An unreadable saved version is still a task row, but cannot perform runtime actions. */
export interface DataSyncUnreadableTaskRecord {
  id: string;
  name: string;
  status: DataSyncTaskStatus | string;
  archived?: boolean;
  definitionVersion: number;
  revision: number;
  publishedVersion: number | null;
  updateBy?: string;
  updateTime?: string;
  configurationError: string;
}

export type DataSyncTaskListRecord = DataSyncTaskRecord | DataSyncUnreadableTaskRecord;

/** Flink CDC Pipeline YAML represented as a JSON mapping for the task API. */
export type DataSyncSchemaChangeBehavior =
  | "ignore"
  | "lenient"
  | "try_evolve"
  | "evolve"
  | "exception";

export type DataSyncSchemaChangeEvent =
  | "create.table"
  | "add.column"
  | "alter.column.type"
  | "rename.column"
  | "drop.column"
  | "truncate.table"
  | "drop.table"
  | "alter.table.comment";

export interface DataSyncPipelineDefinition {
  source: Record<string, unknown> & {
    type: string;
    "datasource-id": string;
    tables: string;
  };
  sink: Record<string, unknown> & {
    type: string;
    "datasource-id": string;
    "exclude.schema.changes"?: DataSyncSchemaChangeEvent[];
  };
  route: Array<Record<string, unknown> & {
    "source-table": string;
    "sink-table": string;
  }>;
  pipeline: Record<string, unknown> & {
    "execution.runtime-mode": "BATCH" | "STREAMING";
    "schema.change.behavior"?: DataSyncSchemaChangeBehavior;
  };
  transform?: Array<Record<string, unknown>>;
}

export interface DataSyncParameters {
  definition: DataSyncPipelineDefinition;
}

export interface TaskDefinitionRecord {
  id: string;
  workflowCode: string;
  name: string;
  taskType: string;
  status: DataSyncTaskStatus | string;
  archived?: boolean;
  definitionVersion: number;
  revision: number;
  publishedVersion: number | null;
  parameters: DataSyncParameters;
  remark?: string;
  createTime?: string;
  updateTime?: string;
  updateBy?: string;
}

export interface PublishTaskRequest {
  version: number;
  expectedRevision: number;
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

export interface DataSyncDefinitionSnapshot {
  tableMappings?: DataSyncTableMapping[];
  taskId: string;
  scope?: DataSyncScope;
  steps?: DataSyncStep[];
  taskName: string;
  taskVersion: number;
  syncType?: DataSyncType | string;
  writeMode?: DataSyncWriteMode | string;
  source: DataSyncEndpointSnapshot;
  target: DataSyncEndpointSnapshot;
}

export interface DataSyncInstanceRecord {
  executionReport?: DataSyncExecutionReport;
  workflowInstanceId?: number;
  id: string;
  scope: DataSyncScope;
  steps: DataSyncStep[];
  taskId: string;
  taskName: string;
  taskVersion: number;
  syncType: DataSyncType | string;
  triggerType?: string;
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

export interface DataSyncInstanceSummary {
  id: string;
  workflowInstanceId?: number;
  taskId: string;
  taskName: string;
  taskVersion: number;
  syncType: DataSyncType;
  triggerType?: string;
  status: DataSyncInstanceStatus;
  startTime?: string;
  finishTime?: string;
  createTime?: string;
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


